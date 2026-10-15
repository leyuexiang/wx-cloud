package cn.iocoder.jtyf.module.semi.common.conf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;
import cn.iocoder.jtyf.module.semi.common.businessinfo.ApiAuthInfo;
import cn.iocoder.jtyf.module.semi.common.util.StrUtil;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;


public class AuthXmlPaser {

    private static CmnLogger logger = CmnLogger.getLogger(AuthXmlPaser.class);

    /**
     * 解析权限XML文件
     *
     * @return 权限Map
     * @throws 异常
     */
    public static Map<String, ApiAuthInfo> parse(String authFilePath) throws DocumentException {
        Map<String, ApiAuthInfo> authMap = new LinkedHashMap<String, ApiAuthInfo>();

        // 解析auth.xml文件内容
        Document document = new SAXReader().read(AuthXmlPaser.class.getClassLoader().getResourceAsStream("auth.xml"));
        Element rtEl = document.getRootElement();
        Element gEl = rtEl.element("grant");

        List<?> apiEls = gEl.elements();
        for (Object api : apiEls) {
            Element apiEl = (Element) api;

            String url = getAttrText(apiEl, "url");
            String alias = getAttrText(apiEl, "alias");
            String authCode = getAttrText(apiEl, "authCode");

            if (StrUtil.isEmpty(url)
                    || StrUtil.isEmpty(alias)
                    || StrUtil.isEmpty(authCode)) {
                logger.log(MessageConst.MSG_01000005,
                        new String[] {"Empty attributes" +
                            " at Element:[" + getTagCtx(apiEl) + "]" +
                            " in auth.xml"});
                continue;
            }

            boolean authCdErr = false;
            List<Long> authCdList = new ArrayList<Long>();
            String[] authCdArr = StrUtil.splitAll(authCode, ",");
            for (String authCd : authCdArr) {
                Long lAuthCd = StrUtil.safeToLong(authCd.trim());
                if (lAuthCd == null) {
                    authCdErr = true;
                    break;
                }
                authCdList.add(lAuthCd);
            }

            if (authCdErr || authCdList.isEmpty()) {
                logger.log(MessageConst.MSG_01000005,
                        new String[] {"Invalid attribute:[authCode]" +
                            " at Element:[" + getTagCtx(apiEl) + "]" +
                            " in auth.xml"});
                continue;
            }

            authMap.put(url, new ApiAuthInfo(url, alias, authCdList));
        }

        return authMap;
    }

    /**
     * 取得指定标签的内容
     *
     * @param ele 标签
     * @return 标签内容
     */
    private static String getTagCtx(Element ele) {
        StringBuilder tagCtx = new StringBuilder();
        tagCtx.append("<")
            .append(ele.getQualifiedName())
            .append(" ");
        for (Object o : ele.attributes()) {
            Attribute attr = (Attribute)o;
            tagCtx.append(attr.getName())
                .append("=\"")
                .append(attr.getValue())
                .append("\" ");
        }
        tagCtx.append("/>");
        return tagCtx.toString();
    }

    private static String getAttrText(Element el, String attrNm) {
        Attribute attr = el.attribute(attrNm);
        return attr == null ? null : attr.getText();
    }

}
