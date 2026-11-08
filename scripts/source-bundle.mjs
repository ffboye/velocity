import { readdirSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs';
const roots = ['app/src', 'gradle', '.github/workflows', 'config', 'docs', 'scripts', 'release-notes'];
const fixed = ['.gitattributes', '.gitignore', 'README.md', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'version.properties', 'update.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle.kts'];
const paths = [...fixed];
function walk(path) {
  for (const entry of readdirSync(path, { withFileTypes: true })) {
    const file = `${path}/${entry.name}`;
    if (entry.isDirectory()) walk(file); else paths.push(file);
  }
}
roots.forEach(walk);
const allowed = /\.(?:kt|kts|xml|webp|toml|properties|jar|yml|txt|md|mjs|ps1|keep)$/;
const files = paths.map(path => {
  if (!fixed.includes(path) && !allowed.test(path)) throw Error(`Unexpected source file ${path}`);
  if (/(?:\.p12|\.jks|\.pem|password\.dpapi|signing\.local)/i.test(path)) throw Error(`Private material path ${path}`);
  const binary = /\.(?:webp|jar)$/.test(path);
  const bytes = readFileSync(path);
  const content = binary ? bytes.toString('base64') : bytes.toString('utf8').replace(/\r\n/g, '\n');
  if (!binary && /-----BEGIN (?:RSA |EC |ENCRYPTED )?PRIVATE KEY-----/.test(content)) throw Error(`Private key in ${path}`);
  return { path, mode: path === 'gradlew' ? '100755' : '100644', binary, content };
});
mkdirSync('build', { recursive: true });
const bundle = JSON.stringify(files);
writeFileSync('build/source-bundle.json', bundle);
console.log(JSON.stringify({ files: files.length, bytes: Buffer.byteLength(bundle) }));
