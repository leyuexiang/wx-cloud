package cn.iocoder.wx.module.dimensions.domain.vo;

import java.util.List;

/** 既有 GIS 列表字段投影；来源编号以字符串返回，不表示资源表编号。 */
public record GisCatalogEntry(String id, String gisName, String dataType, String coordinate,
        int level, String tileSize, String path, String fileName, boolean sliceFlag,
        String sliceRule, String latitude, String longitude, List<Double> bounds, String fileExtension) {}
