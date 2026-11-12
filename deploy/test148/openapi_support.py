"""148 文档聚合配置：仅连接已部署模块，复用服务原有的公开文档路径。"""


SERVICES = {"system": "系统服务", "infra": "基础设施服务", "dimensions": "三维服务"}


def enable_openapi(config):
    """只改文档相关配置，保持现场业务路由、端口和身份头清理规则。"""
    gateway = config["spring"]["cloud"]["gateway"]
    route_ids = {name + "-openapi-test148" for name in SERVICES}
    business_routes = [route for route in gateway["routes"] if route["id"] not in route_ids]
    documentation_routes = []
    aggregation = []
    for name, title in SERVICES.items():
        # 从现有业务路由取真实上游，避免生成器默认端口覆盖现场部署端口。
        business = next(route for route in business_routes if route["id"] == name + "-test148")
        prefix = "/admin-api/" + name
        documentation_routes.append({
            "id": name + "-openapi-test148", "order": -10, "uri": business["uri"],
            "predicates": ["Method=GET", "Path=" + prefix + "/v3/api-docs," + prefix + "/v3/api-docs/**"],
            "filters": ["RemoveRequestHeader=x-bot-user",
                        "RewritePath=^" + prefix + r"(?<docPath>/v3/api-docs(?:/.*)?)$, $\{docPath}"],
        })
        # 后端规范中的接口路径已含 /admin-api，聚合不能再次添加模块前缀。
        aggregation.append({"name": title, "service-name": name + "-server",
                            "url": prefix + "/v3/api-docs", "context-path": "/"})
    gateway["routes"] = documentation_routes + business_routes
    config.setdefault("knife4j", {})["gateway"] = {
        "enabled": True, "strategy": "manual", "routes": aggregation,
    }
    return config
