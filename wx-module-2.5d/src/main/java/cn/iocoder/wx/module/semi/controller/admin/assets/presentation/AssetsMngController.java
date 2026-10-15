package cn.iocoder.wx.module.semi.controller.admin.assets.presentation;

import cn.iocoder.wx.module.semi.common.util.*;
import cn.iocoder.wx.module.semi.controller.admin.assets.presentation.form.AssetsReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.assets.presentation.model.AssetsResInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "api")
public class AssetsMngController {

    private static final String FUNC_NM = "资源管理机能";

    @Value("${app_web_assets_root}")
    private String APP_WEB_ASSETS_ROOT;

    /**
     * 获取资源目录列表
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/assets/folders", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:assets:folders')")
    public String folders(HttpServletRequest req, HttpServletResponse res) throws Exception {
        outputAssets(req, res, true);
        return null;
    }

    /**
     * 获取资源文件列表
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/assets/files", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:assets:files')")
    public String files(HttpServletRequest req, HttpServletResponse res) throws Exception {
        outputAssets(req, res, false);
        return null;
    }

    /**
     * 获取企业服务URL配置文件内容
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/enterprise", method = RequestMethod.GET)
    //@PreAuthorize("@ss.hasPermission('semi:assets:content')")
    public String assets(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "获取企业服务URL配置文件内容", null);
        InputStream is =this.getClass().getClassLoader().getResourceAsStream("enterprise.json");
        ApiUtil.setJsonResponse(res, convertInputStreamToString(is));
        return null;
    }

    private void outputAssets(HttpServletRequest req, HttpServletResponse res, boolean folders) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        if (folders) {
            ApiUtil.callApiLog(req, FUNC_NM, "获取资源目录列表", reqBody);
        } else {
            ApiUtil.callApiLog(req, FUNC_NM, "获取资源文件列表", reqBody);
        }

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return;
        }

        AssetsReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AssetsReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return;
        }

        if (StrUtil.isEmpty(reqInfo.getPath())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "path"
            }, true);
            return;
        }

        String path = reqInfo.getPath();
        String root = APP_WEB_ASSETS_ROOT;//AssetsConfParser.getInstance().getAppWebAssetsRoot();

        if (!root.endsWith("/")) {
            root = root + "/";
        }
        if (path.startsWith("/")) {
            path = path.substring(1, path.length());
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }

        List<String> assets = null;
        if (folders) {
            // 获取资源目录列表
            assets = FileUtil.listSubdir(root, path, false);
        } else {
            // 获取资源文件列表
            assets = FileUtil.listSubfile(root, path, true);
        }

        if (assets == null) {
            assets = new ArrayList<String>();
        }

        AssetsResInfo assetsInfo = new AssetsResInfo();
        assetsInfo.setTotal(assets.size());
        assetsInfo.setList(assets);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(assetsInfo));
    }

    public static String convertInputStreamToString(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        }
        return stringBuilder.toString();
    }

}
