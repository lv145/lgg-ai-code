package com.lgg.lggaicode.service.impl;

import com.lgg.lggaicode.service.ServeDeployService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

//@Component
public class ServerLifecycleManage {
    @Autowired
    private ServeDeployService serveDeployService;

    /**
     * 应用启动完成后启动服务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        System.out.println("Server Service starting...");
        serveDeployService.startServerService();
    }
    /**
     * 应用关闭前关闭服务
     */
    @PreDestroy
    public void onPreDestroy() {
        System.out.println("Server Service stopping...");
        serveDeployService.stopServerService();
    }
}
