package com.lgg.lggaicode.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.exception.ThrowUtils;
import com.lgg.lggaicode.manager.CosManager;
import com.lgg.lggaicode.service.ScreenshotService;
import com.lgg.lggaicode.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class ScreenshotServiceImpl implements ScreenshotService {
    @Resource
    private CosManager cosManager;

    /**
     * 生成并上传网页截图到对象存储
     * @param webUrl
     * @return
     */

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "网页Url 不能为空");
        log.info("开始生成截图: {}", webUrl);
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "本地截图失败");
        try {
            //上传到对象存储
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR, "上传到对象存储失败");
            log.info("截图已上传到对象存储: {}", cosUrl);
            return cosUrl;
        }finally {
            //清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }

    }

    @Override
    public String uploadScreenshotToCos(String localScreenshotPath) {
        if(StrUtil.isBlank(localScreenshotPath)){
            log.error("本地截图路径为空");
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if(!screenshotFile.exists()){
            log.error("本地截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        //生成COS对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8)+"_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图对象存储键名
     * @param fileName 文件名
     * @return
     */
    @Override
    public String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     * @param localFilePath 本地文件路径
     */
    @Override
    public void cleanupLocalFile(String localFilePath) {
        File file = new File(localFilePath);
        if (file.exists()) {
            File parentFile = file.getParentFile();
            FileUtil.del(parentFile);
            log.info("本地截图文件已删除: {}", parentFile.getAbsolutePath());
        }

    }
}
