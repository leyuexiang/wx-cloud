package cn.iocoder.wx.module.infra.service.file;

import org.apache.tika.Tika;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 服务端对图片字节、类型和 XML 活动内容做独立验证；不信任浏览器 MIME/扩展声明。 */
public final class ImageUploadValidator {

    /** 控制器读取流与服务层内容检查共用同一上限，避免两层限制不一致。 */
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SVG_CHARS = 2 * 1024 * 1024;
    private static final int MAX_SAFE_SVG_DEPTH = 64;
    private static final int MAX_SAFE_SVG_NODES = 20_000;
    private static final long MAX_PIXELS = 16_777_216L; // 限制解码峰值，最大约 4096×4096 像素
    private static final Set<String> ALLOWED_SUFFIXES = Set.of("png", "jpg", "jpeg", "webp", "svg");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern CSS_URL = Pattern.compile("(?i)url\\s*\\(\\s*['\\\"]?(?!#)");
    private static final Pattern BIDI_CONTROLS = Pattern.compile("[\\u202A-\\u202E\\u2066-\\u2069]");

    record CheckedImage(String originalName, String suffix, String mediaType) {}

    private ImageUploadValidator() {}

    static CheckedImage validate(String suppliedName, byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw invalid("图片为空或超过 5 MiB");
        }
        String safeName = safeName(suppliedName);
        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot == safeName.length() - 1) throw invalid("图片文件扩展名无效");
        String suffix = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SUFFIXES.contains(suffix)) throw invalid("图片格式不受支持");

        String detected = new Tika().detect(content, safeName).toLowerCase(Locale.ROOT);
        String expected = switch (suffix) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> throw invalid("图片格式不受支持");
        };
        if (!expected.equals(detected)) throw invalid("文件扩展名与实际图片格式不一致");
        if ("svg".equals(suffix)) validateSvg(content);
        else validateRaster(content, detected);
        return new CheckedImage(safeName, suffix, detected);
    }

    private static String safeName(String value) {
        if (!StringUtils.hasText(value)) throw invalid("图片文件名不能为空");
        String name = value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = BIDI_CONTROLS.matcher(CONTROL.matcher(name).replaceAll("")).replaceAll("").trim();
        if (name.isBlank() || name.length() > 256 || name.equals(".") || name.equals("..")) {
            throw invalid("图片文件名无效");
        }
        return name;
    }

    private static void validateRaster(byte[] content, String mediaType) {
        // WebP 先检查画布头，再由已注册的 ImageIO 解码器验证真实载荷，与其他位图共用尺寸上限。
        if ("image/webp".equals(mediaType)) {
            validateWebpCanvas(content);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) throw invalid("图片数据无法解码");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("图片数据无法解码或缺少格式解码器");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                requireCanvas(width, height);
                if (reader.read(0) == null) throw invalid("图片数据无法解码");
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("图片数据无法解码");
        }
    }

    private static void validateWebpCanvas(byte[] bytes) {
        // 识别有限长度的 VP8X/VP8/VP8L 画布头，拒绝未知 chunk 或损坏/超大图像。
        String chunk = ascii(bytes, 12, 4);
        int width;
        int height;
        if ("VP8X".equals(chunk) && bytes.length >= 30) {
            width = 1 + unsigned24(bytes, 24);
            height = 1 + unsigned24(bytes, 27);
        } else if ("VP8 ".equals(chunk) && bytes.length >= 30 && bytes[23] == (byte) 0x9d
                && bytes[24] == 0x01 && bytes[25] == 0x2a) {
            width = (unsigned(bytes[26]) | (unsigned(bytes[27]) << 8)) & 0x3fff;
            height = (unsigned(bytes[28]) | (unsigned(bytes[29]) << 8)) & 0x3fff;
        } else if ("VP8L".equals(chunk) && bytes.length >= 25 && bytes[20] == 0x2f) {
            int b1 = unsigned(bytes[21]), b2 = unsigned(bytes[22]), b3 = unsigned(bytes[23]), b4 = unsigned(bytes[24]);
            width = 1 + b1 + ((b2 & 0x3f) << 8);
            height = 1 + ((b2 >>> 6) & 0x03) + (b3 << 2) + ((b4 & 0x0f) << 10);
        } else {
            throw invalid("WebP 图片头无效或格式不受支持");
        }
        requireCanvas(width, height);
    }

    private static void validateSvg(byte[] content) {
        try {
            String svg = new String(content, StandardCharsets.UTF_8);
            if (svg.length() > MAX_SVG_CHARS) throw invalid("SVG 文本内容过长");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new IllegalArgumentException("外部实体不允许"); });
            Element root = builder.parse(new org.xml.sax.InputSource(new StringReader(svg))).getDocumentElement();
            if (!"svg".equalsIgnoreCase(root.getLocalName() == null ? root.getNodeName() : root.getLocalName())
                    || !"http://www.w3.org/2000/svg".equals(root.getNamespaceURI())) {
                throw invalid("SVG 根元素或命名空间无效");
            }
            int[] nodeCount = {0};
            validateSvgNode(root, 0, nodeCount);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("SVG 内容无效或包含外部实体");
        }
    }

    private static void validateSvgNode(Element element, int depth, int[] nodeCount) {
        if (depth > MAX_SAFE_SVG_DEPTH) throw invalid("SVG 结构嵌套过深");
        if (++nodeCount[0] > MAX_SAFE_SVG_NODES) throw invalid("SVG 节点数量过多");
        String tag = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
        String normalized = tag.toLowerCase(Locale.ROOT);
        if (Set.of("script", "foreignobject", "iframe", "object", "embed", "audio", "video", "image").contains(normalized)) {
            throw invalid("SVG 含活动或外部内容");
        }
        // 内嵌样式正文同样可能包含外链，检查元素属性之外的 CSS 内容。
        if ("style".equals(normalized)) {
            String css = element.getTextContent().toLowerCase(Locale.ROOT);
            if (CSS_URL.matcher(css).find() || css.contains("@import") || css.contains("javascript:")) {
                throw invalid("SVG 样式包含外部引用");
            }
        }
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName().toLowerCase(Locale.ROOT);
            String value = attribute.getNodeValue().trim();
            String lowerValue = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (name.startsWith("on") || ("href".equals(name) || name.endsWith(":href"))
                    && !value.isEmpty() && !value.startsWith("#") || CSS_URL.matcher(value).find()
                    || lowerValue.contains("javascript:") || lowerValue.contains("data:")
                    || lowerValue.contains("@import")) {
                throw invalid("SVG 含脚本、事件或外部引用");
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) validateSvgNode(childElement, depth + 1, nodeCount);
        }
    }

    private static void requireCanvas(int width, int height) {
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) throw invalid("图片尺寸无效或过大");
    }
    private static int unsigned(byte value) { return value & 0xff; }
    private static int unsigned24(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8) | (unsigned(bytes[offset + 2]) << 16);
    }
    private static String ascii(byte[] bytes, int offset, int count) {
        return new String(bytes, offset, count, StandardCharsets.US_ASCII);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
