# Compatibility entry point; deliveries always use the fixed SoSPD release key.
param([string]$SdkRoot, [switch]$SkipBuild)
& (Join-Path $PSScriptRoot 'package-release.ps1') @PSBoundParameters
