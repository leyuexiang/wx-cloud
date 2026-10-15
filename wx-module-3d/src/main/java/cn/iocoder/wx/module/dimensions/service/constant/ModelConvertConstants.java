package cn.iocoder.wx.module.dimensions.service.constant;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * @author: cheYu
 * @desc: 转换模型允许的后缀常量类
 * @create: 2025-07-03 16:16
 */
public interface ModelConvertConstants {
    // 允许转换的后缀
    Set<String> ENABLE_SUFFIX_SET = ImmutableSet.of(FileSuffixConstants.OBJ, FileSuffixConstants.FBX,
            FileSuffixConstants.CDS, FileSuffixConstants.GLB);

    // 允许源文件转换的后缀
    Set<String> MODEL_SRC_SET = ImmutableSet.of(FileSuffixConstants.OBJ, FileSuffixConstants.FBX, FileSuffixConstants.CDS);

    // 允许目标文件转换的后缀
    Set<String> MODEL_TARGET_SET = ImmutableSet.of(FileSuffixConstants.GLTF, FileSuffixConstants.GLB);
}
