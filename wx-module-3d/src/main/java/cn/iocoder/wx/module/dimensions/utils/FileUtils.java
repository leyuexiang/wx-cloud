package cn.iocoder.wx.module.dimensions.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
public class FileUtils {

    public static String getFileSuffixLowerCase(String fileFullName) {
        return getFileSuffix(fileFullName).toLowerCase();
    }

    public static String getFileSuffix(String fileFullName) {
        return StringUtils.substringAfterLast(fileFullName, ".");
    }

    public static String getFileName(String fileFullName) {
        return StringUtils.substringBeforeLast(fileFullName, ".");
    }

    /**
     * 删除指定文件夹下文件,该文件夹也会被删除
     */
    public static void deleteFolders(String filePath) {

        Path path = Paths.get(filePath);
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir,
                                                          IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 删除指定文件夹下文件及子目录,该文件夹不会被删除
    public static void deleteSubDir(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteSubDir(file);//递归删除文件夹内文件
                    }
                    //删除子文件
                    if (file.delete()) {
                        log.info("成功删除子文件");
                    } else {
                        log.info("删除失败");
                    }
                }
            }
        }
    }
}
