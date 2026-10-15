package cn.iocoder.wx.module.dimensions.utils;

import org.lwjgl.assimp.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PNGtoJLTFConverter {
    public static void main(String[] args) {
        // 设置FBX文件路径
        String fbxFilePath = "C:\\fbx\\test\\a.fbx";
        String textureFilePath = "C:\\fbx\\test\\61.png";
        // 设置GLTF文件输出路径
        String gltfFilePath = "C:\\fbx\\test\\b.gltf";

        // 创建属性存储
        AIPropertyStore settings = Assimp.aiCreatePropertyStore();

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

        // 加载纹理图片（这里只是示例获取文件路径，未实际处理图片数据）
        try {
            if (Files.exists(Paths.get(textureFilePath))) {
                // 假设只有一个材质
                if (scene.mNumMaterials() > 0) {
                    AIMaterial material = AIMaterial.create(scene.mMaterials().get(0));
                    AIString texturePath = AIString.calloc();
                    try {
                        // 将 String 转换为 ByteBuffer
                        ByteBuffer buffer = StandardCharsets.UTF_8.encode(textureFilePath);
                        // 将 ByteBuffer 中的内容复制到 AIString
                        texturePath.data(buffer);

                        int textureType = Assimp.aiTextureType_DIFFUSE;
                        int textureIndex = 0;
                        int result = Assimp.aiGetMaterialTexture(material, textureType, textureIndex, texturePath, (IntBuffer) null, null, null, null, null, null);
                        if (result == Assimp.aiReturn_SUCCESS) {
                            System.err.println("设置纹理失败: " + result);
                        }
                    } finally {
                        texturePath.free();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("检查纹理文件存在性时出错: " + e.getMessage());
        }

        try {
            // 导出场景为 gltf 文件
            int exportResult = Assimp.aiExportScene(scene, "gltf2", gltfFilePath, 0);
            if (exportResult != 0) {
                System.err.println("导出为 gltf 文件失败: " + Assimp.aiGetErrorString());
            } else {
                System.out.println("成功将 FBX 文件转换为 gltf 文件！");
            }
        } finally {
            // 释放导入的场景资源
            Assimp.aiReleaseImport(scene);
        }
    }
}