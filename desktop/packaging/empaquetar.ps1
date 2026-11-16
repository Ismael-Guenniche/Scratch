# Empaqueta desktop/build/Scratch.jar para Windows: Java mínimo (jlink), carpeta portable e instalador .exe.
# Requisitos: JDK 17+ con jlink/jpackage y WiX Toolset 3 (viene en los ejecutores de GitHub).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..\..')
$build = 'desktop\build'
$dist = Join-Path $build 'dist'
if (-not (Test-Path "$build\Scratch.jar")) { throw 'Falta desktop\build\Scratch.jar: ejecuta antes desktop/build.sh' }
Remove-Item -Recurse -Force $dist, "$build\runtime", "$build\in", "$build\image" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $dist, "$build\in" | Out-Null
Copy-Item "$build\Scratch.jar" "$build\in\"

$gradle = Get-Content 'app\build.gradle' -Raw
$version = [regex]::Match($gradle, "versionName '([^']+)'").Groups[1].Value
if (-not $version) { $version = '0.1' }
Write-Host "Scratch $version"

if ($env:WIX -and (Test-Path "$env:WIX\bin")) { $env:PATH = "$env:WIX\bin;$env:PATH" }

# Java reducido a lo que usa la app (ventanas, sonido, red con TLS y nombres de fechas en español).
& jlink --add-modules java.base,java.desktop,jdk.crypto.ec,jdk.localedata `
  --include-locales=en,es,ja --strip-debug --no-header-files --no-man-pages --compress=zip-6 `
  --output "$build\runtime"
if ($LASTEXITCODE -ne 0) { throw 'jlink falló' }

$common = @(
  '--name', 'Scratch',
  '--app-version', $version,
  '--vendor', 'Ismael Guenniche',
  '--description', 'Reproductor de musica local con rueda de scratch',
  '--copyright', 'Scratch'
)
# Poca memoria: recolector serie, montón pequeño que se devuelve al sistema y pilas de hilo cortas.
$javaOpts = @(
  '--java-options', '-XX:+UseSerialGC',
  '--java-options', '-Xms24m',
  '--java-options', '-Xmx768m',
  '--java-options', '-XX:MinHeapFreeRatio=10',
  '--java-options', '-XX:MaxHeapFreeRatio=30',
  '--java-options', '-Xss512k',
  '--java-options', '-Dfile.encoding=UTF-8'
)

& jpackage --type app-image @common --input "$build\in" --main-jar Scratch.jar `
  --main-class dev.scratchwheel.DesktopMain --runtime-image "$build\runtime" `
  --icon 'desktop\packaging\Scratch.ico' @javaOpts --dest "$build\image"
if ($LASTEXITCODE -ne 0) { throw 'jpackage (app-image) falló' }

Compress-Archive -Path "$build\image\Scratch" -DestinationPath "$dist\Scratch-$version-windows-portable.zip"

# Instalador por usuario (sin permisos de administrador), con acceso directo en el escritorio y el menú Inicio.
& jpackage --type exe @common --app-image "$build\image\Scratch" --dest $dist `
  --win-per-user-install --win-menu --win-menu-group 'Scratch' --win-shortcut `
  --win-upgrade-uuid '6f1d2c0e-5b7a-4c1e-9a53-5c2a7e1f0d67'
if ($LASTEXITCODE -ne 0) { throw 'jpackage (exe) falló' }

Get-ChildItem $dist | ForEach-Object { Write-Host ("{0}  {1:N1} MB" -f $_.Name, ($_.Length / 1MB)) }
