# 148 字典读取与图片分类发布

2026-11-10，按用户要求发布 `52117b3`。构建采用独立源码工作树，基线为 `0e1c38b3324a61e3da6510f5d6acdf282921c38c`，仅更新系统服务、网关包、业务及隔离网关的字典精确路由。原工作区的 DATA-09 未提交实现未进入本次构建，三维服务和前端静态制品未替换。

| 对象 | SHA-256 |
| --- | --- |
| system.jar | `0aae9cbad7d4e8f2c0cec44cbccecf272eef272af316bd2974b64d57aff3df2e` |
| gateway.jar | `5351279b47f7a2c59541c38761a63eec734671a82bd7d5f79b9bfcdee436b6ff` |

当前系统使用 `wx_editor`、端口 `48081`；保留现有端口、数据库连接和各服务参数。四类数据在发布前已经存在，编码分别为 `DEVICE_ICON/BUILDING_ICON/HERB_ICON/WAREHOUSE_ICON`，排序为 `10/20/30/40`，均启用。本次备份字典两表后只读核对，未执行 SQL 写入，没有重跑初始化。

9447 原代理没有字典读取及图片分页转发。新增 `/api/system/dict/data/type/{dictType}`、`/admin-api/system/dict/data/type/{dictType}` 的精确只读入口，并补齐 `/admin-api/dim3d/icon/page` 的 GET/HEAD 转发，不开放其他系统或图片写入路径。最终规则见 [Nginx 配置](nginx.conf)。

本地命令为 `mvn.cmd -B -o -pl wx-module-system/wx-module-system-biz,wx-gateway -am -Dtest=DictDataTypeControllerTest,DictDataServiceImplTest,DictTypeServiceImplTest,DictionaryRouteTest,*LoginUser*Test* -Dsurefire.failIfNoSpecifiedTests=false package`，实际匹配 47 项 Java 用例，无失败；两个 Python 检查分别为 `test_dict_route.py` 2 项及 `test_icon_category_seed.py` 6 项，均通过。未运行全后端或 Unity 构建。

临时系统 `48184` 和网关 `12533` 通过 [22 项隔离验收](candidate-verification.json) 后切换，候选容器随后移除。正式 `12522/12525` 各 22 项检查通过，包括三类账号登录、原权限及资源读取、两种字典路径的四类顺序/编码/字符串编号、未知类型空数组、匿名/伪造内部身份/篡改租户拒绝。结果见 [发布记录](deployment.json)，服务恢复健康。

浏览器按 ego-browser 本机安装版 v2.2.0 指南，复用任务空间 3，实际登录并进入图片管理，四类展示正确；点击设备后提交真实 `DEVICE_ICON`。字典、列表、全部及四类数量、设备筛选的 8 次请求均为 HTTP 200，见 [浏览器请求](browser-requests.json)。验收账号当前可见总数为 0；不将此结果推断为全库或其他账号无图片。未测试图片上传、修改、删除，也未宣称整套图片管理功能验收完成。

截图因窗口最小化失败，尝试恢复窗口时返回 `No web contents in the target`，因此没有截图文件；按技能要求在错误处停止浏览器操作，没有创建其他任务空间或绕过接管。安装版提示有可用更新，本次未升级浏览器。

服务器发布目录 `/opt/wx/editor-test148/releases/dictionary01-20261110` 保存候选、执行器、最终配置和私有状态；备份目录 `/opt/wx/editor-test148/backups/dictionary01-20261110` 保存旧包、旧配置及字典两表快照。三个原容器以 `-before-dictionary01` 后缀停止保留。回退执行 `python3 /opt/wx/editor-test148/releases/dictionary01-20261110/deploy.py rollback`，恢复本批文件及原容器并重载代理；不调用历史旧系统总回退，不执行数据库恢复。私有状态、账号和数据库快照不进入仓库。
