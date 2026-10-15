package cn.iocoder.jtyf.module.dimensions.utils;


import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Slf4j
public class HttpUtils {
    public static HttpEntity uploadFile(MultipartFile uploadFile, String requestUrl) throws IOException {
        //获取文件后缀
        String originalFilename = uploadFile.getOriginalFilename();
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));

        //注意下面的 特别注意！！！创建临时文件
        File file = File.createTempFile(originalFilename, suffix);
        uploadFile.transferTo(file);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             FileInputStream fileInputStream = new FileInputStream(file);
             InputStream inputStream = new BufferedInputStream(fileInputStream)) {

            // 把文件加到HTTP的post请求中
            HttpEntity multipart = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.RFC6532)
                    .addBinaryBody("files", inputStream, ContentType.MULTIPART_FORM_DATA, file.getName()).build();

            HttpPost httpPost = new HttpPost(requestUrl);
            httpPost.setEntity(multipart);
            CloseableHttpResponse response = httpClient.execute(httpPost);
            return response.getEntity();
        } finally {
            if (file.exists() && !file.delete()) {
                log.warn("临时文件删除失败: {}", file.getAbsolutePath());
            }
        }
    }

    public static HttpEntity updateResource(String url) throws IOException {
        //创建CloseableHttpClient
        try(CloseableHttpClient httpClient = HttpClients.custom().build()) {
            //请求方式 接收请求返回对象
            HttpEntity entity = httpClient.execute(new HttpGet(url)).getEntity();
            //使用EntityUtils 把httpEntity请求返回流读取成字符串
            String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            log.info(result);
            EntityUtils.consume(entity);
            return entity;
        }
    }
}
