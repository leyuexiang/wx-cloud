package cn.iocoder.wx.module.dimensions.utils;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Slf4j
public class ZipUtils {

    /*
     * @Description:解压压缩包、创建解压目录并删除压缩包
     * @Param: zip文件路径
     * @return: 解压文件目录路径
     * @Author: jianlei
     * @Date: 2023/9/27
     */
    public static String unzipAndDelete(String zipFilePath, String unZipRootDirPath){
        //构建目标解压路径
        File file = new File(zipFilePath);
        String fileName = file.getName();
        String unZipDirName = fileName.substring(0, fileName.lastIndexOf(".")) ;
        return ZipUtils.unzipAndDelete(file, unZipRootDirPath, unZipDirName);
    }

    /*
     * @Description:解压压缩包、创建解压目录并删除压缩包
     *              但会新建解压目录，增加雪花名称，防止解压目录名重复
     * @Param: zip文件路径
     * @return: 解压文件目录路径
     * @Author: jianlei
     * @Date: 2023/9/27
     */
    public static String unzipBuildNameAndDelete(String zipFilePath, String unZipRootDirPath){
        //构建目标解压路径--
        File file = new File(zipFilePath);
        String fileName = file.getName();
        String unZipDirName = fileName.substring(0, fileName.lastIndexOf(".")) + IdWorker.getId();
        return ZipUtils.unzipAndDelete(file, unZipRootDirPath, unZipDirName);
    }

    public static String unzipAndDelete(File unZipfile, String unZipRootDirPath, String unZipDirName) {
        String unZipDirPathNew = unzip(unZipfile, unZipRootDirPath + unZipDirName);
        try {
            Files.delete(unZipfile.toPath());
        } catch (IOException e) {
            log.error("删除压缩包失败: {}", e.getMessage());
        }
        log.info("返回解压后的路径：{}", unZipDirPathNew);
        return unZipDirPathNew;
    }

    public static String unzip(String filePath, String zipDir) {
        return unzip(new File(filePath), zipDir);
    }

    public static String unzip(File srcZipFile, String zipDir) {
        return unzip(srcZipFile, zipDir, zipCharset(srcZipFile));
    }

    private static String unzip(File srcZipFile, String zipDir, Charset charset) {
        zipDir = Path.of(zipDir).toString();
        try (ZipFile zipFile = new ZipFile(srcZipFile, charset)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                File currentFile = new File(zipDir + File.separator + zipEntry.getName());

                // 无论是否为目录条目，都确保父目录存在
                File parentDir = currentFile.getParentFile();
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    log.info("父级文件目录创建成功,目录路径为：{}", parentDir.getAbsolutePath());
                }
                if(zipEntry.isDirectory()){
                    if (!currentFile.exists() && !currentFile.mkdir()) {
                        log.error("文件目录创建失败,目录路径为：{}", currentFile.getAbsolutePath());
                    }
                    continue;
                }
                writeZipCurrentFile(zipFile, zipEntry, currentFile);
            }
        } catch (Exception e) {
            log.error("编码[{}]文件：{}, 解压失败：{} ", charset.name(), srcZipFile.getAbsolutePath(), e.getMessage());
        }
        return Path.of(zipDir).toString();
    }

    private static void writeZipCurrentFile(ZipFile zipFile, ZipEntry entry, File currentFile) throws IOException {
        try(BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(currentFile))) {
            int bytesRead;
            byte[] buffer = new byte[8192];
            while ((bytesRead = is.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
        }
    }

    public static Charset zipCharset(String srcZipFile) {
        return zipCharset(new File(srcZipFile));
    }

    private static Charset zipCharset(File srcZipFile) {
        try (ZipFile ignored = new ZipFile(srcZipFile, StandardCharsets.UTF_8)) {
            return StandardCharsets.UTF_8;
        } catch (Exception e) {// 根据压缩文件异常信息，判断压缩文件编码
            if (e instanceof ZipException && StringUtils.contains(e.getMessage(), "bad entry name")) {
                return Charset.forName("GBK");
            }
            return StandardCharsets.UTF_8;
        }
    }
}
