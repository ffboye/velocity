import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdirSync, existsSync, writeFileSync, readFileSync } from 'node:fs';
import { resolve, join } from 'node:path';

const directory = process.argv[2];
const publicFile = resolve('config/update-public-key.txt');
if (!directory) throw Error('Pass a secure directory outside the project.');
const location = resolve(directory);
if (location.toLowerCase().startsWith(`${process.cwd().toLowerCase()}\\`) || location === process.cwd()) throw Error('Keys must be stored outside the project');
mkdirSync(location, { recursive: true, mode: 0o700 });
if (existsSync(join(location, 'manifest-key.pem')) || existsSync(publicFile)) throw Error('Existing keys will not be overwritten');
const pair = generateKeyPairSync('rsa', { modulusLength: 4096 });
writeFileSync(join(location, 'manifest-key.pem'), pair.privateKey.export({ type: 'pkcs8', format: 'pem' }), { flag: 'wx', mode: 0o600 });
mkdirSync(resolve('config'), { recursive: true });
writeFileSync(publicFile, pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64') + '\n', { flag: 'wx' });
const password = randomBytes(32).toString('hex');
const keytool = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'keytool.exe' : 'keytool') : 'keytool';
// Windows DPAPI encrypts credentials to the current user; no password is printed.
if (process.platform !== 'win32') throw Error('This helper currently requires Windows DPAPI');
execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', "[void][System.Reflection.Assembly]::Load('System.Security, Version=4.0.0.0, Culture=neutral, PublicKeyToken=b03f5f7f11d50a3a'); $encrypted=[System.Security.Cryptography.ProtectedData]::Protect([System.Text.Encoding]::UTF8.GetBytes($env:VELOCITY_KEY_PASSWORD),$null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser); [System.IO.File]::WriteAllText([System.IO.Path]::Combine($env:VELOCITY_KEY_DIRECTORY,'password.dpapi'),[Convert]::ToBase64String($encrypted))"], { env: { ...process.env, VELOCITY_KEY_PASSWORD: password, VELOCITY_KEY_DIRECTORY: location }, stdio: 'pipe' });
execFileSync(keytool, ['-genkeypair', '-keystore', join(location, 'velocity-release.p12'), '-storetype', 'PKCS12', '-alias', 'velocity', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000', '-dname', 'CN=Velocity, O=Velocity', '-storepass:env', 'VELOCITY_KEY_PASSWORD', '-keypass:env', 'VELOCITY_KEY_PASSWORD'], { env: { ...process.env, VELOCITY_KEY_PASSWORD: password }, stdio: ['ignore', 'ignore', 'pipe'] });
console.log('Created permanent release and manifest keys outside the project. Only the public manifest key is in config/.');
