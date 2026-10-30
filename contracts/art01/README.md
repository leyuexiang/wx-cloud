# ART-01 机器可读接口合同

本目录固定新资源接口的数据边界，实际路由待 ART-02/03 实现，不表示服务已经支持上传或替换。

- `openapi.yaml`：接口路径、认证、请求和响应，使用开放接口规范（OpenAPI）3.1。
- `resource-contract.schema.json`：使用 JSON 结构规范 2020-12，包含请求、响应和单资源主清单。
- `examples.json`：创建、替换、提交和当前描述示例，全部为合成数据。
- `content-hash-fixture.json`：含中文资产名的规范 UTF-8 字节及期望内容摘要，供 Unity/Java 实现对齐。
- `errors.json`：与后端 `ArtResourceErrorCode` 对齐的领域错误码。
- `verify-contract.py`：离线结构、路径、整数、主清单摘要和引用检查；不调用业务服务。

从后端仓库根目录执行：

```sh
python contracts/art01/verify-contract.py --report docs/evidence/backend-art01-20261029/contract-verification.json
```

验证工具使用本机已有的 `jsonschema` 和 `PyYAML`，不增加后端运行时依赖。自定义格式 `positive-int64-string`、`nonnegative-int64-string`、`positive-file-size-string` 除结构匹配外还检查范围；实际服务必须做同样的有界解析，不能依赖客户端生成器是否识别格式。

合同不单靠结构表达跨字段和数据库规则：相同对象路径、入口悬空、操作状态、归属、候选内容不可变、预期修订和真正的 SHA-256 回读仍须由服务验证。主清单算法与权限边界见[设计合同](../../docs/15_资源模板数据结构与接口合同.md)，最新验收见[记录](../../docs/evidence/backend-art01-20261029/README.md)。
