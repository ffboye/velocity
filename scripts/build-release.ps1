$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'signing.ps1')
$values = Get-VelocitySigning
$previous = @{}
try {
    foreach ($name in $values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
    if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
    & (Join-Path $PSScriptRoot '..\gradlew.bat') :app:assembleRelease --console=plain --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    Write-Output 'Signed APK: app/build/outputs/apk/release/app-release.apk'
} finally {
    foreach ($name in $values.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
