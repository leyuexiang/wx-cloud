package cn.iocoder.wx.module.dimensions.utils;

import org.lwjgl.assimp.*;

import java.nio.IntBuffer;

import static org.lwjgl.assimp.Assimp.*;

public class FBXtoGLBConverter {
    public static void main(String[] args) {
        // 设置FBX文件路径
        String fbxFilePath = "C:\\fbx\\engine\\engine.FBX";

        // 设置GLB文件输出路径
        String gltfFilePath = "C:\\fbx\\engine\\engine.glb";

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


        // 在导入 FBX 文件后，遍历材质和纹理信息
        for (int i = 0; i < scene.mNumMaterials(); i++) {
            AIMaterial material = AIMaterial.create(scene.mMaterials().get(i));
            AIString texPath = AIString.calloc();
            int result = aiGetMaterialTexture(material, aiTextureType_DIFFUSE, 0, texPath, (IntBuffer) null, null, null, null, null, null);
            if (result == 0) {
                System.out.println("材质 " + i + " 的纹理路径: " + texPath.dataString());
            }
            texPath.free();
        }


        try {
            // 导出模型为 GLB 格式，并嵌入纹理
            int exportResult = Assimp.aiExportScene(scene, "glb2", gltfFilePath, Assimp.aiProcess_EmbedTextures);
            if (exportResult != aiReturn_SUCCESS) {
                System.err.println("导出为 glb 文件失败: " + Assimp.aiGetErrorString());
            } else {
                System.out.println("成功将 FBX 文件转换为 glb 文件！");
            }
        } finally {
            // 释放导入的场景资源
            Assimp.aiReleaseImport(scene);
        }

    }


    /**
     * 支持的导出格式数量和格式描述
     */
    private void formatCountAndFormatDesc() {
        long formatCount = Assimp.aiGetExportFormatCount();
        System.out.println("支持的导出格式数量: " + formatCount);

        for (int i = 0; i < formatCount; i++) {
            AIExportFormatDesc formatDesc = Assimp.aiGetExportFormatDescription(i);
            if (formatDesc != null) {
                System.out.println("ID: " + formatDesc.id());
                System.out.println("描述: " + formatDesc.descriptionString());
                System.out.println("文件扩展名: " + formatDesc.fileExtensionString());
                System.out.println("------------------------");
            }
        }
    }
}