# DATA-07 同书签分片读取首批实现

日期：2026-11-07。当前状态：DATA-07-02 已完成，DATA-07-03 真实接口联验待执行。

- 新增 `GET /admin-api/dim3d/scene/chunks/{id}`，按 `revisionNo` 固定修订读取，可选 `chunkId`、`pageNum`、`pageSize` 和 `includeRelations`。
- 返回 `completeBaseline`、`indexVersion`、`sourceHash`、分页字段、区块成员和只读父级关系。读取仍从同一修订四包重建派生索引，未新增数据库表。
- 分页范围限制为页码1～10000、每页1～500，响应序列化后超过2 MiB返回业务错误。分片读取不取得编辑锁，也不改变 `saveAll` 的完整四包语义。
- `Dim3dSceneServiceTest` 18 项和 `SceneRevisionDerivedIndexTest` 3 项通过，共21项。
- `mvn -o -Ptest148 -pl wx-module-3d -am "-Dmaven.test.skip=true" package` 通过。
- 候选包 `wx-module-3d/target/wx-module-3d.jar` SHA-256 为 `daa7a19ca1d098f91dd05c39ba58addbde21975a411c4c2490629bf08cec59c4`。

下一步在独立候选包环境验证租户越权、固定修订、分页边界、超大小响应和旧档读取，再接 Web 只读适配器。当前未部署共享 148，未执行数据库迁移。
