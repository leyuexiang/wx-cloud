# 148 接口文档恢复证据

日期：2026-11-10。任务：OPS-02。结果：已部署配置，真实接口及浏览器检查通过，用户使用确认另计。

## 修改与部署

- 入口：[接口文档](http://192.168.10.148:12522/doc.html)。开启系统、基础设施、三维三个模块的手动聚合；读取 `/v3/api-docs/swagger-config`，不使用旧 `/swagger-resources`。
- 新增只读精确文档路由，去掉模块前缀后进入服务原有文档规则，保留内部身份头清理及业务鉴权。配置生成器与现场增量发布共用 `deploy/test148/openapi_support.py`。
- 发布目录 `/opt/wx/editor-test148/releases/openapi-20261110`，备份 `/opt/wx/editor-test148/backups/openapi-20261110T083922Z`；仅重建业务网关，未改数据库及任何业务程序包。
- 本批记录：[部署与摘要](deployment.json)、[服务范围与健康](service-scope.json)。回退命令与边界见[部署说明](../../20_部署运维/01_148服务器部署与回退.md#2026-11-10-接口文档恢复)。

## 验证

| 检查 | 实际结果 | 证据 |
| --- | --- | --- |
| 本地配置回归 | 新增文档 2 项、原字典路由 2 项、部署兼容层 8 项，共 12 项通过 | 下方命令 |
| 语法与差异 | 本批 Python 文件编译检查及差异空白检查通过 | 下方命令 |
| 回环隔离网关 `12534` | 19 项通过；文档页面、真实脚本、聚合清单、3 个规范及业务前缀、8 项匿名/伪造身份拒绝 | [隔离结果](isolation.json) |
| 业务网关 `12522` | 同一组 19 项通过；系统/基础设施/三维规范分别为 231/82/216 条路径 | [现场结果](postflight.json) |
| ego-browser 0.6.2.8 | 三模块切换、个人密码接口搜索、参数详情、相对调试路径检查通过 | [浏览器记录](browser.json)、[密码接口](password-api.png)、[基础设施](infra.png)、[三维](dimensions.png) |
| 服务范围 | `12522` 与 `12525` 均为 UP；其他服务启动时间保留，四个程序包摘要与发布前一致 | [范围核对](service-scope.json) |

本地执行：

```powershell
python -m unittest discover -s deploy/test148 -p test_openapi_routes.py -v
python -m unittest discover -s deploy/test148 -p test_dict_route.py -v
python -m unittest discover -s deploy/test148 -p test_server_support.py -v
python -m py_compile deploy/test148/openapi_support.py deploy/test148/deploy-openapi.py deploy/test148/verify-openapi.py
git diff --check -- deploy/test148 docs/03_近期执行任务.md docs/20_部署运维/01_148服务器部署与回退.md
```

部署先执行 `deploy-openapi.py isolate`，通过后执行 `deploy-openapi.py apply`，两者均使用宿主 `/usr/bin/python3`。初始隔离遇到候选权限与宿主旧版 YAML 库兼容问题，已在发布脚本修复；验证器按实际 Knife4j 4.5 聚合端点校正后，隔离及现场最终结果如上。

## 验证边界

未提交密码修改请求，未测试携带真实令牌的业务写操作。首页 host 为服务原规范地址，调试框已检查为网关相对路径；业务调试仍需真实登录令牌和租户编号。未在本批恢复 `12525` 的文档聚合，也未改变 DATA-09 的交付状态。浏览器提示有可用升级，本次未升级。
