package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/** 目录排序只拼接服务端白名单及固定方向，同值追加目录编号以保证分页顺序。 */
public final class Dim3dCatalogOrder {
    private Dim3dCatalogOrder() { }

    public static String sql(Map<String, String> allowedColumns, String requestedColumn, String requestedDirection,
                             String invalidMessage) {
        String column = StringUtils.hasText(requestedColumn) ? allowedColumns.get(requestedColumn) : "id";
        String direction = StringUtils.hasText(requestedDirection) ? requestedDirection.toLowerCase(Locale.ROOT) : "desc";
        if (column == null || !(direction.equals("asc") || direction.equals("desc"))) {
            throw new ServiceException(400, invalidMessage);
        }
        return " ORDER BY " + column + " " + direction + ("id".equals(column) ? "" : ", id DESC");
    }
}
