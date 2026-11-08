import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
const keytool = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'keytool.exe' : 'keytool');
const certificate = execFileSync(keytool, ['-exportcert', '-keystore', process.env.ANDROID_KEYSTORE_PATH,
  '-alias', process.env.ANDROID_KEY_ALIAS, '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD']);
console.log(createHash('sha256').update(certificate).digest('hex'));
