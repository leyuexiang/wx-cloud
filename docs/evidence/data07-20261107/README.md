# DATA-07 同书签分片读取首批实现

日期：2026-11-08。当前状态：DATA-07-03 已完成，DATA-07-04 已完成，DATA-07-05 的 WebGPU 运行入口已联验，区块卸载、恢复和保存保全仍待执行。

- 新增 `GET /admin-api/dim3d/scene/chunks/{id}`，按 `revisionNo` 固定修订读取，可选 `chunkId`、`pageNum`、`pageSize` 和 `includeRelations`。
- 返回 `completeBaseline`、`indexVersion`、`sourceHash`、分页字段、区块成员和只读父级关系。读取仍从同一修订四包重建派生索引，未新增数据库表。
- 分页范围限制为页码1～10000、每页1～500，响应序列化后超过2 MiB返回业务错误。分片读取不取得编辑锁，也不改变 `saveAll` 的完整四包语义。
- `Dim3dSceneServiceTest` 18 项和 `SceneRevisionDerivedIndexTest` 3 项通过，共21项。
- `mvn -o -Ptest148 -pl wx-module-3d -am "-Dmaven.test.skip=true" package` 通过。
- 候选包 `wx-module-3d/target/wx-module-3d.jar` SHA-256 为 `daa7a19ca1d098f91dd05c39ba58addbde21975a411c4c2490629bf08cec59c4`。
- 在 148 独立端口和独立数据库中完成真实接口联验。未登录返回401，合法保存后固定修订2读取到区块和父级关系，修订1返回空区块基线，页大小501返回400，未知区块返回404，租户2读取租户1场景返回404。
- 联验结束后已停止并删除隔离容器、数据库、账号和远端临时目录。

Web 已新增 `getDim3dSceneChunks`、`readDim3dSceneChunkPage`、只读会话状态和 `projectSceneForUnityLoadedInstances` 运行投影。会话固定场景修订，记录已加载区块、实例和未加载集合，并提供状态订阅。分片投影自动补齐父级链，不改写完整 `SceneDefinition`。适配器4项和 Unity 投影14项测试通过，`pnpm type-check` 通过，最新 Web 提交为 `204ac9e`。场景编辑页继续以完整 `SceneStore` 作为编辑主数据源，分片会话只允许显式只读调用，不自动覆盖页面对象或进入 `saveAll`。共享 148 未部署，数据库未迁移。
- 官方 Unity CLI 已恢复连接项目 `6000.6.7f1` 编辑器，`unity status` 返回 `ready`，`scene list_open` 确认当前活动场景为既有 `HotfixSandbox`。未修改该场景。真实 WebGPU 分片投影、卸载、恢复和保存保全仍待专用测试场景或运行入口执行。
- 使用官方 Unity CLI 创建隔离 `Data07ChunkValidation` 场景，验证父节点 `Data07Root`、已加载子节点 `Data07LoadedChild` 和未加载兄弟节点 `Data07UnloadedSibling` 的层级关系。临时禁用未加载节点后恢复并保存，最终切回 `HotfixSandbox`，隔离场景及元文件已清理，原场景保持干净。

## 2026-11-08 WebGPU 入口联验

- 本地受控网关改用 Unity 6000.6.7f1 对应的 WebGL 资源发布目录，固定发布描述哈希为 `sha256:75df5ec2d595e06b9778889d608f993e1bab9b049a88c953c5277ba75f1031cb`。
- 浏览器通过运营端测试身份创建临时普通场景 `DATA07-WEBGPU-20261108-R2`，选择“Unity WebGPU”后进入独立编辑页，页面显示“Unity 场景已就绪”。资源面板、场景画布和保存/发布入口均已加载。
- 之前的 `startup-failed-retryable` 已定位为网关混用 Windows 资源发布清单导致的平台和 Unity 版本不匹配。Windows 清单为 `StandaloneWindows64/2021.3.33f1`，不能作为 WebGPU 资源清单。
- 临时场景已删除，服务器列表恢复为0条记录，临时发布目录、启动描述和凭据文件已清理。该联验只证明运行入口和启动元数据链路，不替代区块卸载、恢复和保存保全验收。
- Web 端已补充 `SceneRuntime.applyLoadedInstanceProjection` 和分片会话串行绑定器。绑定器只传递已加载实例编号，旧 Runtime 无投影能力时保持空操作，完整 `SceneStore`、对象缓存和 `saveAll` 边界不变。对应测试共29项通过，`pnpm type-check` 通过。
- 编辑页 `SceneCanvas` 已按锁定基准修订建立会话，并暴露 `loadSceneChunk`、`unloadSceneChunk`。会话只在显式调用时读取区块，区块卸载通过会话状态驱动 Unity 表现更新，不触碰业务保存数据。
