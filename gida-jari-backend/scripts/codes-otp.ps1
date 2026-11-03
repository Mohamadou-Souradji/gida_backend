# Gida Jari - affiche les 10 derniers codes de connexion (mode simulation SMS).
# powershell -ExecutionPolicy Bypass -File C:\Users\Abd-Hafiz\gida-jari-backend\scripts\codes-otp.ps1
$log = Join-Path (Split-Path -Parent $PSScriptRoot) "backend.log"
if (-not (Test-Path $log)) { Write-Host "Pas de backend.log : lance d'abord demarrer-local.ps1"; exit 1 }
Select-String -Path $log -Pattern "CODE OTP" | Select-Object -Last 10 | ForEach-Object {
    if ($_.Line -match 'pour (\+\d+)(?: \(email ([^)]+)\))? : (\d+)') { "{0}  {1}  ->  {2}" -f $Matches[1], $Matches[2], $Matches[3] }
}
