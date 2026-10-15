package cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统计情报
 */
public class StatisticsResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int accountCount;

    @JsonProperty
    private int total2d;

    @JsonProperty
    private int last302d;

    @JsonProperty
    private int last602d;

    @JsonProperty
    private int total3d;

    @JsonProperty
    private int last303d;

    @JsonProperty
    private int last603d;

    @JsonProperty
    private int totalV;

    @JsonProperty
    private int last30V;

    @JsonProperty
    private int last60V;

    public int getAccountCount() {
        return accountCount;
    }

    public void setAccountCount(int accountCount) {
        this.accountCount = accountCount;
    }

    public int getTotal2d() {
        return total2d;
    }

    public void setTotal2d(int total2d) {
        this.total2d = total2d;
    }

    public int getLast302d() {
        return last302d;
    }

    public void setLast302d(int last302d) {
        this.last302d = last302d;
    }

    public int getLast602d() {
        return last602d;
    }

    public void setLast602d(int last602d) {
        this.last602d = last602d;
    }

    public int getTotal3d() {
        return total3d;
    }

    public void setTotal3d(int total3d) {
        this.total3d = total3d;
    }

    public int getLast303d() {
        return last303d;
    }

    public void setLast303d(int last303d) {
        this.last303d = last303d;
    }

    public int getLast603d() {
        return last603d;
    }

    public void setLast603d(int last603d) {
        this.last603d = last603d;
    }

    public int getTotalV() {
        return totalV;
    }

    public void setTotalV(int totalV) {
        this.totalV = totalV;
    }

    public int getLast30V() {
        return last30V;
    }

    public void setLast30V(int last30v) {
        last30V = last30v;
    }

    public int getLast60V() {
        return last60V;
    }

    public void setLast60V(int last60v) {
        last60V = last60v;
    }
}
