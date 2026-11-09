$ErrorActionPreference = 'Stop'

$vehicleAppDirectory = $PSScriptRoot
$repositoryRoot = (Resolve-Path (Join-Path $vehicleAppDirectory '..')).Path
$signingDirectory = Join-Path $repositoryRoot '.tools/signing'
$keystorePath = Join-Path $signingDirectory 'moagil-release.jks'
$encryptedPasswordPath = Join-Path $signingDirectory 'moagil-release-password.dpapi'
$keyAlias = 'moagil-release'
$javaHomeDirectory = Join-Path $vehicleAppDirectory '.tools/jdk17/jdk-17.0.20.1+1'
$keytoolPath = Join-Path $javaHomeDirectory 'bin/keytool.exe'
$releaseApkPath = Join-Path $vehicleAppDirectory 'app/build/outputs/apk/pleos/release/app-pleos-arm64-v8a-release.apk'
$submissionApkPath = Join-Path $repositoryRoot 'apk/최민수_모아길_release.apk'

New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null

if ((Test-Path -LiteralPath $keystorePath) -xor (Test-Path -LiteralPath $encryptedPasswordPath)) {
    throw "Signing files are incomplete under $signingDirectory. Keep the keystore and encrypted password file together."
}

if (-not (Test-Path -LiteralPath $keystorePath)) {
    if (-not (Test-Path -LiteralPath $keytoolPath)) {
        throw 'The bundled JDK 17 keytool was not found. Install JDK 17 and create the release keystore, or restore the ignored local signing files.'
    }

    $randomBytes = New-Object byte[] 32
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
    $releasePassword = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $env:MOAGIL_RELEASE_STORE_PASSWORD = $releasePassword

    try {
        & $keytoolPath -genkeypair -alias $keyAlias -keyalg RSA -keysize 3072 -sigalg SHA256withRSA `
            -validity 10000 -keystore $keystorePath -storetype JKS `
            -dname 'CN=MOAGIL Release, O=MOAGIL, C=KR' `
            -storepass:env MOAGIL_RELEASE_STORE_PASSWORD -keypass:env MOAGIL_RELEASE_STORE_PASSWORD -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the MOAGIL release keystore.' }

        $secureReleasePassword = ConvertTo-SecureString -String $releasePassword -AsPlainText -Force
        $encryptedReleasePassword = ConvertFrom-SecureString -SecureString $secureReleasePassword
        [IO.File]::WriteAllText($encryptedPasswordPath, $encryptedReleasePassword, [Text.Encoding]::UTF8)
        $secureReleasePassword.Dispose()
    } finally {
        Remove-Item Env:MOAGIL_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
        $releasePassword = $null
    }
}

if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $javaHomeDirectory 'bin/java.exe'))) {
    $env:JAVA_HOME = $javaHomeDirectory
}
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $repositoryRoot '.tools/gradle-home'
}

$securePassword = ConvertTo-SecureString -String ([IO.File]::ReadAllText($encryptedPasswordPath).Trim())
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $releasePasswordForBuild = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:MOAGIL_RELEASE_STORE_FILE = $keystorePath
    $env:MOAGIL_RELEASE_STORE_PASSWORD = $releasePasswordForBuild
    $env:MOAGIL_RELEASE_KEY_ALIAS = $keyAlias
    $env:MOAGIL_RELEASE_KEY_PASSWORD = $releasePasswordForBuild

    Push-Location $vehicleAppDirectory
    try {
        & .\gradlew.bat --no-daemon --console=plain testMockDebugUnitTest testPleosDebugUnitTest assemblePleosRelease
        if ($LASTEXITCODE -ne 0) { throw 'Android tests or the signed Pleos release build failed.' }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path -LiteralPath $releaseApkPath)) {
        throw "Signed Pleos ARM64 APK was not produced at $releaseApkPath."
    }

    Copy-Item -LiteralPath $releaseApkPath -Destination $submissionApkPath -Force
    $apkHash = (Get-FileHash -LiteralPath $submissionApkPath -Algorithm SHA256).Hash
    Write-Output "Signed Pleos release APK: $submissionApkPath"
    Write-Output "SHA-256: $apkHash"
} finally {
    Remove-Item Env:MOAGIL_RELEASE_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:MOAGIL_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MOAGIL_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:MOAGIL_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    $securePassword.Dispose()
    $releasePasswordForBuild = $null
}
