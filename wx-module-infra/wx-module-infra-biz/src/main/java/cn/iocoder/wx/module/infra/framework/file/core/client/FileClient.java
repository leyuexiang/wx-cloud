package cn.iocoder.wx.module.infra.framework.file.core.client;

import cn.iocoder.wx.module.infra.framework.file.core.client.s3.FilePresignedUrlRespDTO;

/**
 * 文件客户端
 *
 * @author 九天芸菲
 */
public interface FileClient {

    /**
     * 获得客户端编号
     *
     * @return 客户端编号
     */
    Long getId();

    /**
     * 上传文件
     *
     * @param content 文件流
     * @param path    相对路径
     * @return 完整路径，即 HTTP 访问地址
     * @throws Exception 上传文件时，抛出 Exception 异常
     */
    String upload(byte[] content, String path, String type) throws Exception;

    /**
     * 删除文件
     *
     * @param path 相对路径
     * @throws Exception 删除文件时，抛出 Exception 异常
     */
    void delete(String path) throws Exception;

    /**
     * 获得文件的内容
     *
     * @param path 相对路径
     * @return 文件的内容
     */
    byte[] getContent(String path) throws Exception;

    /**
     * 获得文件预签名地址
     *
     * @param path 相对路径
     * @return 文件预签名地址
     */
    default FilePresignedUrlRespDTO getPresignedObjectUrl(String path) throws Exception {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 生成受限的 GET 读取地址。该接口与既有 PUT 上传签名分离，缺少实现时必须失败关闭。
     *
     * @param path 相对对象键
     * @param expirySeconds 签名有效期，秒
     * @return 短时读取 URL
     */
    default String getPresignedDownloadUrl(String path, int expirySeconds) throws Exception {
        throw new UnsupportedOperationException("当前文件存储不支持受限读取签名");
    }

}
