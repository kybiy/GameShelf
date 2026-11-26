param(
    [string]$ReleaseVersion = ""
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

$RepoRoot = $PSScriptRoot
Set-Location $RepoRoot

# 1. Read pom.xml version and main class
$PomPath = Join-Path $RepoRoot "pom.xml"
if (-not (Test-Path $PomPath)) {
    Fail "pom.xml não encontrado em $RepoRoot."
}

[xml]$Pom = Get-Content $PomPath -Encoding UTF8
$PomVersion = $Pom.project.version.Trim()
if ([string]::IsNullOrWhiteSpace($PomVersion)) {
    Fail "Versão do projeto não encontrada em pom.xml."
}

if ([string]::IsNullOrWhiteSpace($ReleaseVersion)) {
    $ReleaseVersion = $PomVersion
}

# jpackage requires numeric MAJOR.MINOR.PATCH on Windows (without -alpha.1 suffix)
if ($ReleaseVersion -match '^(\d+\.\d+\.\d+)') {
    $NumericVersion = $Matches[1]
} else {
    Fail "Versão inválida para jpackage: '$ReleaseVersion'. Use formato semântico como 0.1.0 ou 0.1.0-alpha.1."
}

Write-Host "=== GameShelf Windows Packaging ===" -ForegroundColor Cyan
Write-Host "Versão do pom.xml : $PomVersion"
Write-Host "Versão da release : $ReleaseVersion (AppVersion Windows: $NumericVersion)"

# 2. Verify JDK 21+ (java, jlink, jpackage)
foreach ($tool in @("java", "jlink", "jpackage")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Fail "Ferramenta '$tool' não encontrada no PATH. Instale um JDK 21+ (ex.: Eclipse Temurin) e adicione bin/ ao PATH."
    }
}

$JavaVersionLine = (cmd /c "java -version 2>&1" | Select-Object -First 1).ToString()
if ($JavaVersionLine -match '"(\d+)') {
    $JavaMajor = [int]$Matches[1]
    if ($JavaMajor -lt 21) {
        Fail "JDK 21 ou superior é obrigatório. Encontrado: $JavaVersionLine"
    }
}
Write-Host "JDK ativo         : $JavaVersionLine"

# 3. Verify Maven
$MvnCmd = $null
$MvnInPath = Get-Command mvn -ErrorAction SilentlyContinue
if ($MvnInPath) {
    $MvnCmd = $MvnInPath.Source
} elseif (Test-Path "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd") {
    $MvnCmd = "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
} else {
    Fail "Apache Maven não encontrado no PATH nem no diretório padrão do NetBeans."
}
Write-Host "Maven             : $MvnCmd"

# 4. Verify WiX Toolset (wix 4+ or candle/light 3.x)
$WixFound = $false
if ((Get-Command wix -ErrorAction SilentlyContinue) -or ((Get-Command candle -ErrorAction SilentlyContinue) -and (Get-Command light -ErrorAction SilentlyContinue))) {
    $WixFound = $true
} else {
    $WixCandidates = @(
        (Join-Path $env:LOCALAPPDATA "Programs\wix314"),
        (Join-Path $env:LOCALAPPDATA "Programs\WiX Toolset v3.14\bin")
    )
    $WixCandidates += Get-ChildItem "C:\Program Files (x86)\WiX Toolset*\bin", "C:\Program Files\WiX Toolset*\bin" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    foreach ($dir in $WixCandidates) {
        if ($dir -and (Test-Path (Join-Path $dir "candle.exe")) -and (Test-Path (Join-Path $dir "light.exe"))) {
            $env:PATH = "$dir;$env:PATH"
            $WixFound = $true
            Write-Host "WiX Toolset       : $dir"
            break
        }
    }
}
if (-not $WixFound) {
    Fail "WiX Toolset não encontrado. Instale o WiX Toolset 3.11+ (ou extraia wix314-binaries em $env:LOCALAPPDATA\Programs\wix314)."
}

# 5. Verify icon
$IconPath = Join-Path $RepoRoot "packaging\gameshelf.ico"
if (-not (Test-Path $IconPath)) {
    Fail "Ícone não encontrado em $IconPath."
}

# 6. Run tests and Maven package
Write-Host "`n[1/5] Executando testes e compilando com Maven..." -ForegroundColor Yellow
& $MvnCmd -B clean package
if ($LASTEXITCODE -ne 0) {
    Fail "Falha na compilação ou nos testes Maven (código $LASTEXITCODE)."
}

$MainJar = Join-Path $RepoRoot "target\gameshelf-$PomVersion.jar"
$LibDir = Join-Path $RepoRoot "target\lib"
if (-not (Test-Path $MainJar) -or -not (Test-Path $LibDir)) {
    Fail "Artefatos esperados não encontrados em target/ após mvn clean package."
}

