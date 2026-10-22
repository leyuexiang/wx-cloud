[CmdletBinding()]
param(
    [string]$DbHost = $(if ($env:IOT_DB_HOST) { $env:IOT_DB_HOST } else { '192.168.10.148' }),
    [int]$DbPort = $(if ($env:IOT_DB_PORT) { [int]$env:IOT_DB_PORT } else { 3306 }),
    [string]$DbName = $(if ($env:IOT_DB_NAME) { $env:IOT_DB_NAME } else { 'wx_iot' }),
    [string]$DbUser = $(if ($env:IOT_DB_USERNAME) { $env:IOT_DB_USERNAME } else { 'iot_app' })
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 只读核验迁移前置条件；密码通过进程环境传给 mysql，不出现在命令行和脚本输出中。
$dbPassword = $env:IOT_DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    throw '请通过 IOT_DB_PASSWORD 注入数据库密码，不要把密码写入命令行或脚本。'
}
if ($DbPort -lt 1 -or $DbPort -gt 65535) {
    throw 'IOT_DB_PORT 必须是 1 到 65535 之间的端口。'
}

$mysqlCommand = Get-Command mysql.exe -ErrorAction SilentlyContinue
if ($null -eq $mysqlCommand) {
    throw '未找到 mysql.exe，请安装 MySQL/MariaDB 客户端后再执行核验。'
}

$query = @'
SELECT CONCAT('server_version=', VERSION());
SELECT CONCAT('table_wx_iot_connector=', IF(COUNT(*) = 1, 'OK', 'MISSING'))
  FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'wx_iot_connector' AND table_type = 'BASE TABLE';
SELECT CONCAT('column_runtime_owner=', IF(COUNT(*) = 1, 'OK', 'MISSING'))
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'wx_iot_connector' AND column_name = 'runtime_owner';
SELECT CONCAT('column_runtime_lease_until=', IF(COUNT(*) = 1, 'OK', 'MISSING'))
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'wx_iot_connector' AND column_name = 'runtime_lease_until';
SELECT CONCAT('index_discovery=', IF(COUNT(*) >= 1, 'OK', 'MISSING'))
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'wx_iot_connector' AND index_name = 'idx_iot_connector_discovery';
'@

$oldMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
[Environment]::SetEnvironmentVariable('MYSQL_PWD', $dbPassword, 'Process')
try {
    # 仅执行 information_schema 查询；不加载 SQL 文件、不执行 ALTER/INSERT/UPDATE/DELETE。
    $result = & $mysqlCommand.Source --protocol=TCP --connect-timeout=5 `
        --host=$DbHost --port=$DbPort --user=$DbUser --database=$DbName `
        --batch --skip-column-names --execute=$query 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "MariaDB 只读核验失败（退出码 $exitCode）：$($result -join [Environment]::NewLine)"
    }
    $result | ForEach-Object { $_ }
    if (($result -join "`n") -match 'MISSING') {
        throw '数据库缺少 IoT 运行字段或发现索引；请先评审并执行增量迁移脚本。'
    }
    Write-Output 'schema_check=OK'
}
finally {
    # 不让密码继续留在当前 PowerShell 进程环境中。
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldMysqlPassword, 'Process')
}
