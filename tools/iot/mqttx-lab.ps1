param(
    [Parameter(Mandatory)]
    [ValidateSet('tunnel', 'ca', 'password', 'payload')]
    [string] $Action,

    [ValidateSet('switch', 'temperature')]
    [string] $Device = 'switch',

    [string] $Value,

    [string] $CaFile = 'D:/UnityProject/jtyf-dts-webgpu/artifacts/Builds/latest/iot-m3-03a-20261114/lab148-mqtt-ca.crt'
)

$ErrorActionPreference = 'Stop'
$node = 'root@192.168.10.148'

switch ($Action) {
    'tunnel' {
        $listener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 18884 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($listener) {
            $process = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess)
            if ($process.Name -eq 'ssh.exe' -and $process.CommandLine -match '(?:127\.0\.0\.1:)?18884:127\.0\.0\.1:18884' -and
                $process.CommandLine -match 'root@192\.168\.10\.148') {
                Write-Output '本机到 148 的 MQTT 转发已经运行。'
                return
            }
            throw '本机 18884 正被其它进程占用。'
        }
        # 转发只绑定本机。这个窗口保持运行，MQTTX 才能使用本地端口。
        ssh -N -o BatchMode=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=20 -L 127.0.0.1:18884:127.0.0.1:18884 $node
        if ($LASTEXITCODE -ne 0) { throw 'MQTT 转发未启动，请先核对 SSH 连接和本机端口。' }
    }
    'ca' {
        scp "${node}:/home/wx-iot-mqtt-lab148/ca.crt" $CaFile
        if ($LASTEXITCODE -ne 0) { throw '代理证书下载失败。' }
        Write-Output $CaFile
    }
    'password' {
        # 账号只用于该设备的专用主题。密码经现有 SSH 权限读取并直接进入本机剪贴板。
        $raw = ssh -o BatchMode=yes $node 'cat /home/wx-iot-mqtt-lab148/writers.private.json'
        if ($LASTEXITCODE -ne 0) { throw '没有读取试运行发布凭据的权限。' }
        $credentialName = 'lab-' + $Device
        $secret = ($raw | ConvertFrom-Json).PSObject.Properties[$credentialName].Value
        if ([string]::IsNullOrWhiteSpace($secret)) { throw '找不到所选设备的发布凭据。' }
        Set-Clipboard -Value $secret
        Write-Output ('已复制 MQTTX 账号 ' + $credentialName + ' 的密码。')
    }
    'payload' {
        # 每次生成新的时间与追踪编号，重复测试时可直接重发上一次报文。
        if ([string]::IsNullOrWhiteSpace($Value)) { throw '请用 -Value 指定开关或温度值。' }
        $payload = [ordered]@{
            externalDeviceId = 'virtual-' + $Device
            eventTime = [DateTimeOffset]::UtcNow.ToString('o')
            traceId = [Guid]::NewGuid().ToString('N')
            quality = 'GOOD'
        }
        if ($Device -eq 'switch') {
            if ($Value -cnotin @('true', 'false')) { throw '开关只接受 true 或 false。' }
            $payload.pointCode = 'rawVisible'
            $payload.valueType = 'BOOLEAN'
            $payload.value = $Value -ceq 'true'
        } else {
            $payload.pointCode = 'rawTemperature'
            $payload.valueType = 'NUMBER'
            $payload.value = [decimal]::Parse($Value, [System.Globalization.CultureInfo]::InvariantCulture)
            $payload.unit = '°C'
        }
        $body = $payload | ConvertTo-Json -Depth 4
        Set-Clipboard -Value $body
        Write-Output $body
    }
}
