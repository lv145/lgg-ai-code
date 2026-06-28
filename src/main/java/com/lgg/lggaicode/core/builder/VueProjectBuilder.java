package com.lgg.lggaicode.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VueProjectBuilder {

    public  void buildProjectAsync(String projectPath) {
        //在单独的线程中执行构建操作,避免阻塞主线程
        Thread.ofVirtual().name("vue-builder-"+System.currentTimeMillis()).start(
                () -> {
                    try {
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建Vue项目发生异常:{}", e.getMessage(), e);
                    }

                });
    }


    /**
     * 执行命令
     * @param workingDir
     * @param command
     * @param timeoutSeconds
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
       try {
           log.info("在目录{}中执行命令{}", workingDir, command);
           Process process= RuntimeUtil.exec(
                   null,
                   workingDir,
                   command.split("\\s+")
           );
           boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
           if (!finished) {
               log.error("命令{}执行超时{}秒,强制终止进程", command, timeoutSeconds);
               process.destroyForcibly();
               return false;
           }

           int exitCode = process.exitValue();
           if (exitCode == 0) {
               log.info("命令执行成功:{}", command);
               return true;
           }else {
               log.error("命令执行失败:{}", command);
               return false;
           }

       } catch (InterruptedException e) {
           log.error("命令执行失败:{},错误信息:{}", command, e.getMessage());
           return false;
       }
    }
    private boolean executeNpmInstall(File workingDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install",buildCommand("npm"));
        return executeCommand(workingDir, command, 300);
    }
    private boolean executeNpmBuild(File workingDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build",buildCommand("npm"));
       return executeCommand(workingDir, command, 300);
    }
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
    private String buildCommand(String baseCommand) {
        return  isWindows()?baseCommand+".cmd":baseCommand;
    }
    public  boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists()||!projectDir.isDirectory()) {
            log.error("项目目录不存在:{}", projectPath);
            return false;
        }
        //检查package.json是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("项目目录{}中不存在package.json文件", projectPath);
            return false;
        }
        log.info("开始构建vue项目{}", projectPath);
        //执行npm install
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install失败");
            return false;
        }
        //执行npm run build
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build失败");
            return false;
        }
        //验证dist目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()||!distDir.isDirectory()) {
            log.error("项目目录{}中不存在dist目录:{}",projectPath, distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue项目构建成功,dist目录路径:{}", distDir.getAbsolutePath());
        return true;
    }
}
