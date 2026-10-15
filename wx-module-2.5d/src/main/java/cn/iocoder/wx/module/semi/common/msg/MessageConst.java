package cn.iocoder.wx.module.semi.common.msg;

public class MessageConst {
    /**
     * {0}<br>
     * Level: debug
     */
    public static final String MSG_01000001 = "01000001";
    /**
     * {0}<br>
     * Level: warn
     */
    public static final String MSG_01000003 = "01000003";
    /**
     * {0}<br>
     * Level: error
     */
    public static final String MSG_01000005 = "01000005";

    /**
     * API执行开始。[API：{0}:{1}]
     */
    public static final String MSG_01010001 = "01010001";
    /**
     * API执行结束。[API：{0}:{1}]
     */
    public static final String MSG_01010002 = "01010002";

    /**
     * SQL执行异常。[SQLStateCode:{0}, Message:{1}]
     */
    public static final String MSG_01020001 = "01020001";
    /**
     * DB发生异常。[SQLStateCode:{0}, Message:{1}]
     */
    public static final String MSG_01020002 = "01020002";
    /**
     * DB session 初期化失败。
     */
    public static final String MSG_01020003 = "01020003";
    /**
     * 无法获取DB连接，连接池等待空闲的连接超时。
     */
    public static final String MSG_01020004 = "01020004";

    /**
     * 文件不存在。[文件名：{0}]
     */
    public static final String MSG_01030001 = "01030001";

    /**
     * 设定文件：{0}读取失败。
     */
    public static final String MSG_01030002 = "01030002";

    /**
     * 设定文件读取失败。[文件名：{0}]
     */
    public static final String MSG_01040001 = "01040001";
    /**
     * 设定文件格式错误。[文件名：{0}, {1}行]
     */
    public static final String MSG_01040002 = "01040002";
    /**
     * 设定文件读取失败。[文件名：{0}, 属性名：{1}]
     */
    public static final String MSG_01040003 = "01040003";
    /**
     * 设定文件属性名不存在。[文件名:{0},属性名:{1}]
     */
    public static final String MSG_01040004 = "01040004";

    /**
     * 请求处理被中断。
     */
    public static final String MSG_01050001 = "01050001";

    /**
     * 发生了想定外的异常。
     */
    public static final String MSG_01990001 = "01990001";
}
