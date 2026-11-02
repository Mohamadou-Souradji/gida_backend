# =====================================================================
#  Gida Jari - demarrage local pour tester sur ton PC (Windows)
#
#  Lancement (PowerShell, depuis n'importe ou) :
#    powershell -ExecutionPolicy Bypass -File C:\Users\Abd-Hafiz\gida-jari-backend\scripts\demarrer-local.ps1
#
#  Le script :
#    1. verifie Java 17+, Maven, Node.js 18+ et PostgreSQL ;
#    2. cree la base gida_jari (utilisateur gida_jari / change_me) si elle n'existe pas ;
#    3. ouvre une fenetre "BACKEND" (Spring Boot, port 8080) ;
#    4. ouvre une fenetre "FRONTEND" (Angular, port 4200) ;
#    5. ouvre le navigateur sur http://localhost:4200 .
#  Les codes de connexion (OTP) s'affichent dans la fenetre BACKEND.
#  Pour les revoir : powershell -ExecutionPolicy Bypass -File ...\scripts\codes-otp.ps1
# =====================================================================

# "Continue" : sous Windows PowerShell 5.1, "Stop" ferait echouer java -version (qui ecrit sur stderr)
$ErrorActionPreference = "Continue"
$backend  = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path (Split-Path -Parent $backend) "gida-jari-frontend"
$log      = Join-Path $backend "backend.log"

function Ok($m)    { Write-Host "  [OK] $m" -ForegroundColor Green }
function Ko($m)    { Write-Host "  [MANQUE] $m" -ForegroundColor Red }
function Info($m)  { Write-Host $m -ForegroundColor Cyan }

function PortOuvert($port) {
    try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect("localhost", $port); $c.Close(); return $true }
    catch { return $false }
}

Info "`n=== 1. Verification des outils ==="
$manque = $false

# Java : PATH, sinon JAVA_HOME, sinon les dossiers d'installation habituels (Maven, IntelliJ...)
$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java -and $env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $java = Join-Path $env:JAVA_HOME "bin\java.exe"
}
if (-not $java) {
    $java = Get-ChildItem -Path "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Microsoft",
            "C:\Program Files\Zulu", "C:\Program Files\Amazon Corretto", "$env:USERPROFILE\.jdks",
            "C:\Program Files\JetBrains" -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\bin\\java\.exe$' } | Sort-Object FullName -Descending |
            Select-Object -First 1 -ExpandProperty FullName
}
if ($java) {
    $v = (& $java -version 2>&1 | Select-Object -First 1).ToString()
    if ($v -match '"(\d+)') { $maj = [int]$Matches[1] } else { $maj = 0 }
    if ($maj -ge 17) {
        # Rend ce Java visible pour Maven et les fenetres ouvertes par ce script
        $jdk = Split-Path -Parent (Split-Path -Parent $java)
        $env:JAVA_HOME = $jdk
        $env:Path = (Join-Path $jdk "bin") + ";" + $env:Path
        Ok "Java $maj ($jdk)"
    } else { Ko "Java 17 ou plus requis (trouve : $v dans $java) -> https://adoptium.net"; $manque = $true }
} else { Ko "Java introuvable -> installe Temurin 17 : https://adoptium.net (coche 'Set JAVA_HOME')"; $manque = $true }

if (Get-Command mvn -ErrorAction SilentlyContinue) { Ok "Maven" }
else { Ko "Maven introuvable -> https://maven.apache.org/download.cgi (ajoute bin au PATH)"; $manque = $true }

try {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "absent" }
    $n = (& node -v).TrimStart('v'); $nmaj = [int]($n.Split('.')[0])
    if ($nmaj -ge 18) { Ok "Node.js $n" } else { Ko "Node.js 18 ou plus requis (trouve : $n)"; $manque = $true }
} catch { Ko "Node.js introuvable -> https://nodejs.org (version LTS)"; $manque = $true }

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if ($psql) { Ok "PostgreSQL ($psql)" }
else { Ko "PostgreSQL introuvable -> https://www.postgresql.org/download/windows/ (note le mot de passe 'postgres')"; $manque = $true }

if (-not (Test-Path $frontend)) { Ko "Dossier frontend introuvable : $frontend"; $manque = $true }

