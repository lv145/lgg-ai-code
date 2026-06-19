package com.lgg.lggaicode.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * npx serve 部署服务类
 */
//@Service
public class ServeDeployService {
    private static final String CODE_BASE_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";
    public static final int SERVE_PORT = 3000;
    private static Process serverProcess;

    /**
     * 启动服务
     */
    public static void startServerService() {
        try {
            if(serverProcess == null||!serverProcess.isAlive()) {
                ProcessBuilder pb = new ProcessBuilder(
                        "npx.cmd","serve",CODE_BASE_DIR,"-p",String.valueOf(SERVE_PORT)
                );
                pb.redirectErrorStream(true);
                serverProcess = pb.start();
                System.out.println("Server Service started on port: " + SERVE_PORT);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to start server Service", e);
        }
    }
    /**
     * 关闭服务
     */
    public static void stopServerService() {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                // 等待服务停止
                serverProcess.waitFor(5, TimeUnit.SECONDS);
                System.out.println("Server Service stopped");
            } catch (InterruptedException e) {
                // 超时未停止，强制销毁
                serverProcess.destroyForcibly();
            }
        }
    }

}