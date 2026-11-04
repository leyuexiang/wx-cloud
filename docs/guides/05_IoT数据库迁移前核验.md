# IoT 数据库迁移前核验

核对日期：2026-10-22。用途：在真实 MariaDB 凭据和备份窗口已落实后，只读检查 IoT 运行字段及发现索引，再决定是否执行迁移。当前 148 的 `3306` 端口可达，但 `iot_app` 凭据尚未提供，本指南尚未在真实数据库执行。

## 1. 核验内容

脚本 [verify-mariadb-schema.ps1](../../tools/iot/verify-mariadb-schema.ps1) 只查询 `information_schema`，检查：

- 数据库版本；
- `wx_iot_connector` 表是否存在；
- `runtime_owner` 字段；
- `runtime_lease_until` 字段；
- `idx_iot_connector_discovery` 索引。

脚本不加载 SQL 文件，不执行 `ALTER`、`INSERT`、`UPDATE`、`DELETE`、`TRUNCATE`、`DROP` 或数据库清理。密码通过当前 PowerShell 进程的 `MYSQL_PWD` 传给客户端，脚本结束时恢复原值；不会把密码拼接到命令行或输出。

## 2. 执行方式

在受控的 Windows 运维终端中，通过环境变量注入凭据：

```powershell
$env:IOT_DB_HOST = '192.168.10.148'
$env:IOT_DB_PORT = '3306'
$env:IOT_DB_NAME = 'wx_iot'
$env:IOT_DB_USERNAME = 'iot_app'
$env:IOT_DB_PASSWORD = '<由部署方注入，不要提交或粘贴到文档>'
& .\tools\iot\verify-mariadb-schema.ps1
Remove-Item Env:IOT_DB_HOST, Env:IOT_DB_PORT, Env:IOT_DB_NAME, Env:IOT_DB_USERNAME, Env:IOT_DB_PASSWORD
```

也可以只设置 `IOT_DB_PASSWORD`，脚本默认使用 `192.168.10.148:3306`、库 `wx_iot` 和账号 `iot_app`。如果输出 `schema_check=OK`，表示目标库已有本轮代码要求的字段和索引；这不表示数据库备份、租户数据、权限、连接池或应用启动已经验收。

## 3. 缺字段时的处理

1. 先停止会写入 `wx_iot_connector` 的应用或连接器管理操作，确认备份和回滚窗口。
2. 核对原表已完成表名前缀迁移，确认目标库为 IoT 实际使用库。
3. 由数据库管理员执行 `wx-module-iot/db/iot-connector-runtime-migration.sql` 一次。
4. 再次执行本只读脚本，并人工检查变更记录；不要将脚本输出为 `OK` 当作迁移已执行证据。
5. 迁移通过后再启动应用，执行连接器查询、动态租约和旧版本兼容验证。

增量脚本不是幂等迁移框架，重复执行可能因字段或索引已存在而失败；本核验脚本就是为了在执行前区分“已具备”和“需要迁移”。当前没有真实数据库凭据，因此本步骤仍待部署方输入和授权。

## 4. 当前环境结论

2026-10-22 从开发机探测到 `192.168.10.148:3306` 可达；无密码使用 `iot_app` 收到 `ERROR 1045`，没有执行任何查询或写操作。真实核验需要受限账号、目标库确认、备份窗口和迁移授权。相关端口证据见[148 依赖端口核验](../records/06_20261022_148依赖端口核验记录.md)。
