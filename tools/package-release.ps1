param([string]$SdkRoot, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$credentials = Join-Path $projectRoot '.signing\signing.properties'
$certificate = Join-Path $projectRoot 'signing\sospd-release.cer'
if (-not (Test-Path -LiteralPath $credentials) -or -not (Test-Path -LiteralPath $certificate)) {
    throw '缺少 SoSPD 固定签名。请恢复 .signing 目录及其签名配置；不要重新生成密钥。'
}
if (-not $SdkRoot) {
    $localSdk = Join-Path $projectRoot '.local\android-sdk'
    if (Test-Path -LiteralPath $localSdk) { $SdkRoot = $localSdk } else { $SdkRoot = $env:ANDROID_HOME }
}
if (-not $SdkRoot -or -not (Test-Path -LiteralPath $SdkRoot)) { throw '请通过 -SdkRoot 指定安卓 SDK 路径。' }
if (-not $SkipBuild) {
    Push-Location $projectRoot
    try {
        & '.\gradlew.bat' ':app:assembleRelease' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw '构建失败。' }
    } finally { Pop-Location }
}
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory |
    Where-Object Name -Match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw '未找到安卓构建工具。' }
$signed = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $signed)) { throw '没有找到使用固定签名的 Release APK，请先构建。' }
$signature = & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $signed 2>&1
if ($LASTEXITCODE -ne 0) { throw '安装包签名验证失败。' }
$expected = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
$fingerprints = @($signature | ForEach-Object {
    $match = [regex]::Match([string]$_, '^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)\s*$')
    if ($match.Success) { $match.Groups[1].Value.ToLowerInvariant() }
})
if ($fingerprints.Count -ne 1 -or $fingerprints[0] -ne $expected) {
    throw '安装包签名与已登记的 SoSPD 固定证书不一致，拒绝交付。'
}
$config = Get-Content -LiteralPath (Join-Path $projectRoot 'app\build.gradle.kts') -Raw
$version = [regex]::Match($config, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $version) { throw '无法读取版本号。' }
$artifactRoot = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
$apk = Join-Path $artifactRoot "SoSPD-$version.apk"
Copy-Item -LiteralPath $signed -Destination $apk
$digest = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$apk.sha256", "$digest  $([IO.Path]::GetFileName($apk))`n", [Text.UTF8Encoding]::new($false))
Write-Output "已生成固定签名安装包：$apk"
Write-Output "签名证书 SHA-256：$expected"
