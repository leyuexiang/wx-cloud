package cn.iocoder.jtyf.module.semi.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HttpUtil {

    public static byte[] forward(HttpServletRequest req, String method, String url, Map<String, String> addHeaderMap) throws Exception {
        // header
        Map<String, String> headerMap = getHeader(req, addHeaderMap);

        // body
        String body = getBody(req);

        return send(method, url, headerMap, body.toString());
    }

    public static byte[] send(String method, String url, Map<String, String> headerMap, String body) throws Exception {
        byte[] resBody = null;

        URL reqUrl = new URL(url);
        HttpURLConnection con = null;
        InputStream in = null;
        OutputStream out = null;

        try {
            con = (HttpURLConnection) reqUrl.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod(method);
            if (headerMap != null && headerMap.size() > 0) {
                for (Map.Entry<String, String> header : headerMap.entrySet()) {
                    con.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            if (StrUtil.isNotEmpty(body)) {
                try {
                    out = con.getOutputStream();
                    byte[] reqBody = body.getBytes("UTF-8");
                    out.write(reqBody, 0, reqBody.length);
                    out.flush();
                } finally {
                    if (out != null) {
                        out.close();
                    }
                }
            }

            con.connect();

            in = con.getInputStream();
            resBody = in.readAllBytes();
        } finally {
            if (in != null) {
                in.close();
            }
            if (con != null) {
                con.disconnect();
            }
        }

        return resBody;
    }

    public static Map<String, String> getHeader(HttpServletRequest req, Map<String, String> addHeaderMap) {
        Map<String, String> headerMap = new HashMap<String, String>();
        Iterator<String> iterator = req.getHeaderNames().asIterator();
        while (iterator.hasNext()) {
            String headerName = iterator.next();
            headerMap.put(headerName, req.getHeader(headerName));
        }
        if (addHeaderMap != null && addHeaderMap.size() > 0) {
            for (String key : addHeaderMap.keySet()) {
                headerMap.put(key, addHeaderMap.get(key));
            }
        }
        return headerMap;
    }

    public static String getBody(HttpServletRequest req) throws Exception {
        StringBuilder body = new StringBuilder();
        String line = null;
        BufferedReader reader = null;
        try {
            reader = req.getReader();
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return body.toString();
    }
}