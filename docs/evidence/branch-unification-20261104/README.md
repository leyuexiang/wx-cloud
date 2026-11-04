# 后端分支代码统一

2026-11-04，BRANCH-01。用户确认以 dev 为开发基准。

整合前 dev 为 1275c827，master 为 9f61a95f。资源市场未提交修改已备份并固化为 0d5c9c9e。master 的物联网接入、三维状态投影和表前缀改造合入 dev，保留 dev 的资源事务、场景保存、认证及模型导入发布。

文档重复重命名采用 dev 的既定位置，合入文档的相对引用已修复。用户表映射和会话测试 SQL 使用 wx 前缀。保留本地备份引用 codex/backup-dev-before-unify-20261104 及 codex/backup-master-before-unify-20261104。

Java 选择资源、模型导入、场景、物联网、认证、用户、权限和短信工厂已有用例，共 444 项，441 项通过，3 项 Redis 实际依赖用例因未配置测试端口而跳过，零失败和零错误。物联网探测脚本 12 项与部署脚本 8 项通过。合入文档相对链接和未解决冲突检查均通过。详细结果见 [检查清单](checks.json)。

Java 回归执行参数保留如下。

~~~text
mvn -o -pl wx-module-3d,wx-module-iot/wx-module-iot-biz,wx-module-system/wx-module-system-biz,wx-module-infra/wx-module-infra-biz -am -Dtest=ModelImport*Test,ModelResourceManifestVerifierTest,ArtResource*Test,Dim3dSceneServiceTest,Iot*Test,AdminAuthServiceImplTest,AdminUserServiceImplTest,RoleServiceImplTest,PermissionServiceTest,SmsClientFactoryImplTest -Dsurefire.failIfNoSpecifiedTests=false test -q
python -m unittest discover -s tools/iot -p test_*.py
python -m unittest discover -s deploy/test148 -p test_*.py
~~~

用户本轮拉取的 main 三个提交均已包含在 dev 祖先历史。主目录 E:/Products/3D-editor/Server/jtyf-cloud 使用 dev，原 jtyf-cloud-scene148 工作树使用同一提交的 master。main 保留其已拉取提交。分支整合仅在本地完成，远端推送与服务器部署另行进行。

全工程离线编译执行 mvn -o -DskipTests compile -q，在既有 wx-module-2.5d 依赖 com.google.code:kaptcha:2.3.2 缺失处停止。华为和阿里既有仓库的该依赖地址返回 404，该模块源码及依赖与 main 相同。本次核心模块编译和上述回归通过，2.5D 全工程编译仍待补齐实际依赖。

2026-11-04补充，按用户全部统一的要求，本地 main 也快进至统一版本。main、master、dev 的当前代码一致。合并前的 main 另存于 codex/backup-main-before-unify-20261104，早前“main 保留其已拉取提交”的表述仅适用于前一步操作。远端分支仍保持推送前状态。
