param([ValidateSet('Iniciar','Detener','Respaldar')][string]$Accion='Iniciar',[switch]$SinAbrir)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false)
$appRoot=$PSScriptRoot
$dataRoot=Join-Path $appRoot 'datos'
$mariaRoot=Join-Path $appRoot 'runtime\mariadb'
$javaExe=Join-Path $appRoot 'runtime\java\bin\java.exe'
$configFile=Join-Path $dataRoot 'configuracion.json'
$appPidFile=Join-Path $dataRoot 'aplicacion.pid'
$dbClientFile=Join-Path $dataRoot 'conexion.ini'
$dbRootFile=Join-Path $dataRoot 'administracion.ini'
$backupRoot=Join-Path $appRoot 'respaldos'

function Get-OwnedProcess([string]$pidFile,[string]$executable) {
    if (-not (Test-Path -LiteralPath $pidFile)) { return $null }
    $savedId=0
    if (-not [int]::TryParse((Get-Content -LiteralPath $pidFile -Raw).Trim(),[ref]$savedId)) { return $null }
    $savedProcess=Get-Process -Id $savedId -ErrorAction SilentlyContinue
    if ($savedProcess -and $savedProcess.Path -eq $executable) { return $savedProcess }
    return $null
}
function Test-Database {
    if (-not (Test-Path -LiteralPath $dbRootFile)) { return $false }
    $priorPreference=$ErrorActionPreference
    $ErrorActionPreference='Continue'
    try {
        & "$mariaRoot\bin\mariadb-admin.exe" "--defaults-extra-file=$dbRootFile" ping --silent *> $null
        return $LASTEXITCODE -eq 0
    } finally { $ErrorActionPreference=$priorPreference }
}
function Save-Backup([string]$filename) {
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    $backupFile=Join-Path $backupRoot $filename
    & "$mariaRoot\bin\mariadb-dump.exe" "--defaults-extra-file=$dbRootFile" --single-transaction --routines --triggers --hex-blob --default-character-set=utf8mb4 "--result-file=$backupFile" --databases emporio
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo guardar el respaldo de la base.' }
    return $backupFile
}

$lockHash=[System.Security.Cryptography.SHA256]::Create()
try { $lockKey=[BitConverter]::ToString($lockHash.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($appRoot.ToLowerInvariant()))).Replace('-','') } finally { $lockHash.Dispose() }
$serviceLock=New-Object System.Threading.Mutex($false,('Local\Emporio-Servicio-'+$lockKey))
$ownsLock=$false
try {
    try { $ownsLock=$serviceLock.WaitOne(120000) } catch [System.Threading.AbandonedMutexException] { $ownsLock=$true }
    if (-not $ownsLock) { throw 'Emporio está ocupado. Espera un momento y vuelve a intentarlo.' }

if ($Accion -eq 'Detener') {
    $runningApp=Get-OwnedProcess $appPidFile $javaExe
    if ($runningApp) { Stop-Process -Id $runningApp.Id }
    if (Test-Database) {
        & "$mariaRoot\bin\mariadb-admin.exe" "--defaults-extra-file=$dbRootFile" shutdown
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener la base de datos.' }
    }
    'Emporio detenido.'
    exit
}

if ($Accion -eq 'Respaldar') {
    if (-not (Test-Database)) { throw 'Abre Emporio antes de guardar un respaldo.' }
    $savedBackup=Save-Backup ('emporio-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.sql')
    "Respaldo guardado en: $savedBackup"
    exit
}

New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath $configFile)) {
    $settings=@{
        rootPassword=[guid]::NewGuid().ToString('N')
        dbPassword=[guid]::NewGuid().ToString('N')
        adminPassword=('Emporio-'+[guid]::NewGuid().ToString('N').Substring(0,16))
        databasePort=3307
        appPort=8080
    }
    $settings | ConvertTo-Json | Set-Content -LiteralPath $configFile -Encoding UTF8
}
$settings=Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
$localUrl="http://127.0.0.1:$($settings.appPort)"
if (Get-OwnedProcess $appPidFile $javaExe) {
    if (-not $SinAbrir) { Start-Process $localUrl }
    "Emporio disponible en $localUrl"
    exit
}
@"
[client]
host=127.0.0.1
port=$($settings.databasePort)
user=root
password=$($settings.rootPassword)
"@ | Set-Content -LiteralPath $dbRootFile -Encoding ASCII
@"
[client]
host=127.0.0.1
port=$($settings.databasePort)
user=emporio_app
password=$($settings.dbPassword)
"@ | Set-Content -LiteralPath $dbClientFile -Encoding ASCII

