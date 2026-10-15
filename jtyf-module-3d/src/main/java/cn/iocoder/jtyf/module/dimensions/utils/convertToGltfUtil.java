package cn.iocoder.jtyf.module.dimensions.utils;

import lombok.extern.slf4j.Slf4j;
import org.lwjgl.assimp.AIPropertyStore;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;

import java.io.File;

import static org.lwjgl.assimp.Assimp.*;

@Slf4j
public class convertToGltfUtil {

    // 默认后处理标志配置
    private static final int DEFAULT_POST_PROCESS_FLAGS =
                    aiProcess_Triangulate |            // 必须：将多边形转换为三角形
                    aiProcess_JoinIdenticalVertices |  // 合并重复顶点，优化性能
                    aiProcess_GenSmoothNormals |       // 生成平滑法线（如果模型没有法线）
                    aiProcess_GlobalScale;             // 应用全局缩放

    /**
     * 将3D模型转换为glTF 2.0格式
     * @param sourceFilePath 源模型文件路径（支持FBX、OBJ等Assimp支持的格式）
     * @param gltfFilePath 输出glTF文件路径
     * @param globalScale 全局缩放因子（默认为1.0）
     * @return 转换成功返回true，失败返回false
     */
    public static boolean convertToGltf(String sourceFilePath, String gltfFilePath, float globalScale) {
        log.info("开始模型转换: {} -> {}", sourceFilePath, gltfFilePath);

        // 输入验证
        if (!validateInput(sourceFilePath, gltfFilePath)) {
            return false;
        }

        AIPropertyStore settings = null;
        AIScene aiScene = null;

        try {
            // 创建Assimp属性存储
            settings = Assimp.aiCreatePropertyStore();
            if (settings == null) {
                log.error("创建Assimp属性存储失败");
                return false;
            }

            // 设置全局缩放因子
            if (globalScale > 0) {
                Assimp.aiSetImportPropertyFloat(settings, AI_CONFIG_GLOBAL_SCALE_FACTOR_KEY, globalScale);
                log.debug("设置全局缩放因子: {}", globalScale);
            }

            // 导入模型
            long startTime = System.currentTimeMillis();
            aiScene = Assimp.aiImportFileExWithProperties(
                    sourceFilePath,
                    DEFAULT_POST_PROCESS_FLAGS,
                    null,
                    settings
            );

            if (aiScene == null) {
                String errorMsg = Assimp.aiGetErrorString();
                log.error("模型导入失败: {}", errorMsg);
                return false;
            }

            log.info("模型导入成功，耗时: {}ms", System.currentTimeMillis() - startTime);
            log.debug("模型信息: 网格数={}, 材质数={}, 动画数={}",
                    aiScene.mNumMeshes(),
                    aiScene.mNumMaterials(),
                    aiScene.mNumAnimations());

            // 导出为glTF 2.0
            startTime = System.currentTimeMillis();
            int exportResult = Assimp.aiExportScene(aiScene, "gltf2", gltfFilePath, 0);

            if (exportResult != 0) {
                String errorMsg = Assimp.aiGetErrorString();
                log.error("导出为glTF失败: {}", errorMsg);
                return false;
            }

            log.info("模型转换成功，耗时: {}ms", System.currentTimeMillis() - startTime);
            return true;

        } catch (Exception e) {
            log.error("模型转换过程中发生异常", e);
            return false;
        } finally {
            // 确保资源按正确顺序释放（先场景，后设置）
            if (aiScene != null) {
                Assimp.aiReleaseImport(aiScene);
                log.debug("已释放AIScene资源");
            }
            if (settings != null) {
                Assimp.aiReleasePropertyStore(settings);
                log.debug("已释放AIPropertyStore资源");
            }
        }
    }

    /**
     * 验证输入参数
     */
    private static boolean validateInput(String sourceFilePath, String gltfFilePath) {
        if (sourceFilePath == null || sourceFilePath.isEmpty()) {
            log.error("源文件路径不能为空");
            return false;
        }

        if (gltfFilePath == null || gltfFilePath.isEmpty()) {
            log.error("目标文件路径不能为空");
            return false;
        }

        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            log.error("源文件不存在或不是有效文件: {}", sourceFilePath);
            return false;
        }

        // 检查输出目录是否存在
        File gltfFile = new File(gltfFilePath);
        File parentDir = gltfFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                log.error("无法创建输出目录: {}", parentDir.getAbsolutePath());
                return false;
            }
        }

        return true;
    }

    // 重载方法，使用默认缩放因子
    public static boolean convertToGltf(String sourceFilePath, String gltfFilePath) {
        return convertToGltf(sourceFilePath, gltfFilePath, 1.0f);
    }
}    