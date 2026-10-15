package cn.iocoder.wx.module.dimensions.entity;

import lombok.Data;

@Data
public class Glass {

    private String colorHex;

    private Double metallic;

    private Double smoothness;

    private Integer refractionModel;

    private Double indexOfRefraction;

    private Double thickness;
}
