package cn.iocoder.jtyf.framework.core.utils;

import cn.iocoder.jtyf.framework.common.exception.ServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @desc 获取 Windows 系统硬件标识工具类（使用 PowerShell 替代 wmic）
 * @author xwd
 * @version 1.0
 * @date 2025-04-22
 */
@Slf4j
public class WinDmcUtils {

    public static String getCpuId() {
        String psCmd = "powershell -Command \"(Get-CimInstance Win32_Processor).ProcessorId\"";
        return execPowerShell(psCmd);
    }

    public static String getSystemUuid() {
        String psCmd = "powershell -Command \"(Get-CimInstance Win32_ComputerSystemProduct).UUID\"";
        return execPowerShell(psCmd);
    }

    public static String getMainBoardId() {
        String psCmd = "powershell -Command \"(Get-CimInstance Win32_BaseBoard).SerialNumber\"";
        return execPowerShell(psCmd);
    }

    public static void killJavaProcess() {
        String processInfo = getJavaProcessInfo();
        if (StringUtils.isNotBlank(processInfo)) {
            String[] parts = processInfo.split("\\s+");
            if (parts.length > 1) {
                String pid = parts[1]; // 第二列是 PID
                try {
                    Process p = Runtime.getRuntime().exec("taskkill /F /PID " + pid);
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
        String cmd = "tasklist /v | findstr jtyf-module-3d";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"cmd", "/c", cmd});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.isNotBlank(line)) {
                        return line.trim();
                    }
                }
            }
        } catch (IOException e) {
            log.error("查询进程出现错误：", e);
            throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "查询进程出现错误");
        }
        return "";
    }

    private static String execPowerShell(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.isNotBlank(line)) {
                        log.info("系统信息：{}",line.trim());
                        return line.trim();
                    }
                }
            }
        } catch (IOException e) {
            log.error("执行 PowerShell 命令失败 [{}]：", command, e);
            throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "获取硬件信息失败");
        }
        return "";
    }
}