if ($manque) {
    Write-Host "`nInstalle ce qui manque, ferme puis rouvre PowerShell, et relance ce script." -ForegroundColor Yellow
    exit 1
}

Info "`n=== 2. Base de donnees ==="
if (-not (PortOuvert 5432)) {
    Write-Host "  PostgreSQL ne repond pas sur le port 5432." -ForegroundColor Yellow
    Write-Host "  Demarre le service : touche Windows > 'Services' > postgresql-x64-.. > Demarrer, puis relance." -ForegroundColor Yellow
    exit 1
}
$mdp = Read-Host "  Mot de passe de l'utilisateur 'postgres' (choisi a l'installation)" -AsSecureString
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($mdp))
$existeUser = & $psql -U postgres -h localhost -tAc "SELECT 1 FROM pg_roles WHERE rolname='gida_jari'"
if ($LASTEXITCODE -ne 0) { Write-Host "  Connexion refusee : mot de passe 'postgres' incorrect ?" -ForegroundColor Red; exit 1 }
if ($existeUser -ne "1") { & $psql -U postgres -h localhost -c "CREATE USER gida_jari PASSWORD 'change_me';" | Out-Null; Ok "Utilisateur gida_jari cree" }
else { Ok "Utilisateur gida_jari deja present" }
$existeBase = & $psql -U postgres -h localhost -tAc "SELECT 1 FROM pg_database WHERE datname='gida_jari'"
if ($existeBase -ne "1") { & $psql -U postgres -h localhost -c "CREATE DATABASE gida_jari OWNER gida_jari;" | Out-Null; Ok "Base gida_jari creee" }
else { Ok "Base gida_jari deja presente" }
Remove-Item Env:PGPASSWORD

Info "`n=== 3. Backend (fenetre BACKEND) ==="
if (PortOuvert 8080) { Ok "Un backend tourne deja sur le port 8080" }
else {
    $cmd = "`$host.UI.RawUI.WindowTitle='BACKEND Gida Jari'; cd '$backend'; mvn spring-boot:run 2>&1 | Tee-Object -FilePath '$log'"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd
    Write-Host "  Demarrage (1 a 3 min la premiere fois)..." -NoNewline
    for ($i = 0; $i -lt 180 -and -not (PortOuvert 8080); $i++) { Start-Sleep 2; Write-Host "." -NoNewline }
    Write-Host ""
    if (PortOuvert 8080) { Ok "Backend pret sur http://localhost:8080" }
    else { Write-Host "  Le backend n'a pas demarre : regarde l'erreur dans la fenetre BACKEND." -ForegroundColor Red; exit 1 }
}

Info "`n=== 4. Frontend (fenetre FRONTEND) ==="
if (PortOuvert 4200) { Ok "Un frontend tourne deja sur le port 4200" }
else {
    $installer = if (Test-Path (Join-Path $frontend "node_modules")) { "" } else { "npm install; " }
    $cmd = "`$host.UI.RawUI.WindowTitle='FRONTEND Gida Jari'; cd '$frontend'; ${installer}npm start"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd
    if ($installer) { Write-Host "  Premiere fois : installation des dependances (3 a 10 min)..." -NoNewline }
    else { Write-Host "  Compilation Angular..." -NoNewline }
    for ($i = 0; $i -lt 450 -and -not (PortOuvert 4200); $i++) { Start-Sleep 2; Write-Host "." -NoNewline }
    Write-Host ""
    if (-not (PortOuvert 4200)) { Write-Host "  Le frontend n'a pas demarre : regarde la fenetre FRONTEND." -ForegroundColor Red; exit 1 }
    Start-Sleep 5
    Ok "Frontend pret"
}

Start-Process "http://localhost:4200"
Info "`n=== C'est parti ! ==="
Write-Host "  Application : http://localhost:4200"
Write-Host "  Admin       : numero 90 00 00 00 (choisis 'Client' a la 1re connexion)"
Write-Host "  Codes OTP   : fenetre BACKEND, ou lance scripts\codes-otp.ps1"
Write-Host "  Pour arreter : ferme les fenetres BACKEND et FRONTEND.`n"
