# Publica una nueva versión de cups: sube la versión, compila el APK firmado,
# hace commit + push y crea el Release en GitHub con el APK adjunto.
# Uso:  .\publicar.ps1 1.1 "Qué cambió en esta versión"
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notas = ""
)
$ErrorActionPreference = "Continue"  # git escribe avisos por stderr; los pasos cr�ticos comprueban $LASTEXITCODE
Set-Location $PSScriptRoot

if ((git branch --show-current) -ne "main") { throw "Publica desde main (la skill 'publicar' pasa antes los cambios de prueba a main)" }

$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = "C:\Program Files\GitHub CLI\gh.exe" }
if (-not (Test-Path $gh)) { throw "Falta GitHub CLI (winget install GitHub.cli)" }
& $gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw "Primero inicia sesión una sola vez con:  gh auth login" }
if (-not (Test-Path "keystore\cups.keystore")) { throw "Falta keystore\cups.keystore (la llave de firma)" }

# 1. Subir versionName y versionCode
$f = "app\build.gradle.kts"
$txt = Get-Content $f -Raw
$code = [int]([regex]::Match($txt, 'versionCode = (\d+)').Groups[1].Value) + 1
$txt = $txt -replace 'versionCode = \d+', "versionCode = $code" -replace 'versionName = "[^"]*"', "versionName = `"$Version`""
Set-Content $f $txt -NoNewline

# 2. Compilar el APK firmado
.\gradlew.bat assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "Falló la compilación" }
$apk = "app\build\outputs\apk\release\cups-$Version.apk"
Copy-Item "app\build\outputs\apk\release\app-release.apk" $apk -Force

# 3. Commit + push
git add -A
git commit -m "Versión $Version" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
git push
if ($LASTEXITCODE -ne 0) { throw "Falló el push" }

# 4. Release en GitHub con el APK adjunto
if (-not $Notas) { $Notas = "cups $Version" }
& $gh release create "v$Version" $apk --title "cups $Version" --notes $Notas
if ($LASTEXITCODE -ne 0) { throw "Falló la creación del release" }
Write-Host "Listo: cups $Version publicada. Los teléfonos con una versión menor ya ven la actualización."
