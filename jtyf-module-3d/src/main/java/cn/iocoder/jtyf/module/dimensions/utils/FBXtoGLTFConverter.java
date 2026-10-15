package cn.iocoder.jtyf.module.dimensions.utils;

import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;

public class FBXtoGLTFConverter {
    public static void main(String[] args) {
        // 设置FBX文件路径
        String fbxFilePath = "C:\\fbx\\engine.FBX";

        // 设置GLTF文件输出路径
        String gltfFilePath = "C:\\fbx\\engine.gltf";

        // 使用Assimp进行文件转换
        AIScene scene = Assimp.aiImportFile(fbxFilePath,
                Assimp.aiProcess_Triangulate
                        | Assimp.aiProcess_GenSmoothNormals
                        | Assimp.aiProcess_JoinIdenticalVertices
                        | Assimp.aiProcess_PreTransformVertices
                        | Assimp.aiProcess_FixInfacingNormals
                        | Assimp.aiProcess_OptimizeMeshes
                        | Assimp.aiProcess_OptimizeGraph);

        // 检查导入是否成功
        if (scene == null) {
            System.err.println("导入 FBX 文件失败: " + Assimp.aiGetErrorString());
            return;
        }
        try {
            // 导出场景为 gltf 文件
            int exportResult = Assimp.aiExportScene(scene, "gltf2", gltfFilePath, 1);
            if (exportResult != 0) {
                System.err.println("导出为 gltf 文件失败: " + Assimp.aiGetErrorString());
            } else {
                System.out.println("成功将 FBX 文件转换为 gltf 文件！");
            }
        } finally {
            // 释放导入的场景资源
            Assimp.aiReleaseImport(scene);
        }


//        long formatCount = Assimp.aiGetExportFormatCount();
//        System.out.println("支持的导出格式数量: " + formatCount);
//
//        for (int i = 0; i < formatCount; i++) {
//            AIExportFormatDesc formatDesc = Assimp.aiGetExportFormatDescription(i);
//            if (formatDesc != null) {
//                System.out.println("ID: " + formatDesc.id());
//                System.out.println("描述: " + formatDesc.descriptionString());
//                System.out.println("文件扩展名: " + formatDesc.fileExtensionString());
//                System.out.println("------------------------");
//            }
//        }

    }
}