# 7. Prepare clean jpackage input directory (JAR + runtime libs only)
$InputDir = Join-Path $RepoRoot "target\jpackage-input"
$InputLibDir = Join-Path $InputDir "lib"
if (Test-Path $InputDir) { Remove-Item $InputDir -Recurse -Force }
New-Item -ItemType Directory -Path $InputLibDir -Force | Out-Null
Copy-Item $MainJar -Destination $InputDir
Copy-Item (Join-Path $LibDir "*.jar") -Destination $InputLibDir

# 8. Build minimal Java runtime via jlink
$RuntimeDir = Join-Path $RepoRoot "target\runtime"
if (Test-Path $RuntimeDir) { Remove-Item $RuntimeDir -Recurse -Force }
Write-Host "`n[2/5] Gerando runtime Java otimizado via jlink..." -ForegroundColor Yellow
$Modules = "java.base,java.desktop,java.logging,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported"
& jlink --add-modules $Modules --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output $RuntimeDir
if ($LASTEXITCODE -ne 0) {
    Fail "Falha ao executar jlink (código $LASTEXITCODE)."
}

# 9. Prepare dist directory
$DistDir = Join-Path $RepoRoot "dist"
$AppImageDest = Join-Path $DistDir "app-image"
if (Test-Path $DistDir) { Remove-Item $DistDir -Recurse -Force }
New-Item -ItemType Directory -Path $AppImageDest -Force | Out-Null

# 10. Generate app-image (portable base, no console window)
Write-Host "`n[3/5] Gerando app-image portátil via jpackage..." -ForegroundColor Yellow
& jpackage `
    --type app-image `
    --name "GameShelf" `
    --app-version $NumericVersion `
    --vendor "kybiy" `
    --description "Universal game library and playtime tracker for Windows" `
    --icon $IconPath `
    --input $InputDir `
    --main-jar "gameshelf-$PomVersion.jar" `
    --main-class "br.com.gameshelf.Main" `
    --runtime-image $RuntimeDir `
    --dest $AppImageDest
if ($LASTEXITCODE -ne 0) {
    Fail "Falha ao gerar app-image com jpackage (código $LASTEXITCODE)."
}

$BuiltAppImage = Join-Path $AppImageDest "GameShelf"
$ExePath = Join-Path $BuiltAppImage "GameShelf.exe"
if (-not (Test-Path $ExePath)) {
    Fail "GameShelf.exe não foi gerado em $ExePath."
}

# 11. Package portable ZIP
Write-Host "`n[4/5] Compactando versão portátil..." -ForegroundColor Yellow
$PortableName = "GameShelf-$ReleaseVersion-portable-win-x64.zip"
$PortableZip = Join-Path $DistDir $PortableName
Compress-Archive -Path $BuiltAppImage -DestinationPath $PortableZip -CompressionLevel Optimal
if (-not (Test-Path $PortableZip)) {
    Fail "Falha ao gerar $PortableZip."
}

# 12. Generate per-user installer EXE
Write-Host "`n[5/5] Gerando instalador Windows (.exe) via jpackage..." -ForegroundColor Yellow
& jpackage `
    --type exe `
    --name "GameShelf" `
    --app-version $NumericVersion `
    --vendor "kybiy" `
    --description "Universal game library and playtime tracker for Windows" `
    --icon $IconPath `
    --app-image $BuiltAppImage `
    --win-per-user-install `
    --win-menu `
    --win-menu-group "GameShelf" `
    --win-shortcut `
    --win-shortcut-prompt `
    --win-dir-chooser `
    --win-upgrade-uuid "8f3c2d1e-4b5a-4c6d-9e7f-1a2b3c4d5e6f" `
    --dest $DistDir
if ($LASTEXITCODE -ne 0) {
    Fail "Falha ao gerar o instalador EXE com jpackage (código $LASTEXITCODE)."
}

$DefaultInstaller = Join-Path $DistDir "GameShelf-$NumericVersion.exe"
$InstallerName = "GameShelf-$ReleaseVersion-win-x64.exe"
$FinalInstaller = Join-Path $DistDir $InstallerName
if (-not (Test-Path $DefaultInstaller)) {
    Fail "Instalador esperado não encontrado em $DefaultInstaller."
}
Move-Item -Path $DefaultInstaller -Destination $FinalInstaller -Force

# 13. Generate SHA256SUMS.txt
$SumsPath = Join-Path $DistDir "SHA256SUMS.txt"
$HashLines = foreach ($file in @($FinalInstaller, $PortableZip)) {
    $hash = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $(Split-Path $file -Leaf)"
}
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($SumsPath, $HashLines, $Utf8NoBom)

Write-Host "`n=== Distribuição gerada com sucesso ===" -ForegroundColor Green
Get-ChildItem $FinalInstaller, $PortableZip, $SumsPath, $ExePath | ForEach-Object {
    $mb = [math]::Round($_.Length / 1MB, 2)
    Write-Host (" - {0} ({1} MB)" -f $_.FullName, $mb)
}
Write-Host "`nSHA-256:" -ForegroundColor Cyan
Get-Content $SumsPath
