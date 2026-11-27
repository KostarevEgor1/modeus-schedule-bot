param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectDirectory 'target/modeus-schedule-bot-0.0.1-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw 'Build the application first: .\mvnw.cmd clean verify'
}

$javaExecutable = if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    (Get-Command java -ErrorAction Stop).Source
} else {
    Join-Path $JavaHome 'bin/java.exe'
}

if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw 'JDK not found. Set JAVA_HOME or pass -JavaHome with a JDK directory.'
}

$previousValues = @{}
foreach ($name in @('TELEGRAM_BOT_ENABLED', 'TELEGRAM_BOT_TOKEN', 'TELEGRAM_BOT_USERNAME',
        'TELEGRAM_BOT_OWNER_ID', 'MODEUS_AUTH_MODE', 'MODEUS_USERNAME', 'MODEUS_PASSWORD')) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $ownerInput = (Read-Host 'Telegram owner ID (press Enter to look it up using /id)').Trim()
    $ownerId = 0L
    if ($ownerInput -and (-not [long]::TryParse($ownerInput, [ref]$ownerId) -or $ownerId -le 0)) {
        throw 'Enter a positive numeric Telegram user ID.'
    }
    $telegramUsername = Read-Host 'Bot username (without @)'
    $telegramSecret = Read-Host 'BotFather token (hidden input)' -AsSecureString
    $env:TELEGRAM_BOT_ENABLED = 'true'
    $env:TELEGRAM_BOT_USERNAME = $telegramUsername.Trim().TrimStart('@')
    $env:TELEGRAM_BOT_TOKEN = [System.Net.NetworkCredential]::new('', $telegramSecret).Password
    $env:TELEGRAM_BOT_OWNER_ID = $null

    if ($ownerId -gt 0) {
        $modeusLogin = Read-Host 'UrFU login (hidden input)' -AsSecureString
        $modeusPassword = Read-Host 'UrFU password (hidden input)' -AsSecureString
        $env:TELEGRAM_BOT_OWNER_ID = [string]$ownerId
        $env:MODEUS_AUTH_MODE = 'SSO'
        $env:MODEUS_USERNAME = [System.Net.NetworkCredential]::new('', $modeusLogin).Password
        $env:MODEUS_PASSWORD = [System.Net.NetworkCredential]::new('', $modeusPassword).Password
    }

    & $javaExecutable '-Djava.net.useSystemProxies=true' -jar $jarPath --modeus.check=false
} finally {
    foreach ($name in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
    Remove-Variable telegramSecret, telegramUsername, modeusLogin, modeusPassword, previousValues -ErrorAction SilentlyContinue
}
