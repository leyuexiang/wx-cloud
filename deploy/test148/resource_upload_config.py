"""生成签名对象传输的跨域配置，默认兼容多个前端及无来源头的调试工具。"""

import re
from urllib.parse import urlsplit


# 按当前用户要求取消前端来源登记；对象传输不使用浏览器凭据，权限由短时签名决定。
DEFAULT_ALLOWED_ORIGINS = ("*",)
_HOST_LABEL = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
_ORIGIN = re.compile(
    r"^(?P<scheme>https?)://(?P<host>localhost|[A-Za-z0-9.-]+)(?::(?P<port>[0-9]{1,5}))?$",
    re.IGNORECASE,
)


def parse_allowed_origins(value):
    """接受单独通配符或显式精确列表；兼容旧部署覆盖并拒绝指令注入。"""
    candidates = list(DEFAULT_ALLOWED_ORIGINS) if value is None else value.split(",")
    if not candidates or any(not item.strip() for item in candidates):
        raise ValueError("上传跨域来源列表不能为空或包含空项")
    if len(candidates) == 1 and candidates[0].strip() == "*":
        return ("*",)

    normalized = []
    for candidate in candidates:
        origin = candidate.strip()
        match = _ORIGIN.fullmatch(origin)
        if not match:
            raise ValueError("上传跨域来源必须是协议、主机和可选端口组成的精确源地址")

        parsed = urlsplit(origin)
        host = match.group("host").lower()
        if parsed.path or parsed.query or parsed.fragment or parsed.username or parsed.password:
            raise ValueError("上传跨域来源不能包含路径、查询参数、片段或凭据")
        if host != "localhost" and any(not _HOST_LABEL.fullmatch(label) for label in host.split(".")):
            raise ValueError("上传跨域来源主机名无效")

        try:
            port = parsed.port
        except ValueError as error:
            raise ValueError("上传跨域来源端口无效") from error
        if port is not None and not 1 <= port <= 65535:
            raise ValueError("上传跨域来源端口超出有效范围")

        scheme = match.group("scheme").lower()
        if port == (80 if scheme == "http" else 443):
            port = None
        canonical = f"{scheme}://{host}" + (f":{port}" if port is not None else "")
        if canonical not in normalized:
            normalized.append(canonical)
    return tuple(normalized)


def render_nginx_origin_map(origins, variable="wx_resource_upload_cors_origin"):
    """上传和下载使用独立映射；通配模式不反射来源，也不允许浏览器凭据。"""
    if not re.fullmatch(r"[a-z][a-z0-9_]*", variable):
        raise ValueError("跨域映射变量名无效")
    validated = parse_allowed_origins(",".join(origins))
    wildcard = validated == ("*",)
    lines = [
        f"map $http_origin ${variable} {{",
        '    default "*";' if wildcard else '    default "";',
    ]
    if not wildcard:
        lines.extend(f'    "{origin}" $http_origin;' for origin in validated)
    lines.extend(("}", ""))
    return "\n".join(lines)
