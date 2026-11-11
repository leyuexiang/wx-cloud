# 前端请求与后端实现差异清单

任务：M0-01；日期：2026-10-31；状态：源码核查完成，M0-02 策略已冻结，待 M0-03 冻结正式接口合同。本文件不声明任何新增接口已实现或部署。

## 1. 记法与核查方法

- `P`：业务平台前缀，运营 `/api/operation`、租户 `/api/tenant/web`；以下路径不含服务器根地址。
- `A`：`Authorization: Bearer ...` 和 `ClientId`。实际独立/嵌入身份由请求拦截器注入；它们是请求凭证输入，不是可由浏览器声明的权限结论。
- `E`：业务封套 `{code:200,msg,data}`；根级分页与 `data` 分页必须根据对应消费者确认，不能把 `list`、`rows`、`records` 无差别替换。
- `适配`：可复用领域服务，但路径/字段/身份仍需实现；`新增`：未发现满足该合同的控制器及服务；`外部`：依赖另一个真实服务或产物。
- `活跃` 指有页面/业务服务调用引用；`间接` 指注入/默认适配器引用；`仅声明` 指没有发现非测试消费者。静态引用不等于真实环境验收。

逐项检查前端适配器、请求改写及非测试调用引用，再对照后端控制器、服务和部署网关。后端匹配范围为当前系统、基础设施、三维服务；旧 `project-engineering` 和被注释的 `SceneDefController` 不算新的场景实现。

## 2. 路径和身份的共性差异

1. 独立认证通过 `standalone.ts` 的原生请求固定调用 `/api/...`，不经过通用平台插段。业务适配器默认经过 `request.ts` → `platformApiPathResolver.ts`。
2. 字典、普通文件、模型转换及资源元信息默认显式 `skipPlatformPath:true`，保持公共 `/api` 路径；其中有些方法允许调用方覆盖选项，冻结前需审计是否存在覆盖。公共路径不代表匿名授权。
3. 当前部署网关仅配置 `/admin-api/system`、`/admin-api/infra`、`/admin-api/dim3d` 等入口，没有前端所需的上述 `/api` 路由。不能只改前端服务器地址。
4. 现后端以既有令牌和租户上下文工作；前端主要提交 A，不保证提交 `tenant-id`。新接口如何安全恢复真实租户，留给 M0-02，禁止从平台路径猜测。
5. 外部编号须按字符串保真。系统资料 `id/nickname` 与前端 `userId/nickName`、后端 `accessToken/expiresTime` 与前端 `access_token/expire_in` 均需显式投影。

## 3. 认证与基础配置（M1）

| 编号 | 前端方法及路径 | 调用证据 | 输入/响应要点 | 后端基础和明确差异 | 分类/责任 |
| --- | --- | --- | --- | --- | --- |
| AUTH-01 | POST `/api/auth/operation/login` | F01；`views/StandaloneLogin.vue` 活跃 | `clientId,accountType=2,phonenumber,password,grantType=password` → E 的 `access_token,client_id,expire_in` | B01 `/admin-api/system/auth/login` 接收现账号登录模型；没有此双端合同；响应为 `accessToken,refreshToken,expiresTime` | 适配/系统 |
| AUTH-02 | POST `/api/auth/tenant/login` | F01；同一页面切平台 | 同上；租户身份不能由通用应用编号唯一推断 | 同 B01；需解决手机号多租户歧义、应用与平台绑定，不能仅设置不同 URL | 适配/系统 |
| AUTH-03 | GET `/api/system/user/getInfo` | F01 的 `loadProfile`，登录/恢复链路 | A → `data.user.userId,nickName,roles[],permissions[]` | B01 `/get-permission-info` 已有角色和权限；用户字段为 `id,nickname`，另有 `menus` | 适配/系统 |
| AUTH-04 | GET `/api/tenant/profile/getInfo` | F01 租户资料链路 | A；接受租户 `userName` 投影为 `nickName` | 复用资料/权限服务，但必须校验真实租户及平台 | 适配/系统 |
| AUTH-05 | POST `/api/auth/logout` | F01；`utils/auth.ts` 活跃 | A、正文 `{}` → `data:null` | B01 `/logout` 返回布尔值；新接口应明确当前应用/会话的撤销语义 | 适配/系统 |
| AUTH-06 | POST `/api/auth/refresh` | F01；`request.ts` 配置开关控制 | `clientId,refreshToken,platformType`；秒单位轮换字段 | 既有 `/refresh-token?refreshToken=...` 和令牌服务可复用部分逻辑，但轮换链/摘要及重放撤销没有证据满足新合同；默认开关关闭 | 适配并补规则/系统，启用另验 |
| BASE-01 | GET `/api/system/dict/data/type/{dictType}` | 当前 `jtyf-web-three` 字典适配器；管理页/场景/资源面板活跃，跳过平台改写 | A → `dictCode,dictLabel,dictValue,dictType` 数组 | M1-02 补充精确网关改写及系统读取适配，复用 B02 字典服务；主键字符串化，过滤启用项；实际分类值仍需业务配置 | 适配/系统；验证及部署状态见 03 |
| BASE-02 | GET `P/tenant/dashboard/list` | F04；`views/SceneManage/index.vue` 授权选择 | A；筛选、分页 → `rows,total` | B03 `/admin-api/system/tenant/page` 可复用部分租户查询，字段/筛选不同；普通租户可见范围必须先冻结 | 适配/系统 |

