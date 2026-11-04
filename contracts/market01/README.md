# MARKET-01普通模型清单子合同

2026-11-04。冻结范围仅为wx.model-resource.v1清单结构、路径、大小和摘要规则，用于独立服务端验证器；不表示导入API、迁移、发布适配或运行加载合同整体冻结。

普通模型清单的 resourceId 为稳定模型资源主键。它不标识某个物理文件或内容修订。旧普通模型清单字段不进入本项目兼容范围。

清单定义的权威来源是Unity工程Tools/ResourceMarket/contracts/model-manifest.schema.json；本目录model-examples.json是该合同生成的跨语言固定测试夹具，不独立维护另一套结构。

- 格式model.gltf.v1/model.glb.v1分别要求.gltf/.glb入口，平台本期WebGL；兼容标识为非空、无空白/控制字符的字符串，实际值由加载器后续冻结。
- 根字段固定schemaVersion/resourceId/contentHash/contentFormat/platform/appCompatibility/entry/files；entry固定kind=MODEL、assetName、modelPath，拒绝热更包字段。
- files为1～4096项，单文件最多2GiB，总量最多4GiB，大小及编号使用规范十进制字符串，编号不超过Long.MAX_VALUE。
- 文件路径复用ART安全相对路径规则，拒绝重复、目录尾斜杠、入口缺失和主清单自引用；主清单最多4MiB。
- 严格UTF-8与JSON解析，拒绝重复属性、BOM、尾随根和无效代理字符。文本长度沿用既有Java资源合同的UTF-16长度上限。
- contentHash排除自身，文件按ASCII路径排序，对象键排序，紧凑UTF-8无BOM；manifestSha256是最终文件原始字节摘要，保持两个摘要职责分离。

仅验证清单不能证明模型文件合法。外部URI/缓冲/贴图、GLB头及块长、真实字节摘要、租户和兼容性仍由后续独立闭包校验及受控发布负责。本轮验证器没有接入现有ART提交路由，不放宽原热更校验。

导入API和恢复规则继续见Unity工程Tools/ResourceMarket/contracts/README.md；未整体冻结前不开放上传入口、不建业务表。

2026-11-04数据与响应子项：新增deploy/test148/migrations/market01迁移，仅独立库演练19项通过；来源字段与导入表尚未用于业务库。响应结构在Unity工程Tools/ResourceMarket/contracts/model-import-status.schema.json，Java固定错误码和投影已实现。以ART成功回执确定COMMITTED，尚未实现导入公开API，整体合同继续按专项分阶段推进。

2026-11-04接收侧子集已冻结并本地实现：ModelImportController/Service提供create、query、source-target和submit，路由默认关闭。内部ArtResourceService.createModelImport复用操作身份与鉴权，并以服务端modelImport标记隔离旧热更提交入口。专用retry/cancel、工作线程和模型发布仍待，不修改已冻结热更请求结构。


## 原文件执行子合同

内部原文件执行器每轮最多检查16个到期候选，按操作行→任务行加锁；领取QUEUED或租约过期的VERIFYING_SOURCE，生成新attemptId，以数据库UTC时间设置120秒租约，每20秒续租。权限使用原操作可信操作者/租户再次检查并恢复线程租户上下文。

文件IO在事务外：先复用ART流式冻结至该尝试的独立私有前缀，冻结对象再次核对大小/摘要并检查ZIP魔数。已持久化不可变原ZIP恢复时只重验该对象，不能重新接受暂存覆盖。最终短事务重新检查原操作状态/截止/尝试/租约/取消/资源归属，才允许变为CONVERTING。丢租约、取消或迟到结果只能产生待保留孤立对象，不能推进任务或当前资源指针。

此子项不启动自动调度或转换，不视为已完成可靠转换/发布管线；转换期间续租、产物校验及最终发布仍须后续实现。

执行侧错误补充：2030001108 IMPORT_PERMISSION_REVOKED（不可重试）、2030001109 SOURCE_STORAGE_UNAVAILABLE（可重试），避免把权限撤销或原文件存储故障误报成转换器故障。检查权限时强制启用租户隔离，结束后恢复原租户与忽略标志。

## 转换执行子合同

