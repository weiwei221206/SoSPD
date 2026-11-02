param([string]$SdkRoot, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot) {
    $localSdk = Join-Path $projectRoot '.local\android-sdk'
    if (Test-Path -LiteralPath $localSdk) { $SdkRoot = $localSdk } else { $SdkRoot = $env:ANDROID_HOME }
}
if (-not $SdkRoot -or -not (Test-Path -LiteralPath $SdkRoot)) { throw '请通过 -SdkRoot 指定安卓 SDK 路径。' }
if (-not $SkipBuild) {
    Push-Location $projectRoot
    try {
        & '.\gradlew.bat' ':app:assembleRelease' ':app:validateSigningDebug' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw '构建失败。' }
    } finally { Pop-Location }
}
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory |
    Where-Object Name -Match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw '未找到安卓构建工具。' }
$config = Get-Content -LiteralPath (Join-Path $projectRoot 'app\build.gradle.kts') -Raw
$version = [regex]::Match($config, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $version) { throw '无法读取版本号。' }
$artifactRoot = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
$apk = Join-Path $artifactRoot "SoSPD-$version.apk"
$key = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android\debug.keystore'
if (-not (Test-Path -LiteralPath $key)) { throw '未找到测试签名。请先构建一次 Debug 版本。' }
$unsigned = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
& (Join-Path $buildTools.FullName 'apksigner.bat') sign --ks $key --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $apk $unsigned
if ($LASTEXITCODE -ne 0) { throw '测试签名失败。' }
& (Join-Path $buildTools.FullName 'apksigner.bat') verify $apk
if ($LASTEXITCODE -ne 0) { throw '安装包签名验证失败。' }
$digest = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$apk.sha256", "$digest  $([IO.Path]::GetFileName($apk))`n", [Text.UTF8Encoding]::new($false))
Write-Output "已生成测试签名安装包：$apk"
