package com.lgg.lggaicode.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
/**
 * 网页截图工具类(chrome‑headless‑shell 131 精简无头浏览器)
 */
public class WebScreenshotUtils {

    public static final int DEFAULT_WIDTH = 1600;
    public static final int DEFAULT_HEIGHT = 900;

    // 服务器本地二进制绝对路径
    @Value("${chrome.path.driver}")
    private   String chromeDriverPath;
    @Value("${chrome.path.shell}")
    private   String chromeShellPath;

    /**
     * 【修改】改用 try-finally 手动关闭driver，兼容所有Selenium版本
     */
    public String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }

        ChromeDriver driver = null;
        try {
            // 每次截图新建浏览器实例
            driver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);

            //创建临时目录
            String rootPath = System.getProperty("user.dir")
                    + File.separator + "tmp"
                    + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            final String IMAGE_SUFFIX = ".png";
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;

            driver.get(webUrl);
            waitForPageLoad(driver);

            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图已保存到: {}", imageSavePath);

            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩截图已保存到: {}", compressedImagePath);

            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("保存网页截图时出现异常", e);
            return null;
        } finally {
            // 【关键】无论成功失败，一定关闭浏览器进程，防止僵尸进程堆积
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.error("关闭ChromeDriver失败", e);
                }
            }
        }
    }

    /**
     * 等待页面加载完成
     */
    private void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    /**
     * 初始化 chrome‑headless‑shell
     * 关键点：
     * 1、删除 WebDriverManager，不联网下载驱动
     * 2、ChromeDriverService 指定本地chromedriver
     * 3、options.setBinary() 指定本地headless‑shell
     * 4、删除 --headless 参数！headless‑shell本身就是无头
     */
    private ChromeDriver initChromeDriver(int width, int height) {
        try {
            File chromeBinary = new File(chromeShellPath);
            File driverBinary = new File(chromeDriverPath);
            if (!chromeBinary.exists() || !driverBinary.exists()) {
                log.error("chrome‑headless‑shell 文件不存在 bin={}, driver={}", chromeShellPath, chromeDriverPath);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "浏览器二进制文件不存在");
            }

            ChromeDriverService service = new ChromeDriverService.Builder()
                    .usingDriverExecutable(driverBinary)
                    .build();

            ChromeOptions options = new ChromeOptions();
            options.setBinary(chromeBinary);

            // ❗重要：不要加 --headless / --headless=new，chrome‑headless‑shell 原生无头
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-gpu");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-background-networking");
            options.addArguments("--disable-sync");
            options.addArguments("--disable-default-apps");
            options.addArguments("--mute-audio");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            ChromeDriver driver = new ChromeDriver(service, options);
            driver.manage().timeouts()
                    .pageLoadTimeout(Duration.ofSeconds(30))
                    .scriptTimeout(Duration.ofSeconds(15))
                    .implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome headless‑shell 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    private void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    private void compressImage(String originalImagePath, String compressedImagePath) {
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    public void cleanupTempFiles() {
        String tempDirPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots";
        File tempDir = new File(tempDirPath);
        if (tempDir.exists() && tempDir.isDirectory()) {
            FileUtil.clean(tempDir);
        }
    }
}
