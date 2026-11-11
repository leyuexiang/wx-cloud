package cn.iocoder.wx.module.infra.service.file;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;

/**
 * 文件 Service 接口
 *
 * @author 九天芸菲
 */
public interface FileService {

    /**
     * 获得文件分页
     *
     * @param pageReqVO 分页查询
     * @return 文件分页
     */
    PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO);

    /**
     * 上传一份具有明确归属的图片；普通历史上传仍保持原始地址响应。
     * 文件类型与有界大小校验通过后，由服务端生成唯一对象键。
     *
     * @param originalName 原始展示名
     * @param content 有界图片文件内容
     * @return 已成功登记的文件记录
     */
    OwnedImageUploadResult createOwnedImage(String originalName, byte[] content) throws Exception;

    /**
     * 查询当前身份可见的文件；忽略表要求所有归属谓词显式存在于本查询。
     *
     * @param id 文件编号
     * @return 可见文件，不存在/不可见时返回 null
     */
    FileDO getOwnedFile(Long id);

    /**
     * 为已通过范围检查的文件签发限时 GET 地址。无 GET 签名能力时安全失败。
     */
    String getOwnedFileReadUrl(FileDO file);

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param name    文件名称
     * @param path    文件路径
     * @param content 文件内容
     * @return 文件路径
     */
    String createFile(String name, String path, byte[] content);

    /**
     * 创建文件
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createFile(FileCreateReqVO createReqVO);

    /**
     * 删除文件
     *
     * @param id 编号
     */
    void deleteFile(Long id) throws Exception;

    /**
     * 获得文件内容
     *
     * @param configId 配置编号
     * @param path     文件路径
     * @return 文件内容
     */
    byte[] getFileContent(Long configId, String path) throws Exception;

    /**
     * 生成文件预签名地址信息
     *
     * @param path 文件路径
     * @return 预签名地址信息
     */
    FilePresignedUrlRespVO getFilePresignedUrl(String path) throws Exception;

}
