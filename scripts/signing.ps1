function Get-VelocitySigning {
    $directory = Join-Path $env:LOCALAPPDATA 'Velocity\Signing'
    $encrypted = [Convert]::FromBase64String([IO.File]::ReadAllText((Join-Path $directory 'password.dpapi')).Trim())
    $plain = [Security.Cryptography.ProtectedData]::Unprotect($encrypted, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $password = [Text.Encoding]::UTF8.GetString($plain)
    [Array]::Clear($plain, 0, $plain.Length)
    return @{
        ANDROID_KEYSTORE_PATH = Join-Path $directory 'velocity-release.p12'
        ANDROID_KEYSTORE_PASSWORD = $password
        ANDROID_KEY_ALIAS = 'velocity'
        ANDROID_KEY_PASSWORD = $password
        MANIFEST_KEY_PATH = Join-Path $directory 'manifest-key.pem'
    }
}