BASE-01 涉及模型、图片、场景、访问模式、行业模板和展厅模板六类字典。业务值尚未冻结，不补造完整分类库。认证成功不等于允许全局资源维护。

## 4. 场景管理、锁与保存（M2）

统一来源 F02、F11；后端 B08 是旧工程接口参考，未发现下列新场景合同实现。所有写入须使用可信会话，后续正式权限码由 M0-03 冻结。

| 编号 | 方法及路径 | 页面/业务消费者 | 请求及响应差异 | 分类/责任 |
| --- | --- | --- | --- | --- |
| SCN-01 | GET `P/dim3d/scene/page` | `views/SceneManage/index.vue` | 名称/分类/类型/用途/分页等 → 根级或 `data` 包装 `rows,total`，不接受仅 `data.list` | 新增/三维 |
| SCN-02 | GET `P/dim3d/scene/info/{id}` | 编辑器、F11、场景导入导出 | 字符串 id → 基本视图、完整当前四包修订及发布引用；旧整数工程详情不可直接代替 | 新增/三维 |
| SCN-03 | POST `P/dim3d/scene/add` | `views/SceneManage/AddDialog.vue` | `name,sceneType` 等 → 新场景基本视图；不能返回无编号空成功 | 新增/三维 |
| SCN-04 | PUT `P/dim3d/scene/edit/{id}` | 同上 | 场景表单；归属由身份决定 → E | 新增/三维 |
| SCN-05 | DELETE `P/dim3d/scene/remove/{id}` | 场景列表 | 删除权限/引用/锁边界须冻结 → E | 新增/三维 |
| SCN-06 | POST `P/dim3d/scene/copy/{id}` | 场景列表 | 无正文 → 新身份场景；不得复用旧锁/发布票据 | 新增/三维 |
| LOCK-01 | POST `P/dim3d/scene/lock/{id}?clientKey=...` | `views/sceneEdit/index.vue` | 页签编号 → `lockToken,baseRevisionNo,expireTime,locked,lockedMap`；`locked=false` 且有令牌才获得编辑权 | 新增/三维 |
| LOCK-02 | POST `P/dim3d/scene/heartbeat/{id}` | 编辑页心跳 | 正文 `{lockToken}`，不传 clientKey；合同租约 20 分钟 | 新增/三维 |
| LOCK-03 | DELETE `P/dim3d/scene/unlock/{id}?clientKey=...` | 编辑页正常退出及卸载 keepalive | A + 页签身份；不能只凭 id 清除他人锁 | 新增/三维 |
| SAVE-01 | PUT `P/dim3d/scene/saveAll` | F10 → `editorDim3dSceneSaveService.ts` 活跃 | `sceneId,lockToken,requestId,baseRevisionNo,schemaVersion` 及四个 JSON 字符串 → 真实 `revisionId,revisionNo,lockVersion,savedTime,conflict` | 新增/三维 |

四包与索引、书签及幂等结果须同事务；旧工程“保存部分对象”接口不符合整体保存要求。前端类型中部分字段可选但正式保存会生成，服务端必填和错误码仍须 M0-03 冻结，不能在差异清单中提前认定最终政策。

## 5. 目录与普通文件（M3，前置准备可提前）

