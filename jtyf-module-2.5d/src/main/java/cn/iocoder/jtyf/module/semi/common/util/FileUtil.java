package cn.iocoder.jtyf.module.semi.common.util;


import cn.iocoder.jtyf.module.semi.common.exception.CmnBizException;
import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileUtil {
    /**
     * logger
     */
    private static final CmnLogger logger = CmnLogger.getLogger(FileUtil.class);

    // 图片
    private static final List<String> FILE_EXT_PIC = Arrays.asList(new String[] {
            "png", "jpg", "jpeg", "gif", "svg", "bmp"
    });

    // 文档
    private static final List<String> FILE_EXT_DOC = Arrays.asList(new String[] {
            "wps", "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "md", "zip", "gz", "7z", "rar"
    });

    // 音乐
    private static final List<String> FILE_EXT_MUS = Arrays.asList(new String[] {
            "mp3", "wma", "wav"
    });

    // 视频
    private static final List<String> FILE_EXT_VID = Arrays.asList(new String[] {
            "avi", "flv", "rm", "rmvb", "mp4", "wmv", "mov", "mpg", "mkv", "3gp"
    });

    // 3D模型
    private static final List<String> FILE_EXT_MDL_3D = Arrays.asList(new String[] {
            "glb", "gltf", "max", "obj", "fbx"
    });

    public static byte[] getFileBytes(String fullPathname) throws CmnBizException {
        File file = null;
        FileInputStream inStream = null;
        FileChannel channel = null;
        try {
            file = new File(fullPathname);
            inStream = new FileInputStream(file);
            channel = inStream.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            while (channel.read(buffer) > 0) {
            }
            return buffer.array();
        } catch (FileNotFoundException e) {
            Object[] args = new Object[] { fullPathname };
            logger.log(MessageConst.MSG_01030001, args);
            throw new CmnBizException(MessageConst.MSG_01030001, args);
        } catch (Exception e) {
            logger.log(MessageConst.MSG_01990001, null, e);
            throw new CmnBizException(MessageConst.MSG_01990001, null, e);
        } finally {
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException e) {
            }
            try {
                if (inStream != null) {
                    inStream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public static boolean checkFileExist(File file) {
        return file.exists() && file.isFile();
    }

    public static boolean checkFileExist(String filePath) {
        File file = new File(filePath);
        return checkFileExist(file);
    }

    public static boolean checkDirExist(File dir) {
        return dir.exists() && dir.isDirectory();
    }

    public static boolean checkDirExist(String dirPath) {
        File dir = new File(dirPath);
        return checkDirExist(dir);
    }

    public static List<String> listSubdir(String root, String path, boolean prefix) {
        if (!checkDirExist(root + path)) {
            return null;
        }
        File dir = new File(root + path);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        List<String> result = new ArrayList<String>();
        for (File file : files) {
            if (checkDirExist(file)) {
                if (prefix) {
                    result.add(path + file.getName());
                } else {
                    result.add(file.getName());
                }
            }
        }
        return result;
    }

    public static List<String> listSubfile(String root, String path, boolean prefix) {
        if (!checkDirExist(root + path)) {
            return null;
        }
        File dir = new File(root + path);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        List<String> result = new ArrayList<String>();
        for (File file : files) {
            if (checkFileExist(file)) {
                if (prefix) {
                    result.add(path + file.getName());
                } else {
                    result.add(file.getName());
                }
            }
            if (checkDirExist(file)) {
                File[] subfiles = file.listFiles();
                if (subfiles == null || subfiles.length == 0) {
                    continue;
                }
                for (File subfile : subfiles) {
                    if (checkFileExist(subfile)) {
                        if (prefix) {
                            result.add(path + file.getName() + "/" + subfile.getName());
                        } else {
                            result.add(file.getName() + "/" + subfile.getName());
                        }
                    }
                }
            }
        }
        return result;
    }

    public static String judgeFileType(String fileExtension) {
        if (StrUtil.isEmpty(fileExtension)) {
            return null;
        }
        String type = null;
        fileExtension = fileExtension.toLowerCase();
        if (FILE_EXT_PIC.contains(fileExtension)) {
            type = "图片";
        } else if (FILE_EXT_DOC.contains(fileExtension)) {
            type = "文档";
        } else if (FILE_EXT_MUS.contains(fileExtension)) {
            type = "音乐";
        } else if (FILE_EXT_VID.contains(fileExtension)) {
            type = "视频";
        } else if (FILE_EXT_MDL_3D.contains(fileExtension)) {
            type = "3D模型";
        }
        return type;
    }
}
