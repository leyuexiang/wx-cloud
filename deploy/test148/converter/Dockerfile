# 基于服务器已安装的工具链派生；执行前记录基础镜像编号，不拉取浮动远端镜像。
ARG BASE_IMAGE
FROM ${BASE_IMAGE}
USER root
COPY material-reference.cjs /app/lib/material-reference.cjs
COPY apply-patch.cjs verify-materials.cjs /opt/model03/
RUN node /opt/model03/apply-patch.cjs && node /opt/model03/verify-materials.cjs
# 保持现场转换服务原有的非特权运行身份及入口。
USER node