| 编号 | 方法及路径 | 来源及消费者 | 当前后端基础 | 明确差异/处理 | 分类/责任 |
| --- | --- | --- | --- | --- | --- |
| CAT-01 | GET `P/dim3d/model/page`、`/model/info/{id}` | F05；模型管理、编辑资源面板 | B04 同名后缀 `/admin-api/dim3d` | 后端 `data.list,total` 对照前端 `rows/records,total`；字段/编号/归属投影；不能原样代理 | 适配/三维 |
| CAT-02 | POST `/model/add`；PUT `/model/edit/{id}`、`/model/{status}/{id}`；DELETE `/model/remove/{id}`，均加 `P/dim3d` | F05；模型管理/弹窗 | B04 有新增、编辑、状态及删除 | 保留 `dim3d:model:*` 权限；前端 SYSTEM/MINE 与可信资源范围不能直接等同；新增当前返回 Long 而消费者定义 E/空数据，按样例冻结 | 适配/三维 |
| CAT-03 | GET `/icon/page`、`/icon/info/{id}`；POST `/icon/add`；PUT `/icon/edit/{id}`、`/icon/{status}/{id}`；DELETE `/icon/remove/{id}`，均加 `P/dim3d` | F06；图片管理、图像选择及部件素材 | B04 已有对应后缀 | 同样需分页、字段、状态和 `dim3d:icon:*` 权限适配；普通文件稳定身份不能假定等于包资源编号 | 适配/三维 |
| CAT-04 | GET `/template/page`、`/template/info/{id}`；PUT `/template/edit/{id}`、`/template/{status}/{id}`；DELETE `/template/remove/{id}`，均加 `P/dim3d` | F07；模板管理和编辑器资源面板 | B04 已有对应后缀，另有 `/template/add` | 前端当前文件无独立新增模板导出，不因后端有接口就列为首批直接调用；模板新增见 TMP-01 | 适配/三维 |
| FILE-01 | POST `/api/resource/oss/upload` → `/admin-api/infra/file/resource/oss/upload` | F08；图片、模型封面、模板封面 | B05 `/admin-api/infra/file/upload` 返回地址字符串；本地新增独立候选控制器/Service，有归属 schema 迁移未现场验证 | 只接受单字段 `file`；返回 `ossId,url,originalName,fileSuffix`，`ossId` 为 `wx_infra_file.id` 且按字符串传输；不得把可选兼容字段 `fileName,ext1,service` 猜成必需身份 | 适配/基础设施；合同见 [IMG-01](image-upload-contract.md) |
| FILE-02 | GET `/api/resource/oss/listByIds/{ids}` → `/admin-api/infra/file/resource/oss/listByIds/{ids}` | F08；`sceneAssetUrlResolver.ts` 和图片部件 | 本地已有显式范围 Mapper 与签名候选；数据库字段/签名 Host 及真实读取未验 | 首期只接受一个正十进制本地文件编号；legacy 不可见，当前仅 private uploader 可读；每次按编号签发 S3 GET 短时地址；批量语法不在首期 | 新增读取适配/基础设施 |
| RES-01 | POST `/api/dim3d/resource/uploadConvert` | F09；模型新增弹窗 | B04 有 `/admin-api/dim3d/resource/uploadConvert`；B07 代理转换 | 前端 file ZIP → `id,resourceName,resourceType,assetName,resourceRelativePath`；代理仍依赖参考服务，不能声称本地转换齐备 | 适配+外部/三维 |
| RES-02 | GET `/api/dim3d/resource/info/{id}` | F09；模型管理、资源面板、场景资源解析 | 有 `Dim3dConvertedResource` 和映射器；未发现满足该精确详情合同的控制器 | 读取 modelFileId 对应元信息并鉴权；不能用 ART 的 `/resources/{id}/current` 返回另一种结构 | 新增读取适配/三维 |
| RES-03 | 静态 `{VITE_DIM3D_AMM_SERVER_URL}/amm/{resourceRelativePath}` | F09 `buildDim3dResourceUrl`，活跃资源解析 | 当前新私有桶及 ART 签名读取不是旧 `/amm/` 静态合同 | 审计资源格式/产物和访问授权；不能公开整个私有桶，也不把 GLB 路径当 Unity 包 | 外部/存储与三维 |

B04 三类目录的删除/状态服务存在不等于已满足运营/租户双端权限与新场景引用规则。以上需要适配的项目，没有一项被认定为“改服务地址即可直接复用的前端接口”。

## 6. 模板、发布及授权（M3）

