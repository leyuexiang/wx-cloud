# DATA-06 同书签派生索引首批验证

日期：2026-11-07。当前状态：进行中。

本批完成保存门禁和内存派生索引模型。索引由完整三维四包构建，带租户、场景、修订号、索引版本和四包来源摘要。索引只服务当前修订，不接受客户端独立写入，也没有新增数据库表。

验证结果：

- `SceneRevisionDerivedIndexTest` 3 项通过。
- `Dim3dSceneServiceTest` 17 项通过，包含未知区块成员拒绝后修订数保持不变。
- `mvn -pl wx-module-3d -am "-Dtest=SceneRevisionDerivedIndexTest,Dim3dSceneServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` 通过，共20项。
- `mvn -o -Ptest148 -pl wx-module-3d -am "-Dmaven.test.skip=true" package` 通过。
- 候选包 `wx-module-3d/target/wx-module-3d.jar` SHA-256 为 `271064ff9bb76b19af216d0772f3d23e104bd213a1957e4410bba07bd992b3b`，大小 194,656,522 字节。

当前未部署 148，未执行数据库迁移。下一项是在隔离服务中使用该候选包执行真实 MySQL 保存、失败回滚和同号重试，再决定是否增加可重建物理索引表。
