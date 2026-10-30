"""从项目现有配置生成测试服务器的独立配置，不在文件中写入真实密钥。"""

from pathlib import Path
import copy
import yaml


def merge(target, incoming):
    """合并现有多文档配置，保留同一顶级键下的各项设置。"""
    for key, value in incoming.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge(target[key], value)
        else:
            target[key] = copy.deepcopy(value)
    return target


class ExistingConfigLoader(yaml.SafeLoader):
    """兼容历史配置中重复出现的顶级配置段，生成结果使用唯一键。"""


def construct_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=True)
        value = loader.construct_object(value_node, deep=True)
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            merge(result[key], value)
        else:
            result[key] = value
    return result


ExistingConfigLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, construct_mapping
)


def replace_placeholders(value):
    """未接入的外部集成使用测试环境变量占位，不能沿用旧明文配置。"""
    if isinstance(value, str):
        return value.replace("${REDACTED_SECRET}", "${WX_RUNTIME_SECRET}")
    if isinstance(value, dict):
        return {key: replace_placeholders(item) for key, item in value.items()}
    if isinstance(value, list):
        return [replace_placeholders(item) for item in value]
    return value


ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "config"
OUT.mkdir(parents=True, exist_ok=True)
MODULES = {
    "dimensions": ("wx-module-3d", 48092),
    "system": ("wx-module-system/wx-module-system-biz", 48081),
    "infra": ("wx-module-infra/wx-module-infra-biz", 48082),
    "gateway": ("wx-gateway", 12532),
}

for service, (module, port) in MODULES.items():
    config = {}
    source = ROOT / module / "src/main/resources/application.yaml"
    for document in yaml.load_all(source.read_text(encoding="utf-8-sig"), ExistingConfigLoader):
        merge(config, document or {})
    config = replace_placeholders(config)
    config.pop("rocketmq", None)
    config.setdefault("spring", {}).pop("config", None)
    config["spring"].pop("profiles", None)
    config["spring"]["cloud"] = {
        "nacos": {
            "discovery": {"enabled": False},
            "config": {"enabled": False, "import-check": {"enabled": False}},
        },
        "discovery": {"client": {"simple": {"instances": {
            "system-server": [{"uri": "http://127.0.0.1:48081"}],
            "infra-server": [{"uri": "http://127.0.0.1:48082"}],
            "dimensions-server": [{"uri": "http://127.0.0.1:48092"}],
        }}}},
    }
    config["server"] = {
        "port": "${WX_GATEWAY_PORT:12532}" if service == "gateway" else port,
        "address": "0.0.0.0" if service == "gateway" else "127.0.0.1",
    }
    config["management"] = {"endpoints": {"web": {"exposure": {"include": "health,info"}}},
                            "endpoint": {"health": {"show-details": "never"}}}
    config["logging"] = {"file": {"name": f"/app/logs/wx-{service}.log"},
                         "level": {"root": "INFO"}}
    config["wx"] = config.get("wx", {})
    config["wx"].pop("env", None)
    if service == "gateway":
        routes = []
        for name, upstream in [("system", 48081), ("infra", 48082), ("dimensions", 48092)]:
            routes.append({
                "id": f"{name}-test148",
                "uri": f"http://127.0.0.1:{upstream}",
                "predicates": [f"Path=/admin-api/{name}/**,/app-api/{name}/**" +
                               (",/admin-api/dim3d/**" if name == "dimensions" else "")],
                # 外部请求不能借内部机器人请求头绕过用户认证。
                "filters": ["RemoveRequestHeader=x-bot-user"],
            })
        config["spring"]["cloud"]["gateway"] = {"routes": routes,
                                                        "x-forwarded": {"prefix-enabled": False}}
        config["knife4j"] = {"gateway": {"enabled": False}}
    else:
        config["spring"]["datasource"] = {"dynamic": {
            "primary": "master",
            "datasource": {"master": {
                "url": "jdbc:mysql://127.0.0.1:3306/wx_editor_test148?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true",
                "username": "wx_editor_test148",
                "password": "${WX_DB_PASSWORD}",
                "driver-class-name": "com.mysql.cj.jdbc.Driver",
            }},
            "druid": {"initial-size": 1, "min-idle": 1, "max-active": 10},
        }}
        config["spring"]["data"] = {"redis": {
            "host": "127.0.0.1", "port": "${WX_REDIS_PORT:16380}", "database": 0,
            "password": "${WX_REDIS_PASSWORD}", "repositories": {"enabled": False},
        }}
        config["spring"]["kafka"] = {"bootstrap-servers": "127.0.0.1:9092",
                                     "listener": {"auto-startup": False, "missing-topics-fatal": False}}
        config["spring"]["boot"] = {"admin": {"client": {"enabled": False}, "context-path": "/admin"}}
        config["xxl"] = {"job": {"enabled": False, "accessToken": "${WX_RUNTIME_SECRET}"}}
        config["wx"]["security"] = {"mock-enable": False}
        config["wx"]["captcha"] = {"enable": False}
        config["wx"]["access-log"] = {"enable": False}
        config["wx"]["tenant"]["enable"] = True
        config["wx"]["message-data"] = {"speed": 1}
        # 密码登录依赖社交服务的工厂对象，但本次不配置任何第三方登录渠道。
        config["justauth"] = {"enabled": True, "type": {}}
        config["wx"]["mp"] = {"app-id": "wx-test148", "secret": "${WX_RUNTIME_SECRET}"}
        config["wx"]["miniapp"] = {"appid": "wx-test148", "secret": "${WX_RUNTIME_SECRET}"}
        config["minio"] = {
            "url": "http://127.0.0.1:9000", "accessKey": "${WX_MINIO_ACCESS_KEY}",
            "secretKey": "${WX_MINIO_SECRET_KEY}", "bucketName": "wx-art-test148",
            "expiry": 1, "breakpointTime": 1,
        }
        # 平台租户是受信服务配置，不从资源请求或任意角色名推导。
        config.setdefault("dim3d", {}).setdefault("resource", {})["platform-tenant-id"] = 1
        config["dim3d"]["resource"]["download-endpoint"] = "https://192.168.10.148:9444"
        config["dim3d"]["resource"]["upload-endpoint"] = "https://192.168.10.148:9445"
        config["server-path"] = "/admin-api/dimensions"
        config["file-save-path"] = "/data/web/"
        config["file-save-temporary-path"] = "/data/template/"
        config["file-save-img-path"] = "/data/images/"
        config["project-name-prefix"] = "wx"
        config["batch-conversion-actuality-path"] = "/data/batch/"
        config["material-path"] = "/data/material/"
        # ART-02：平台超级管理员必须同时来自该受信平台租户；缺省不允许全局写入。
        config["dim3d"] = {"resource": {"platform-tenant-id": 1}}
        config["ptc"] = {"license": {"start-check": False, "verify-system-switch": True,
                                      "license-path": "/data/license/license.lic"}}
        config["preview"] = {"base-url": "http://192.168.10.148:12522",
                             "2d": {"base-url": "${preview.base-url}/babylonWeb/editor"},
                             "3d": {"base-url": "${preview.base-url}/web/#/scenePreview"}}
        if service == "infra":
            config["spring"]["datasource"]["dynamic"]["datasource"]["master"]["name"] = "wx_editor_test148"
    path = OUT / f"{service}.yaml"
    path.write_text("# 测试服务器独立配置；密钥仅通过服务器环境变量注入。\n" +
                    yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
    print(f"generated {path.relative_to(ROOT)}")
