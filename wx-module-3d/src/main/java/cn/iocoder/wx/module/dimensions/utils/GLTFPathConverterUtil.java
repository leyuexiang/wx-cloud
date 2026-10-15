package cn.iocoder.wx.module.dimensions.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class GLTFPathConverterUtil {



    public static void gltfPathConverter(String gltfFilePath) throws IOException {

        Path gltfpath = Paths.get(gltfFilePath);
        String gltfContent = new String(Files.readAllBytes(gltfpath));

        String convertedContent = gltfContent.replace("\\", "/");

        //覆盖原文件
        Files.write(gltfpath,convertedContent.getBytes());

        log.info("路径转换并覆盖成功");

    }
}
