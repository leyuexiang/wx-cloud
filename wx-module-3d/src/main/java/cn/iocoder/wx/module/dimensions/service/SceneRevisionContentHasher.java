package cn.iocoder.wx.module.dimensions.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 场景修订四包正文摘要工具。
 *
 * <p>摘要输入使用固定版本、结构版本、四包数量、每包字节长度和 UTF-8 原始字节，
 * 避免简单字符串拼接造成的分包边界碰撞。这里不改写 JSON 空白或字段顺序，
 * 因为兼容期要求共享正文与修订行逐包字节一致。</p>
 */
public final class SceneRevisionContentHasher {
    public static final int PACKAGE_COUNT = 4;
    private static final int ENCODING_VERSION = 1;

    private SceneRevisionContentHasher() {
    }

    /** 返回四包正文按 UTF-8 编码后的总字节数。 */
    public static long contentBytes(String sceneJson, String threeDJson, String twoDJson, String dependenciesJson) {
        long total = 0;
        for (String value : packages(sceneJson, threeDJson, twoDJson, dependenciesJson)) {
            total += value.getBytes(StandardCharsets.UTF_8).length;
        }
        return total;
    }

    /** 返回包含结构版本和四包分包边界的稳定小写 SHA-256 摘要。 */
    public static String sha256(int schemaVersion, String sceneJson, String threeDJson,
                                String twoDJson, String dependenciesJson) {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("结构版本必须为正数");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(ENCODING_VERSION);
                output.writeInt(schemaVersion);
                output.writeInt(PACKAGE_COUNT);
                for (String value : packages(sceneJson, threeDJson, twoDJson, dependenciesJson)) {
                    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(encoded.length);
                    output.write(encoded);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("场景修订正文摘要计算失败", error);
        }
    }

    private static String[] packages(String sceneJson, String threeDJson, String twoDJson, String dependenciesJson) {
        return new String[]{
                Objects.requireNonNull(sceneJson, "sceneJson不能为空"),
                Objects.requireNonNull(threeDJson, "threeDJson不能为空"),
                Objects.requireNonNull(twoDJson, "twoDJson不能为空"),
                Objects.requireNonNull(dependenciesJson, "dependenciesJson不能为空")
        };
    }
}
