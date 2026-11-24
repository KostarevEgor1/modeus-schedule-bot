param(
    [ValidateSet('day', 'today', 'tomorrow', 'week', 'next')]
    [string]$View = 'today',
    [string]$Date,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectDirectory 'target/modeus-schedule-bot-0.0.1-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw 'Build the application first: .\mvnw.cmd clean verify'
}
if ($Date -and $View -notin @('day', 'week')) {
    throw '-Date can only be used with -View day or -View week.'
}
if ($Date) {
    $parsedDate = [DateTime]::ParseExact($Date, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
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
foreach ($name in @('MODEUS_AUTH_MODE', 'MODEUS_USERNAME', 'MODEUS_PASSWORD')) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $modeusLogin = Read-Host 'UrFU login (hidden input)' -AsSecureString
    $modeusPassword = Read-Host 'UrFU password (hidden input)' -AsSecureString
    $env:MODEUS_AUTH_MODE = 'SSO'
    $env:MODEUS_USERNAME = [System.Net.NetworkCredential]::new('', $modeusLogin).Password
    $env:MODEUS_PASSWORD = [System.Net.NetworkCredential]::new('', $modeusPassword).Password

    $javaArguments = @('-Djava.net.useSystemProxies=true', '-jar', $jarPath, '--modeus.check=true',
        '--telegram.bot.enabled=false', "--modeus.view=$View")
    if ($Date) {
        $javaArguments += "--modeus.date=$Date"
    }
    & $javaExecutable @javaArguments
} finally {
    foreach ($name in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
    Remove-Variable modeusLogin, modeusPassword, previousValues -ErrorAction SilentlyContinue
}
