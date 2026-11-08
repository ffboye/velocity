import { createHash, createPrivateKey, createPublicKey, sign, verify } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function generate({ apkPath, badging, repository, tag, notes, privateKeyPem, publicKeyBase64 }) {
  if (!/^[A-Za-z0-9-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/.test(repository)) throw Error('Invalid repository');
  if (!/^v\d+\.\d+\.\d+$/.test(tag)) throw Error('A stable version tag is required');
  const pkg = /package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/.exec(badging);
  const sdk = /^(?:sdkVersion|minSdkVersion):'(\d+)'/m.exec(badging);
  if (!pkg || !sdk) throw Error('Cannot read the actual APK metadata');
  const [, packageName, code, version] = pkg;
  if (tag !== `v${version}`) throw Error('Tag and actual APK version differ');
  const versionCode = Number(code);
  if (!Number.isSafeInteger(versionCode) || versionCode < 1 || versionCode > 2100000000) throw Error('Invalid versionCode');
  if (Number(sdk[1]) < 24 || Number(sdk[1]) > 1000) throw Error('Invalid minimum SDK');
  const releaseNotes = notes.split(/\r?\n/).map(line => line.trim()).filter(line => line && !line.startsWith('#')).map(line => line.replace(/^[-*]\s+/, ''));
  if (!releaseNotes.length || releaseNotes.length > 100 || releaseNotes.some(line => line.length > 2000)) throw Error('Supply concise release notes');
  const privateKey = createPrivateKey(privateKeyPem);
  const publicKey = createPublicKey({ key: Buffer.from(publicKeyBase64.trim(), 'base64'), format: 'der', type: 'spki' });
  if (privateKey.asymmetricKeyType !== 'rsa' || privateKey.asymmetricKeyDetails.modulusLength < 3072) throw Error('RSA key must be at least 3072 bits');
  if (!createPublicKey(privateKey).export({ type: 'spki', format: 'der' }).equals(publicKey.export({ type: 'spki', format: 'der' }))) throw Error('Manifest key does not match the public key embedded in the app');
  const apk = readFileSync(apkPath);
  if (!apk.length || apk.length > 1024 ** 3) throw Error('Invalid APK size');
  const manifest = { schemaVersion: 1, version, versionCode, minimumAndroidSdk: Number(sdk[1]),
    apkUrl: `https://github.com/${repository}/releases/download/${tag}/velocity.apk`,
    sha256: createHash('sha256').update(apk).digest('hex'), sizeBytes: apk.length, releaseNotes, packageName };
  const metadata = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`);
  const signature = sign('RSA-SHA256', metadata, privateKey);
  if (!verify('RSA-SHA256', metadata, publicKey, signature)) throw Error('Manifest signature self-check failed');
  return { manifest, metadata, signature };
}
export function validatePrevious(metadata, signature, publicKeyBase64, next) {
  const key = createPublicKey({ key: Buffer.from(publicKeyBase64.trim(), 'base64'), type: 'spki', format: 'der' });
  if (!verify('RSA-SHA256', metadata, key, signature)) throw Error('Previous manifest signature is untrusted');
  const previous = JSON.parse(metadata);
  if (!Number.isSafeInteger(previous.versionCode) || next.versionCode <= previous.versionCode) throw Error('versionCode must increase');
  if (previous.packageName !== next.packageName) throw Error('Application ID changed');
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const { APK_PATH, AAPT2_PATH, RELEASE_TAG, RELEASE_NOTES_PATH, MANIFEST_KEY_PATH, GITHUB_REPOSITORY, PREVIOUS_MANIFEST_PATH } = process.env;
  for (const [name, value] of Object.entries({ APK_PATH, AAPT2_PATH, RELEASE_TAG, RELEASE_NOTES_PATH, MANIFEST_KEY_PATH, GITHUB_REPOSITORY })) if (!value) throw Error(`Missing ${name}`);
  const config = Object.fromEntries(readFileSync('update.properties', 'utf8').split(/\r?\n/).filter(line => line.includes('=')).map(line => line.split('=')));
  const repository = `${config.UPDATE_OWNER}/${config.UPDATE_REPOSITORY}`;
  if (GITHUB_REPOSITORY !== repository) throw Error('The workflow repository differs from the configured update source');
  const publicKeyBase64 = readFileSync('config/update-public-key.txt', 'utf8');
  const result = generate({ apkPath: APK_PATH, badging: execFileSync(AAPT2_PATH, ['dump', 'badging', APK_PATH], { encoding: 'utf8' }),
    repository, tag: RELEASE_TAG, notes: readFileSync(RELEASE_NOTES_PATH, 'utf8'), privateKeyPem: readFileSync(MANIFEST_KEY_PATH), publicKeyBase64 });
  if (result.manifest.packageName !== 'com.example.myapplication') throw Error('Application ID changed');
  if (PREVIOUS_MANIFEST_PATH) validatePrevious(readFileSync(PREVIOUS_MANIFEST_PATH), readFileSync(`${PREVIOUS_MANIFEST_PATH}.sig`), publicKeyBase64, result.manifest);
  mkdirSync('.release', { recursive: true });
  writeFileSync('.release/velocity.apk', readFileSync(APK_PATH));
  writeFileSync('.release/update.json', result.metadata);
  writeFileSync('.release/update.json.sig', result.signature);
  writeFileSync('.release/release-notes.md', readFileSync(RELEASE_NOTES_PATH));
  console.log(`Generated authenticated metadata from APK: ${result.manifest.version} (${result.manifest.versionCode})`);
}
