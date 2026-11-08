import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify, createHash } from 'node:crypto';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { generate, validatePrevious } from './generate-update.mjs';

test('release generator derives real byte metadata and authenticates version progression', () => {
  const pair = generateKeyPairSync('rsa', { modulusLength: 3072 });
  const publicKeyBase64 = pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
  const directory = mkdtempSync(join(tmpdir(), 'velocity-manifest-'));
  try {
    const apkPath = join(directory, 'fixture.apk');
    const bytes = Buffer.from('APK bytes fixture for release generator unit test');
    writeFileSync(apkPath, bytes);
    const options = { apkPath, repository: 'ffboye/velocity', tag: 'v1.1.1', notes: '- Signed update support',
      badging: "package: name='com.example.myapplication' versionCode='3' versionName='1.1.1'\nminSdkVersion:'24'",
      privateKeyPem: pair.privateKey.export({ type: 'pkcs8', format: 'pem' }), publicKeyBase64 };
    const result = generate(options);
    assert.equal(result.manifest.sizeBytes, bytes.length);
    assert.equal(result.manifest.sha256, createHash('sha256').update(bytes).digest('hex'));
    assert.equal(result.manifest.versionCode, 3);
    assert.ok(verify('RSA-SHA256', result.metadata, pair.publicKey, result.signature));
    assert.equal(generate({ ...options, badging: options.badging.replace('minSdkVersion', 'sdkVersion') }).manifest.minimumAndroidSdk, 24);
    assert.throws(() => generate({ ...options, tag: 'v1.2.0' }), /version differ/);
    assert.throws(() => generate({ ...options, notes: '' }), /release notes/);
    assert.throws(() => generate({ ...options, repository: 'bad/../repo' }), /repository/);
    assert.throws(() => validatePrevious(result.metadata, result.signature, publicKeyBase64, result.manifest), /increase/);
    validatePrevious(result.metadata, result.signature, publicKeyBase64, { ...result.manifest, versionCode: 4 });
    const tampered = Buffer.from(result.signature); tampered[0] ^= 1;
    assert.throws(() => validatePrevious(result.metadata, tampered, publicKeyBase64, { ...result.manifest, versionCode: 4 }), /untrusted/);
    const other = generateKeyPairSync('rsa', { modulusLength: 3072 });
    assert.throws(() => generate({ ...options, publicKeyBase64: other.publicKey.export({ type: 'spki', format: 'der' }).toString('base64') }), /does not match/);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});