| 编号 | 方法及路径 | 来源和调用层级 | 请求/响应要点 | 后端差异/责任 |
| --- | --- | --- | --- | --- |
| TMP-01 | POST `P/dim3d/scene/saveAsTemplateFromSnapshot` | F02；编辑器模板服务注入，非简单目录新增 | 当前内存四包、`templateType,category,saveScope,schemaVersion` → 新模板场景 | 新增/三维；目录 add 不能替代域裁剪和快照 |
| PUB-01 | POST `P/dim3d/scene/publish/{id}` | F02；场景列表和编辑器活跃 | `accessMode,accessPassword?,changeSummary?` → `publishedPublicationId,revisionNo,alreadyPublished` 等 | 新增/三维；旧 set-publish 布尔不等于不可变快照 |
| PUB-02 | PUT `P/dim3d/scene/cancel/{id}` | F02；场景列表活跃 | 无正文；撤销对后续票据/读取生效 | 新增/三维 |
| PUB-03 | POST `P/dim3d/scene/grant` | F02；场景列表活跃 | `{sceneId,grantTenantId}` → E | 新增/三维；跨租户授权是明确关系，不改变资源所有者 |
| PUB-04 | POST `P/dim3d/scene/revoke`；GET `P/dim3d/scene/authorizedTenants/{id}` | F02 已声明，本次未确认非测试页面调用；合同保留 | 授权关系撤回/记录，状态和历史语义待冻结 | 新增/三维；不把仅声明标为活跃使用 |

## 7. 发布读取的两套路径：需要显式冻结

| 编号 | 方法及路径 | 源码调用层级 | 鉴权/结构 | 结论 |
| --- | --- | --- | --- | --- |
| READ-01 | GET `P/dim3d/scene/published/{publicationId}/sceneBasic`、`/threeD`、`/twoD` | F10；`sceneRuntimeFragmentLoader.ts` 中默认 API，支持注入；未确认所有页面默认分支实际走到 | A；单包字符串、固定发布编号 | 新增能力候选，默认/兼容读取不能自动当作正式预览唯一路径 |
| READ-02 | POST `P/dim3d/scene/published/{publicationId}/dependencies:batch-get` | 同上默认 API | 正文原始实例字符串数组，非对象包裹 | 与 READ-01 同组，是否保留在 M0-03 明确 |
| PREVIEW-01 | GET `P/dim3d/scenePreview/resolve?sceneId=...&password=...` | F10 → `scenePreviewAccess.ts` → 预览页活跃 | 查询字段 `sceneId,password?`；响应访问判定和发布/票据上下文；不得记录密码/票据查询串 | 新增/三维；公开匿名探测默认运营路径，受限身份走真实平台 |
| PREVIEW-02 | GET `P/dim3d/scenePreview/published/{publicationId}/sceneBasic`、`/threeD`、`/twoD` | 同上专用预览 API 注入 F10 加载器 | 查询带 ticket；仍经平台改写；返回对应原文包 | 新增/三维；不能移除 ticket 或用草稿代替快照 |
| PREVIEW-03 | POST `P/dim3d/scenePreview/published/{publicationId}/dependenciesByinstances` | 同上活跃预览 API | 查询 ticket；正文原始字符串数组；各依赖元素为单条 JSON 原文字符串 | 新增/三维；与 READ-02 路径及授权不同，不盲目合并 |
| PREVIEW-04 | POST `P/dim3d/scenePreview/published/{publicationId}/vegetation/resources` | F10 专用预览适配器，按场景是否有植被调用 | 固定发布和规则/来源上下文，ticket 及明确版本结果 | 后续植被专项；当前三维核心部署未提供此发布读取合同 |

当前后端未发现两组发布读取的对应实现。正式预览页通过 `createScenePreviewFragmentLoaderApi(session)` 注入票据 API，而底层加载器仍保留登录态默认 API。合同应明确二者用途及退役策略，不能因为旧交接只列票据组就认定另一组完全不存在，也不能把默认 API 的存在当作所有页面都在请求它。

## 8. 不扩展当前首批范围

- `servers/generated/` 的旧工程和文件适配器属于兼容来源，不能按生成文件数量重建旧平台。
- `scene.ts` 中模板另存是服务注入调用；撤回/授权列表目前仅确认声明，仍按 M3 合同候选登记。
- 数据中间件、媒体、GIS、植被、Unity 构建、信令与启动器分别列为外部或后续专项；不在 M0-01 执行真实请求、改地址或申请会话。
- 云会话申请/续租仍为跨端设计草案，不能用普通业务封套替代 Unity 结构化远程调用（JSON-RPC）的协议。
- ART 已有受控资源合同继续独立维护；前端旧转换目录消费者和新的不可变包消费者不能按相同数字主键直接拼接。

