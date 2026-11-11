package cn.iocoder.wx.module.infra.service.file;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.io.FileUtils;
import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.wx.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.wx.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.wx.module.infra.framework.file.core.client.s3.FilePresignedUrlRespDTO;
import cn.iocoder.wx.module.infra.framework.file.core.utils.FileTypeUtils;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;

/**
 * 文件 Service 实现类
 *
 * @author 九天芸菲
 */
@Service
public class FileServiceImpl implements FileService {

    @Resource
    private FileConfigService fileConfigService;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO) {
        return fileMapper.selectPage(pageReqVO);
    }

    @Override
    @SneakyThrows
    public OwnedImageUploadResult createOwnedImage(String originalName, byte[] content) {
        LoginUser actor = requireOwnedImageActor();
        // 上传能力沿用业务写权限，文件可见性由归属约束；普通图片使用者无需后台文件管理权限。
        if (!securityFrameworkService.hasAnyPermissions("dim3d:icon:add", "dim3d:icon:edit",
                "dim3d:model:add", "dim3d:model:edit", "dim3d:template:edit",
                "dim3d:scene:add", "dim3d:scene:edit")) {
            throw new ServiceException(403, "无权上传文件");
        }
        ImageUploadValidator.CheckedImage checked = ImageUploadValidator.validate(originalName, content);
        String suffix = checked.suffix();
        String mediaType = checked.mediaType();
        String path = "images/" + IdUtil.fastSimpleUUID() + "." + suffix.toLowerCase(Locale.ROOT);
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        String url = client.upload(content, path, mediaType);
        String readUrl;
        OwnedImageUploadResult result;
        boolean metadataAttempted = false;
        try {
            // 上传元数据之前先检查下载域能为该对象生成 GET 签名；旧 PUT 签名绝不能冒充图片读取 URL。
            readUrl = client.getPresignedDownloadUrl(path, 600);
            if (!StringUtils.hasText(readUrl)) throw new IllegalStateException("存储客户端未返回读取签名");
            metadataAttempted = true;
            result = new TransactionTemplate(transactionManager).execute(status -> {
                FileDO file = new FileDO();
                file.setConfigId(client.getId());
                file.setName(checked.originalName());
                file.setPath(path);
                file.setUrl(url);
                file.setType(mediaType);
                file.setSize(content.length);
                file.setTenantId(actor.getTenantId());
                file.setUploaderId(actor.getId());
                file.setVisibilityScope("PRIVATE_UPLOAD");
                fileMapper.insert(file);
                return new OwnedImageUploadResult(file, readUrl);
            });
            if (result == null) throw new IllegalStateException("文件元数据事务未返回结果");
            return result;
        } catch (Exception ex) {
            // 签名失败也可能抛受检异常。提交结果不明时先回查，数据库不可用则保留对象供后续核验。
            try {
                if (!metadataAttempted || fileMapper.selectOne(FileDO::getPath, path) == null) {
                    client.delete(path);
                }
            } catch (Exception cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw new ServiceException(metadataAttempted ? 500 : 503,
                    metadataAttempted ? "保存图片文件信息失败，请重试" : "图片读取地址签发失败，请重试");
        }
    }

    @Override
    public FileDO getOwnedFile(Long id) {
        // 已登录用户只可查询显式授权范围，读取能力不依赖后台文件管理菜单。
        LoginUser actor = requireOwnedImageActor();
        if (id == null || id <= 0) return null;
        return fileMapper.selectVisibleById(id, actor.getTenantId(), actor.getId());
    }

    @Override
    @SneakyThrows
    public String getOwnedFileReadUrl(FileDO file) {
        if (file == null || file.getId() == null || file.getConfigId() == null || file.getPath() == null) return null;
        // 重新读取已授权文件，避免调用方传入未校验的 FileDO 后绕过范围门禁。
        FileDO visible = getOwnedFile(file.getId());
        if (visible == null) return null;
        FileClient client = fileConfigService.getFileClient(visible.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", visible.getConfigId());
        return client.getPresignedDownloadUrl(visible.getPath(), 600);
    }

    private static LoginUser requireOwnedImageActor() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getId() <= 0
                || user.getTenantId() == null || user.getTenantId() <= 0) {
            throw new ServiceException(401, "请先登录");
        }
        if (!user.getTenantId().equals(TenantContextHolder.getTenantId())) {
            throw new ServiceException(403, "租户身份不一致");
        }
        return user;
    }

    @Override
    @SneakyThrows
    public String createFile(String name, String path, byte[] content) {
        // 计算默认的 path 名
        String type = FileTypeUtils.getMineType(content, name);
        if (StrUtil.isEmpty(path)) {
            path = FileUtils.generatePath(content, name);
        }
        // 如果 name 为空，则使用 path 填充
        if (StrUtil.isEmpty(name)) {
            name = path;
        }

        // 上传到文件存储器
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        String url = client.upload(content, path, type);

        // 保存到数据库
        FileDO file = new FileDO();
        file.setConfigId(client.getId());
        file.setName(name);
        file.setPath(path);
        file.setUrl(url);
        file.setType(type);
        file.setSize(content.length);
        fileMapper.insert(file);
        return url;
    }

    @Override
    public Long createFile(FileCreateReqVO createReqVO) {
        FileDO file = BeanUtils.toBean(createReqVO, FileDO.class);
        fileMapper.insert(file);
        return file.getId();
    }

    @Override
    public void deleteFile(Long id) throws Exception {
        // 校验存在
        FileDO file = validateFileExists(id);

        // 从文件存储器中删除
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", file.getConfigId());
        client.delete(file.getPath());

        // 删除记录
        fileMapper.deleteById(id);
    }

    private FileDO validateFileExists(Long id) {
        FileDO fileDO = fileMapper.selectById(id);
        if (fileDO == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        return fileDO;
    }

    @Override
    public byte[] getFileContent(Long configId, String path) throws Exception {
        // 历史下载路径允许匿名访问，新归属图片则必须复核身份，避免该路径绕过私有文件读取限制。
        FileDO stored = fileMapper.selectOne(FileDO::getConfigId, configId, FileDO::getPath, path);
        if (stored != null && stored.getVisibilityScope() != null && !"LEGACY".equals(stored.getVisibilityScope())
                && getOwnedFile(stored.getId()) == null) {
            throw new ServiceException(403, "无权读取图片文件");
        }
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, "客户端({}) 不能为空", configId);
        return client.getContent(path);
    }

    @Override
    public FilePresignedUrlRespVO getFilePresignedUrl(String path) throws Exception {
        FileClient fileClient = fileConfigService.getMasterFileClient();
        FilePresignedUrlRespDTO presignedObjectUrl = fileClient.getPresignedObjectUrl(path);
        return BeanUtils.toBean(presignedObjectUrl, FilePresignedUrlRespVO.class,
                object -> object.setConfigId(fileClient.getId()));
    }

}
