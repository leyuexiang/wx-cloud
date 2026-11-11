package cn.iocoder.wx.module.infra.controller.admin.file;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.OwnedFileRespVO;
import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.wx.module.infra.service.file.FileService;
import cn.iocoder.wx.module.infra.service.file.ImageUploadValidator;
import cn.iocoder.wx.module.infra.service.file.OwnedImageUploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static cn.iocoder.wx.framework.common.pojo.CommonResult.success;

/** 普通图片文件公共别名的后台适配；稳定编号与资源目录、ART resourceId 分属不同命名空间。 */
@RestController
@RequestMapping("/infra/file/resource/oss")
@Tag(name = "管理后台 - 有归属图片文件")
public class OwnedImageController {

    @Resource
    private FileService fileService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "上传当前用户有归属的图片文件")
    public CommonResult<OwnedFileRespVO> upload(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > ImageUploadValidator.MAX_BYTES) {
            throw new ServiceException(400, "图片为空或超过 5 MiB");
        }
        // multipart 声明大小不是安全边界；实际流最多读取 5 MiB + 1 字节后拒绝。
        byte[] content;
        try (var input = file.getInputStream();
             var output = new ByteArrayOutputStream(Math.min((int) file.getSize(), 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer, 0,
                    Math.min(buffer.length, ImageUploadValidator.MAX_BYTES + 1 - total))) != -1) {
                total += count;
                if (total > ImageUploadValidator.MAX_BYTES) {
                    throw new ServiceException(400, "图片超过 5 MiB");
                }
                output.write(buffer, 0, count);
            }
            content = output.toByteArray();
        } catch (java.io.IOException ex) {
            throw new ServiceException(400, "读取上传图片失败");
        }
        OwnedImageUploadResult result = fileService.createOwnedImage(file.getOriginalFilename(), content);
        return success(toResponse(result.file(), result.readUrl()));
    }

    @GetMapping("/listByIds/{ids}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "按单个本地普通文件编号读取短期地址")
    public CommonResult<List<OwnedFileRespVO>> listById(@PathVariable("ids") String ids) {
        if (ids == null || !ids.matches("[1-9][0-9]{0,18}")) {
            throw new ServiceException(400, "文件编号格式无效");
        }
        final Long id;
        try {
            id = Long.valueOf(ids);
        } catch (NumberFormatException ex) {
            throw new ServiceException(400, "文件编号格式无效");
        }
        FileDO file = fileService.getOwnedFile(id);
        if (file == null) return success(List.of());
        String readUrl = fileService.getOwnedFileReadUrl(file);
        if (!StringUtils.hasText(readUrl)) return success(List.of());
        return success(List.of(toResponse(file, readUrl)));
    }

    private static OwnedFileRespVO toResponse(FileDO file, String readUrl) {
        OwnedFileRespVO response = new OwnedFileRespVO();
        response.setOssId(String.valueOf(file.getId()));
        response.setUrl(readUrl);
        response.setOriginalName(file.getName());
        String path = file.getPath();
        int dot = path == null ? -1 : path.lastIndexOf('.');
        response.setFileSuffix(dot >= 0 && dot < path.length() - 1
                ? path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "");
        return response;
    }
}
