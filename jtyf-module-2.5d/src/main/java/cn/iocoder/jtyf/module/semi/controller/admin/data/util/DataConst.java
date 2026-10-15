package cn.iocoder.jtyf.module.semi.controller.admin.data.util;

import java.util.Arrays;
import java.util.List;

public class DataConst {

    /**
     * 通用数据分类列表
     */
    public static final List<String> COMMON_DATA_COLLECTION_LIST = Arrays.asList(new String[] {
            "2d", "2d.component", "3d", "3d.component", "v", "v.component", "datasource"
    });

    public static final List<String> HISTORY_DATA_COLLECTION_LIST = Arrays.asList(new String[] {
            "2d", "2d.component", "3d", "3d.component", "v", "v.component"
    });

    /**
     * 权限动作：新增
     */
    public static final int AUTH_ACT_ADD = 1;

    /**
     * 权限动作：读
     */
    public static final int AUTH_ACT_READ = 2;

    /**
     * 权限动作：写
     */
    public static final int AUTH_ACT_UPDATE = 3;

    /**
     * 权限动作：授权
     */
    public static final int AUTH_ACT_ADMIN = 4;

    /**
     * 权限动作：所有者
     */
    public static final int AUTH_ACT_OWNER = 5;

    /**
     * 权限主题类型：所有者
     */
    public static final int AUTH_SUB_TYP_OWNER = 1;

    /**
     * 权限主题类型：部门
     */
    public static final int AUTH_SUB_TYP_DEPARTMENT = 2;

    /**
     * 权限主题类型：团队
     */
    public static final int AUTH_SUB_TYP_TEAM = 3;

    /**
     * 权限主题类型：角色
     */
    public static final int AUTH_SUB_TYP_ROLE = 4;
}
