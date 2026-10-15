package cn.iocoder.jtyf.module.semi.common.util;

import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.account.conf.AccountConfParser;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;


import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JwtUtil {

    public static final String TOKEN_EXTEND_TYPE_HOURS = "hours";
    public static final String TOKEN_EXTEND_TYPE_REMEMBER = "remember";
    public static final String TOKEN_EXTEND_TYPE_MOBILE = "mobile";

    private static final Map<String, Object> headerMap;
    private static final String PAYLOAD_KEY_ACCOUNT;
    private static final String SECRET;
    private static final Algorithm algorithm;
    private static final JWTVerifier verifier;

    static {
        headerMap = new HashMap<String, Object>();
        headerMap.put("typ", "JWT");
        headerMap.put("alg", "HS256");
        PAYLOAD_KEY_ACCOUNT = "account";
        SECRET = AccountConfParser.getInstance().getJwtSecret();
        algorithm = Algorithm.HMAC256(SECRET);
        verifier = JWT.require(algorithm).build();
    }

    /**
     * 创建令牌
     * 
     * @param accountInfo 账号情报
     * @param expiresHours 过期小时数
     * @return 令牌
     */
    public static String buildToken(AccountInfo accountInfo, long expiresHours){
        long nowTime = new Date().getTime();
        long expTime = nowTime + (1000 * 60 * 60 * expiresHours);
        return JWT.create().withHeader(headerMap)
                           .withJWTId(UUID.randomUUID().toString())
                           .withClaim(PAYLOAD_KEY_ACCOUNT, JsonUtil.toJsonStr(accountInfo))
                           .withIssuedAt(new Date(nowTime))
                           .withExpiresAt(new Date(expTime))
                           .sign(algorithm);
    }

    /**
     * 创建令牌
     * 
     * @param accountInfo 账号情报
     * @param tokenExtendType Token续期类别
     * @return 令牌
     */
    public static String buildToken(AccountInfo accountInfo, String tokenExtendType){
        long expiresHours = getTokenExpiresHours(tokenExtendType);
        return buildToken(accountInfo, expiresHours);
    }

    /**
     * Token续期
     * 
     * @param token 当前Token值
     * @param accountInfo 账号情报
     * @param tokenExtendType Token续期类别
     * @return 新Token值
     */
    public static String extendToken(String token, AccountInfo accountInfo, String tokenExtendType){
        DecodedJWT decoded = verifier.verify(token);
        long expiresSeconds = decoded.getClaim("exp").asLong();

        long tokenValidHours = 0;
        long validSeconds = expiresSeconds - (new Date().getTime() / 1000);
        if (validSeconds > 0) {
            long q = validSeconds / 3600; // 商
            long m = validSeconds % 3600; // 余数
            tokenValidHours = q;
            if (m != 0) {
                tokenValidHours++;
            }
        }

        if (tokenValidHours < 2) {
            return buildToken(accountInfo, tokenExtendType);
        }

        return null;
    }

    /**
     * 验证令牌
     * 
     * @param token 令牌
     * @return 账号情报
     */
    public static AccountInfo verifyToken(String token){
        DecodedJWT decoded = verifier.verify(token);
        return JsonUtil.toObj(decoded.getClaim(PAYLOAD_KEY_ACCOUNT).asString(), AccountInfo.class);
    }

    /**
     * 获取Token有效期小时数
     * 
     * @param tokenExtendType Token续期类别
     * @return Token有效期小时数
     */
    private static long getTokenExpiresHours(String tokenExtendType){
        if (StrUtil.isEmpty(tokenExtendType)) {
            return 16;
        }
        if (tokenExtendType.equals(TOKEN_EXTEND_TYPE_HOURS)) {
            return 16;
        }
        if (tokenExtendType.equals(TOKEN_EXTEND_TYPE_REMEMBER)) {
            return 24 * 365;
        }
        if (tokenExtendType.equals(TOKEN_EXTEND_TYPE_MOBILE)) {
            return 24 * 365 * 7;
        }
        return 16;
    }
}
