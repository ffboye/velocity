param([string]$CopySecret)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'signing.ps1')
$values = Get-VelocitySigning
$secrets = @{
    ANDROID_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($values.ANDROID_KEYSTORE_PATH))
    ANDROID_KEYSTORE_PASSWORD = $values.ANDROID_KEYSTORE_PASSWORD
    ANDROID_KEY_ALIAS = $values.ANDROID_KEY_ALIAS
    ANDROID_KEY_PASSWORD = $values.ANDROID_KEY_PASSWORD
    MANIFEST_PRIVATE_KEY_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($values.MANIFEST_KEY_PATH))
}
if ($CopySecret) {
    if (!$secrets.ContainsKey($CopySecret)) { throw 'Unknown secret name' }
    Set-Clipboard -Value $secrets[$CopySecret]
    Write-Output "Copied $CopySecret to the Windows clipboard. Save it in your password manager or GitHub Actions secret; clear your clipboard afterwards."
    return
}
$gh = Get-Command gh -ErrorAction SilentlyContinue
if (!$gh) {
    & winget install --id GitHub.cli --exact --source winget
    if ($LASTEXITCODE -ne 0) { throw 'Install GitHub CLI, then run this script again.' }
    $executable = Join-Path $env:ProgramFiles 'GitHub CLI\gh.exe'
} else { $executable = $gh.Source }
& $executable auth status
if ($LASTEXITCODE -ne 0) {
    & $executable auth login --hostname github.com --web --git-protocol https
    if ($LASTEXITCODE -ne 0) { throw 'GitHub sign-in did not complete.' }
}
foreach ($name in $secrets.Keys) {
    $start = [Diagnostics.ProcessStartInfo]::new($executable)
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    foreach ($argument in @('secret','set',$name,'--repo','ffboye/velocity')) { [void]$start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::Start($start)
    $process.StandardInput.Write($secrets[$name])
    $process.StandardInput.Close()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "GitHub could not save $name" }
    $process.Dispose()
}
Write-Output 'All five Actions secrets are configured for ffboye/velocity. Private keys were not added to the repository.'