## 9. 交给 M0-03 的决策项

1. 登录手机号/账号和租户如何唯一识别，应用与平台的可信映射及测试账号策略。
2. 统一用户资料、令牌字段、有效期、编号、分页和失败语义；刷新是否纳入首次上线，启用前的轮换/重放要求。
3. 六类字典业务值、租户可见范围、平台/租户/普通用户的动作权限。
4. 保存必填字段、锁/租约与幂等顺序、冲突业务码及初次无修订场景行为。
5. 两套发布读取路径的具体用途、票据与登录身份组合、批量依赖上限及取消发布的失效规则。
6. 普通文件编号映射、旧转换元信息及 `/amm/` 到当前私有资源机制的过渡，禁止绕过资源授权。

这些是待冻结问题，不在本任务中代替用户作业务决策。后续进入 M0-03；M1 业务开发等待 M0 必要合同和设计完成。

## 10. 源码定位

前端路径相对于独立仓库；后端路径相对于本仓库。链接用于静态复核，不包含任何凭据。

| 标识 | 源码 |
| --- | --- |
| F01 | [独立认证](../../../wx-vue-unity/src/auth/standalone.ts) |
| F02 | [场景适配器](../../../wx-vue-unity/src/servers/manual/dim3d/api/scene.ts) |
| F03 | [字典](../../../wx-vue-unity/src/servers/manual/dim3d/api/dictionary.ts) |
| F04 | [租户列表](../../../wx-vue-unity/src/servers/manual/dim3d/api/tenant.ts) |
| F05 | [模型目录](../../../wx-vue-unity/src/servers/manual/dim3d/api/model.ts) |
| F06 | [图片目录](../../../wx-vue-unity/src/servers/manual/dim3d/api/icon.ts) |
| F07 | [模板目录](../../../wx-vue-unity/src/servers/manual/dim3d/api/template.ts) |
| F08 | [普通文件](../../../wx-vue-unity/src/servers/manual/dim3d/api/oss.ts) |
| F09 | [转换上传](../../../wx-vue-unity/src/servers/manual/dim3d/api/upload.ts)、[资源详情/路径](../../../wx-vue-unity/src/servers/manual/dim3d/api/resource.ts) |
| F10 | [场景保存及运行接口](../../../wx-vue-unity/src/servers/manual/dim3d/api/sceneRuntime.ts) |
| F11 | [场景仓储](../../../wx-vue-unity/src/integrations/scene/dim3dSceneRepository.ts) |
| F12 | [请求拦截器](../../../wx-vue-unity/src/request.ts)、[平台路径改写](../../../wx-vue-unity/src/auth/platformApiPathResolver.ts)、[票据预览 API 注入](../../../wx-vue-unity/src/integrations/scene/scenePreviewAccess.ts) |
| B01 | [认证控制器](../../wx-module-system/wx-module-system-biz/src/main/java/cn/iocoder/wx/module/system/controller/admin/auth/AuthController.java) |
| B02 | [字典控制器](../../wx-module-system/wx-module-system-biz/src/main/java/cn/iocoder/wx/module/system/controller/admin/dict/DictDataController.java) |
| B03 | [租户控制器](../../wx-module-system/wx-module-system-biz/src/main/java/cn/iocoder/wx/module/system/controller/admin/tenant/TenantController.java) |
| B04 | [资源目录控制器](../../wx-module-3d/src/main/java/cn/iocoder/wx/module/dimensions/controller/admin/Dim3dResourceController.java) |
| B05 | [文件控制器](../../wx-module-infra/wx-module-infra-biz/src/main/java/cn/iocoder/wx/module/infra/controller/admin/file/FileController.java) |
| B06 | [ART 控制器](../../wx-module-3d/src/main/java/cn/iocoder/wx/module/dimensions/controller/admin/ArtResourceController.java) |
| B07 | [目录及转换代理服务](../../wx-module-3d/src/main/java/cn/iocoder/wx/module/dimensions/service/impl/Dim3dResourceCatalogServiceImpl.java) |
| B08 | [旧工程控制器](../../wx-module-3d/src/main/java/cn/iocoder/wx/module/dimensions/controller/admin/EngineeringController.java) |
| B09 | [本次部署网关路由](../../deploy/test148/config/gateway.yaml) |
