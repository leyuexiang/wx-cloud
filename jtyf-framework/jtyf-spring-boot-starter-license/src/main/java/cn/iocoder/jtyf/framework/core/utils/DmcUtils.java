package cn.iocoder.jtyf.framework.core.utils;

import cn.iocoder.jtyf.framework.common.exception.ServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @desc 获取linux服务器cpu序列号
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 */
@Slf4j
public class DmcUtils {
    public static String getCpuId() {
        String result = "";
        String cmd = "dmidecode -t 4 | grep ID |sort -u |awk -F': ' '{print $2}'";
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });// 管道
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = reader.readLine().trim();
            if(StringUtils.isNotBlank(line)){
                result  = line;
            }
            reader.close();
            return result.trim();

        } catch (IOException e) {
            log.error("获取服务器cpu id错误：", e);
            throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "获取服务器cpuId失败");
        }
    }

    public static String getSystemUuid() {
        String result = "";
        String cmd = "dmidecode -s system-uuid | tr 'A-Z' 'a-z'";
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });// 管道
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine().trim();
            if(StringUtils.isNotBlank(line)){
                result  = line;
            }
            reader.close();
            return result.trim();

        } catch (IOException e) {
            log.error("获取系统uuid错误：", e);
            throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "获取系统uuid失败");
        }
    }

    public static String getMainBordId() {
        String result = "";
        String cmd = "lshw -class system | grep serial: | awk '{print $2}'";
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });// 管道
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine().trim();
            if(StringUtils.isNotBlank(line)){
                result  = line;
            }
            reader.close();
            return result.trim();

        } catch (IOException e) {
            log.error("获取主板id错误：", e);
            throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "获取主板id失败");
        }
    }

    public static void killJavaProcess() {
        String processInfo = getJavaProcessInfo();
        if (StringUtils.isNotBlank(processInfo)) {
            String[] parts = processInfo.split("\\s+");
            if (parts.length > 1) {
                String pid = parts[1];
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "kill -9 " + pid});
                    int exitCode = p.waitFor();
                    if (exitCode == 0) {
                        log.info("成功杀掉进程，PID: {}", pid);
                    } else {
                        log.error("杀掉进程失败，PID: {}, 退出码: {}", pid, exitCode);
                    }
                } catch (IOException | InterruptedException e) {
                    log.error("杀掉进程出现错误：", e);
                    throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "杀掉进程出现错误");
                }
            }
        } else {
            log.info("未找到以 jtyf-module-3d 开头的 Java 进程");
        }
    }

    private static String getJavaProcessInfo() {
        String result = "";
        String cmd = "ps -ef | grep jtyf-module-3d | grep -v grep";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.isNotBlank(line)) {
                    result = line;
                    break;
                }
            }
            reader.close();
            return result.trim();
        } catch (IOException e) {
            log.error("查询进程出现错误：", e);
            throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "查询进程出现错误");
        }
    }
}
