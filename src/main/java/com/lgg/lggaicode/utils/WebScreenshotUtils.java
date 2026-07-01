package com.lgg.lggaicode.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.model.entity.App;
import com.mybatisflex.core.util.StringUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

@Slf4j
public class WebScreenshotUtils {

    public static final ThreadLocal<WebDriver> webDriverThreadLocal = new ThreadLocal<>();
    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;
    private static WebDriver getWebDriver() {
        WebDriver webDriver = webDriverThreadLocal.get();
        if (webDriver == null) {
            webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            webDriverThreadLocal.set(webDriver);
        }
        return webDriver;
    }


    @PreDestroy
    public void destroy() {
        WebDriver webDriver = webDriverThreadLocal.get();
        if (webDriver != null) {
            webDriver.quit();
        }
    }
    public static String saveWebPageScreenshot(String webUrl){
        WebDriver webDriver = getWebDriver();
        if (StrUtil.isBlank(webUrl)){
            log.error("网页URL不能为空");
            return null;
        }
        try {
            //创建来临时目录
            String rootPath = System.getProperty("user.dir")
                    + File.separator + "tmp"
                    + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            final String IMAGE_SUFFIX = ".png";
//            原始截图文件路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
//            访问网页
            webDriver.get(webUrl);
            //等待页面加载完成
            waitForPageLoad(webDriver);
            //截图
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            //保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图已保存到: {}", imageSavePath);
            //压缩图片
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩截图已保存到: {}", compressedImagePath);
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        }catch (Exception e){
            log.error("保存网页截图时出现异常", e);
            return null;
        }

    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 自动管理 EdgeDriver
//            WebDriverManager.edgedriver().setup();
            // 配置 Edge 选项
            EdgeOptions options = new EdgeOptions();
            // 无头模式
            options.addArguments("--headless");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            // 1、指定本机Edge浏览器本体路径（Windows默认路径）
            File edgeExe = new File("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
            if (!edgeExe.exists()) {
                edgeExe = new File(System.getProperty("user.home") + "\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe");
            }
            options.setBinary(edgeExe);

            // 2、本地驱动文件，完全不走任何网络
            File driverFile = new File("src/main/resources/driver/msedgedriver.exe");
            System.setProperty("webdriver.edge.driver", driverFile.getAbsolutePath());
            // 创建驱动
            WebDriver driver = new EdgeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }
    /**
     * 保存图片到文件
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }
    /**
     * 压缩图片
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
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

    /**
     * 清理本地临时文件
     */
    public static void cleanupTempFiles() {
        String tempDirPath = System.getProperty("user.dir")+File.separator+"tmp";
        File tempDir = new File(tempDirPath);
        if (tempDir.exists()&&tempDir.isDirectory()) {
            FileUtil.clean(tempDir);
        }
    }
}
