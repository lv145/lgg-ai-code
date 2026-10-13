package com.lgg.lggaicode;

import com.lgg.lggaicode.ai.tools.BaseTool;
import com.lgg.lggaicode.ai.tools.FileDirReadTool;
import com.lgg.lggaicode.ai.tools.ToolManager;
import com.lgg.lggaicode.manager.CosManager;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;

@SpringBootTest
public class ToolsTest {
    @Autowired
    private ToolManager toolManager;
    @Resource
    private CosManager cosManager;

    @Resource
    private ResourceLoader resourceLoader;

    @Test
    public void testCosManager() throws IOException {
        File file = resourceLoader.getResource("classpath:logo.png").getFile();
        String url = cosManager.uploadFile("/logo/logo.png", file);
        System.out.println(url);

    }

    @Test
    public void test() {
        FileDirReadTool readTool=new FileDirReadTool();
        String s = readTool.readDir(null, 1L);
        System.out.println(s);


    }
}
