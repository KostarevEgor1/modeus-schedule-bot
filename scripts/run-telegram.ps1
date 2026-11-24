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
foreach ($name in @('TELEGRAM_BOT_ENABLED', 'TELEGRAM_BOT_TOKEN', 'TELEGRAM_BOT_USERNAME')) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $telegramUsername = Read-Host 'Bot username (without @)'
    $telegramSecret = Read-Host 'BotFather token (hidden input)' -AsSecureString
    $env:TELEGRAM_BOT_ENABLED = 'true'
    $env:TELEGRAM_BOT_USERNAME = $telegramUsername.Trim().TrimStart('@')
    $env:TELEGRAM_BOT_TOKEN = [System.Net.NetworkCredential]::new('', $telegramSecret).Password

    & $javaExecutable -jar $jarPath --modeus.check=false
} finally {
    foreach ($name in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
    Remove-Variable telegramSecret, telegramUsername, previousValues -ErrorAction SilentlyContinue
}
