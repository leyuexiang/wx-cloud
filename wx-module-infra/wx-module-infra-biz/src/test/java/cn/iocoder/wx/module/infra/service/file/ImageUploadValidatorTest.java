package cn.iocoder.wx.module.infra.service.file;

import cn.hutool.core.io.resource.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** 图片上传拒绝伪造身份和危险内容之前，先锁定服务器可观察到的格式边界。 */
class ImageUploadValidatorTest {

    /** 一像素的有效 WebP，覆盖页面已声明支持的有损及无损格式。 */
    @Test
    void acceptsDecodableWebpAndRejectsTruncatedPayload() {
        byte[] lossy = Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        byte[] lossless = Base64.getDecoder().decode(
                "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        assertEquals("image/webp", ImageUploadValidator.validate("图片.webp", lossy).mediaType());
        assertEquals("webp", ImageUploadValidator.validate("图片.webp", lossless).suffix());
        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadValidator.validate("坏图.webp", Arrays.copyOf(lossy, 30)));
    }

    // 真实 1x1 RGBA PNG 字节夹具（CI 必须以 ImageIO 解码确认完整有效，不能只检查签名）。
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==");

    @Test
    void acceptsDecodablePngAndReturnsCanonicalMetadata() throws Exception {
        try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(ONE_PIXEL_PNG))) {
            assertEquals("png", javax.imageio.ImageIO.getImageReaders(input).next().getFormatName().toLowerCase(java.util.Locale.ROOT));
        }
        var checked = ImageUploadValidator.validate("C:\\fakepath\\图标.PNG", ONE_PIXEL_PNG);
        assertEquals("图标.PNG", checked.originalName());
        assertEquals("png", checked.suffix());
        assertEquals("image/png", checked.mediaType());
    }

    @Test
    void rejectsEmptyOversizedAndExtensionContentMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadValidator.validate("empty.png", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadValidator.validate("huge.png", new byte[5 * 1024 * 1024 + 1]));
        byte[] boundary = new byte[5 * 1024 * 1024];
        System.arraycopy(ONE_PIXEL_PNG, 0, boundary, 0, ONE_PIXEL_PNG.length);
        // 文件尾部附加填充仍是可解码的 PNG，恰好到上限时按有效图片接受。
        assertEquals("png", ImageUploadValidator.validate("boundary.png", boundary).suffix());
        byte[] jpeg = ResourceUtil.readBytes("file/erweima.jpg");
        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadValidator.validate("forged.png", jpeg));
    }

    @Test
    void rejectsBadOriginalNameAndOverlongSvgBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.validate("../bad.svg", "<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("imgnp.png", ImageUploadValidator.validate("im\u202Egnp.png", ONE_PIXEL_PNG).originalName());
        String hugeSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>" + "x".repeat(2 * 1024 * 1024) + "</text></svg>";
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.validate("huge.svg", hugeSvg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsInvalidMagicEvenWhenExtensionAndMimeLookValid() {
        byte[] corrupt = Arrays.copyOf(ONE_PIXEL_PNG, ONE_PIXEL_PNG.length);
        corrupt[0] = 0;
        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadValidator.validate("corrupt.png", corrupt));
    }

    @Test
    void rejectsNonSvgNamespaceAndDisallowedSvgFeatures() {
        String wrongNamespace = "<svg xmlns=\"urn:not-svg\"/>";
        String cssExternal = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect style=\"fill:url(https://evil.example/x)\"/></svg>";
        String cssImport = "<svg xmlns=\"http://www.w3.org/2000/svg\"><style>@import url(https://evil.example/x)</style></svg>";
        String externalEntity = "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg xmlns=\"http://www.w3.org/2000/svg\">&x;</svg>";
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.validate("wrong.svg", wrongNamespace.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.validate("css.svg", cssExternal.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.validate("import.svg", cssImport.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.validate("xxe.svg", externalEntity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsUnsafeSvgXmlAndAcceptsSimpleVector() {
        String safe = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><path d=\"M0 0h10v10z\"/></svg>";
        assertEquals("svg", ImageUploadValidator.validate("shape.svg", safe.getBytes(java.nio.charset.StandardCharsets.UTF_8)).suffix());
        String unsafe = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadValidator.validate("unsafe.svg", unsafe.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String deep = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + "<g>".repeat(65)
                + "<rect/>" + "</g>".repeat(65) + "</svg>";
        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadValidator.validate("deep.svg", deep.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