内部执行器读取服务配置 `dim3d.model-import.converter-url`（完整 /convert URL，默认空），仅发送冻结的 ZIP（`file`）和目标格式（`format`）。禁止重定向；200 必须为 ZIP，旧的 200 JSON 转换失败按固定错误处理，响应正文不回传给前端。流式请求和响应有总时限，临时 ZIP 有大小上限。

转换中继续续租；过期的 CONVERTING 可在新尝试中复用同一冻结源，仅在未固定产物时重新转换。每个尝试使用独立产物路径；写入并校验大小和 SHA-256 后，由当前租约在短事务内固定产物描述并推进 VALIDATING。这里的 ZIP 仍未进行完整模型闭包校验，不可供平台消费。产物固定后，后续阶段只能读取该描述，不重新转换覆盖。

配置默认：总超时 960000 毫秒，产物 ZIP 上限 2147483648 字节，可分别用 `dim3d.model-import.converter-timeout-ms`、`dim3d.model-import.converter-max-archive-bytes` 收紧；未配置转换地址时不领取任务。尚未固定产物前的临时磁盘/对象存储故障使用可重试的 2030001110 ARTIFACT_STORAGE_UNAVAILABLE；已固定产物不可用仍为不可重试的 2030001107 ARTIFACT_UNAVAILABLE。

## 私有产物校验

校验只读取固定 ZIP，恢复时可替换租约尝试编号，ZIP 键及摘要保持不变。检查 ZIP 完整性、路径、文件规模、唯一模型入口，以及 glTF 2.0 的本地缓冲和图片引用、内嵌数据和 GLB 块长度。相对路径复用普通模型清单的安全路径集合，远程和跨目录回退引用不可进入发布；只接受入口及其声明引用的文件。模型结构检查服务于闭包安全，渲染兼容和实际加载由后续验收确定。

持有当前租约时将排序后的文件路径、字节数和摘要及唯一入口固定在 artifact_descriptor.validated，同时进入 PUBLISHING。发布器只能使用这份身份和对应固定 ZIP，仍须核验实际发布对象并通过原受控操作提交。

## 目录与发布前置读取

前端管理页使用 resourceId 保存导入身份，旧目录写入口保留原 modelFileId 语义。后续发布应在原操作下明确收集目录名称、分类、说明及封面等业务字段，使用可信操作者和资源所有者生成目录关联。CREATE 建立新的 resource_id 关联，REPLACE 使用该关联更新已有目录并沿用稳定资源主键。旧表 model_file_id 当前为 NOT NULL，受管记录的旧字段处理及目录元数据提交时点需在实现写事务前确定，禁止用相同数字替代两种身份。

文件签发读取已提交修订快照，根据快照 content_format 选择模型或热更清单验证。普通模型须核对格式、平台、加载兼容标识和摘要，签发范围限清单及成员，不包含源 ZIP 或转换 ZIP。旧快照缺少格式时仍走既有热更清单校验。

## 受控发布子合同

POST /model-imports/{operationId}/publish 接受 modelName（2 到 50 字符）、category（1 到 32 字符）及可选 modelDesc（最多 500 字符）。在 PUBLISHING 固定一次目录输入。相同输入可重放，变更已固定输入返回冲突。HTTP 202 表示已受理，HTTP 200 表示原操作已提交。本轮目录写入仅维护这三项，新建为免费并上线，替换保留既有目录身份、封面、计费和上下线。资源范围和所有者仅来自原操作。

受控工作线程要求配置与加载器验收一致的 appCompatibility，重新核验固定 ZIP，并按已校验文件计划保存模型文件和主清单。每次尝试使用独立冻结前缀。最终数据库事务按资源、操作、任务顺序加锁，校验租约、取消、权限及预期修订。写入资源修订、目录 resource_id、原提交回执和任务状态后同时提交。临时存储故障等待租约到期复用固定产物；固定产物损坏停止。受管目录的 model_file_id 为空，历史目录继续保留原文件编号。

内部轮询要求 dim3d.model-import.enabled 和 dim3d.model-import.worker-enabled 同时开启，两个开关默认关闭。部署配置还需提供已验证的 dim3d.model-import.app-compatibility 与内部转换地址。首次上线须在隔离环境验收实际文件存储、模型加载及目录读取。
