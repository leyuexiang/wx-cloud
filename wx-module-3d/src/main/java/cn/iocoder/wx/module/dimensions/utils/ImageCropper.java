package cn.iocoder.wx.module.dimensions.utils;

import cn.iocoder.wx.module.dimensions.service.constant.FileSuffixConstants;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * @author xwd
 * @description 图片裁剪工具类
 * @create 2023-04-14 10:49
 */
public class ImageCropper {
    public static void main(String[] args) {
        String inputImagePath = "C:\\fbx\\aaaaa\\8k_images1.png"; // 输入图片的路径
        String outputDirectory = "C:\\fbx\\aaaaa\\zzzzzzzzzzzzzzzzzz\\"; // 输出图片的目录
        int cropWidth = 100; // 裁剪后的图片宽度
        int cropHeight = 100; // 裁剪后的图片高度
        int numCrops = 3000; // 裁剪的份数

        try {
            // 读取输入图片
            File inputFile = new File(inputImagePath);
            BufferedImage originalImage = ImageIO.read(inputFile);

            // 创建输出目录
            File outputDir = new File(outputDirectory);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            /**
             * BufferedImage 里，图片的坐标体系以左上角为原点 (0, 0)，x 轴向右为正方向，y 轴向下为正方向
             */
            int x = 0;
            int y = 0;
            for (int i = 0; i < numCrops; i++) {
                if (x + cropWidth > originalImage.getWidth()) {
                    x = 0;
                    y += cropHeight;
                    if (y + cropHeight > originalImage.getHeight()) {
                        System.out.println("图片尺寸不足以裁剪出3000份。");
                        break;
                    }
                }

                // 裁剪图片
                BufferedImage croppedImage = originalImage.getSubimage(x, y, cropWidth, cropHeight);

                // 保存裁剪后的图片
                File outputFile = new File(outputDirectory + "crop_1" + i + FileSuffixConstants._JPG);
                ImageIO.write(croppedImage, "jpg", outputFile);

                x += cropWidth;
            }

            System.out.println("裁剪完成。");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