$databaseData=Join-Path $dataRoot 'base'
$databaseIni=Join-Path $databaseData 'my.ini'
if (Test-Path -LiteralPath $databaseIni) {
    $iniText=[System.IO.File]::ReadAllText($databaseIni)
    $newDataPath=$databaseData.Replace('\','/')
    $newPluginPath=(Join-Path $mariaRoot 'lib\plugin').Replace('\','/')
    $iniText=[regex]::Replace($iniText,'(?m)^datadir=.*$',('datadir='+$newDataPath).Replace('$','$$'))
    $iniText=[regex]::Replace($iniText,'(?m)^plugin-dir=.*$',('plugin-dir='+$newPluginPath).Replace('$','$$'))
    $iniText=[regex]::Replace($iniText,'(?m)^port=.*$',('port='+$settings.databasePort))
    [System.IO.File]::WriteAllText($databaseIni,$iniText,(New-Object System.Text.UTF8Encoding($false)))
}
if (-not (Test-Database)) {
    $portProbe=New-Object System.Net.Sockets.TcpClient
    try { $portProbe.Connect('127.0.0.1',[int]$settings.databasePort); $portBusy=$true } catch { $portBusy=$false } finally { $portProbe.Dispose() }
    if ($portBusy) { throw "El puerto $($settings.databasePort) está ocupado por otra base. Cierra la otra instancia antes de abrir Emporio." }
    if (-not (Test-Path -LiteralPath "$databaseData\mysql")) {
        & "$mariaRoot\bin\mariadb-install-db.exe" "--datadir=$databaseData" "--password=$($settings.rootPassword)" "--port=$($settings.databasePort)" --silent
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar la base de datos.' }
    }
    $dbArguments=@(('"--defaults-file='+$databaseData+'\my.ini"'),'--bind-address=127.0.0.1','--console')
    $databaseProcess=Start-Process -FilePath "$mariaRoot\bin\mariadbd.exe" -ArgumentList $dbArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dataRoot\base-salida.log" -RedirectStandardError "$dataRoot\base-error.log"
    $databaseProcess.Id | Set-Content -LiteralPath "$dataRoot\base.pid"
    $ready=$false
    for ($attempt=0;$attempt -lt 40;$attempt++) {
        Start-Sleep -Milliseconds 500
        if (Test-Database) { $ready=$true; break }
        $databaseProcess.Refresh()
        if ($databaseProcess.HasExited) { break }
    }
    if (-not $ready) { throw 'La base de datos no pudo iniciar. Revisa datos/base-error.log.' }
}
$bootstrapSql=@"
CREATE DATABASE IF NOT EXISTS emporio CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'emporio_app'@'127.0.0.1' IDENTIFIED BY '$($settings.dbPassword)';
GRANT ALL PRIVILEGES ON emporio.* TO 'emporio_app'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
$bootstrapSql | & "$mariaRoot\bin\mariadb.exe" "--defaults-extra-file=$dbRootFile" --default-character-set=utf8mb4
if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir la base del negocio.' }
@"
EMPORIO - ACCESO INICIAL

Dirección: $localUrl
Usuario: admin
Contraseña inicial: $($settings.adminPassword)

Puedes cambiarla desde Mi cuenta dentro de Emporio.

BASE DE DATOS
Host: 127.0.0.1
Puerto: $($settings.databasePort)
Base: emporio
Usuario: emporio_app
Contraseña: $($settings.dbPassword)
"@ | Set-Content -LiteralPath "$appRoot\CREDENCIALES.txt" -Encoding UTF8

$env:DB_URL="jdbc:mariadb://127.0.0.1:$($settings.databasePort)/emporio"
$env:DB_USER='emporio_app'; $env:DB_PASSWORD=$settings.dbPassword
$env:ADMIN_USERNAME='admin'; $env:ADMIN_PASSWORD=$settings.adminPassword
$env:APP_HOST='127.0.0.1'; $env:APP_PORT=[string]$settings.appPort
$jarFile=Join-Path $PSScriptRoot 'Emporio.jar'
$appProcess=Start-Process -FilePath $javaExe -ArgumentList @('-jar',('"'+$jarFile+'"')) -WorkingDirectory $appRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dataRoot\aplicacion-salida.log" -RedirectStandardError "$dataRoot\aplicacion-error.log"
$appProcess.Id | Set-Content -LiteralPath $appPidFile
$appReady=$false
for ($attempt=0;$attempt -lt 90;$attempt++) {
    Start-Sleep -Milliseconds 500
    $appProcess.Refresh()
    if ($appProcess.HasExited) { throw 'Emporio no pudo iniciar. Revisa datos/aplicacion-salida.log.' }
    try { $response=Invoke-WebRequest "$localUrl/login" -UseBasicParsing -TimeoutSec 2; if ($response.StatusCode -eq 200) { $appReady=$true; break } } catch {}
}
if (-not $appReady) { throw 'Emporio está tardando en iniciar. Revisa datos/aplicacion-salida.log.' }
$dailyBackup='emporio-'+(Get-Date -Format 'yyyyMMdd')+'.sql'
if (-not (Test-Path -LiteralPath (Join-Path $backupRoot $dailyBackup))) {
    try { Save-Backup $dailyBackup | Out-Null } catch { Write-Warning 'Emporio inició, pero no se pudo guardar la copia diaria. Usa Guardar copia de seguridad en Opciones.' }
}
if (-not $SinAbrir) { Start-Process $localUrl }
"Emporio abierto: $localUrl"
'Credenciales iniciales en CREDENCIALES.txt.'
} finally {
    if ($ownsLock) { $serviceLock.ReleaseMutex() }
    $serviceLock.Dispose()
}
