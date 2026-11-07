# DATA-06 同书签派生索引首批验证

日期：2026-11-07。当前状态：进行中（真实 MySQL 门禁已通过，物理索引表仍未实施）。

本批完成保存门禁和内存派生索引模型。索引由完整三维四包构建，带租户、场景、修订号、索引版本和四包来源摘要。索引只服务当前修订，不接受客户端独立写入，也没有新增数据库表。

验证结果：

- `SceneRevisionDerivedIndexTest` 3 项通过。
- `Dim3dSceneServiceTest` 17 项通过，包含未知区块成员拒绝后修订数保持不变。
- `mvn -pl wx-module-3d -am "-Dtest=SceneRevisionDerivedIndexTest,Dim3dSceneServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` 通过，共20项。
- `mvn -o -Ptest148 -pl wx-module-3d -am "-Dmaven.test.skip=true" package` 通过。
- 候选包 `wx-module-3d/target/wx-module-3d.jar` SHA-256 为 `271064ff9bb76b19af216d0772f3d23e104bd213a1957e4410bba07bd992b3b`，大小 194,656,522 字节。
- 在 148 上使用该候选包启动独立 `data06-dimensions` 服务，连接独立 MySQL 数据库 `wx_data06_20261107`，未触碰共享服务和业务库。
- 真实接口链路通过：创建场景、获取编辑锁、合法保存生成修订 2；同 `requestId` 重放仍返回修订 2；以当前基线 2 提交未知区块成员时返回业务错误 400，场景当前修订仍为 2；最后已释放编辑锁。
- 联验完成后已停止并删除隔离容器、数据库、账号和远端临时目录。

当前未部署共享 148 服务，未执行数据库迁移。下一项是冻结索引字段与来源摘要合同，评审是否进入 DATA-07 分片读取和可重建物理索引表设计。
