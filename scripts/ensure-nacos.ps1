param(
    [string]$NacosHome = 'E:\Applications\nacos',
    [int]$Port = 8848,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'

function Test-LocalPort([int]$TargetPort) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $client.BeginConnect('127.0.0.1', $TargetPort, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($pending)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

# Nacos clients require both the HTTP port and the client gRPC port.
function Test-NacosPorts {
    return ((Test-LocalPort $Port) -and (Test-LocalPort ($Port + 1000)))
}

if (Test-NacosPorts) {
    Write-Output 'Nacos ports are ready; reusing the running instance.'
    exit 0
}

# Serialize startup when multiple IDEA configurations run at the same time.
$mutex = New-Object System.Threading.Mutex($false, "Local\RednoteNacosStartup$Port")
$locked = $false
try {
    try { $locked = $mutex.WaitOne($TimeoutSeconds * 1000) }
    catch [System.Threading.AbandonedMutexException] { $locked = $true }
    if (-not $locked) { throw 'Timed out waiting for another Nacos startup task.' }
    if (Test-NacosPorts) { exit 0 }

    $startup = Join-Path $NacosHome 'start-standalone.cmd'
    if (-not (Test-Path -LiteralPath $startup)) { throw "Startup script not found: $startup" }
    if (-not (Test-LocalPort $Port)) {
        Write-Output 'Starting local Nacos...'
        Start-Process -FilePath $env:ComSpec -ArgumentList ('/d /c ""{0}""' -f $startup) -WorkingDirectory $NacosHome -WindowStyle Hidden
    }

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (Test-NacosPorts) {
            Write-Output 'Nacos ports are ready.'
            exit 0
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Nacos ports are not ready. Check port conflicts and logs in $NacosHome\logs."
} catch {
    Write-Error $_
    exit 1
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
