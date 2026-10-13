package com.lgg.lggaicode.service;

public interface ScreenshotService  {
    /**
     * 生成并上传网页截图到对象存储
     * @param webUrl
     * @return
     */
    String generateAndUploadScreenshot(String webUrl);

    /**
     * 上传截图到 COS
     * @param localScreenshotPath 本地截图路径
     * @return  对象存储访问 URL,失败返回 null
     */
    String uploadScreenshotToCos(String localScreenshotPath);

    /**
     * 生成截图对象存储键名
     * @param fileName 文件名
     * @return  格式为 "screenshots/2026/01/01/filename.jpg"
     */
    String generateScreenshotKey(String fileName);

    /**
     * 清理本地文件
     * @param localFilePath 本地文件路径
     */
    void cleanupLocalFile(String localFilePath);

}
