package com.lgg.lggaicode;

import com.lgg.lggaicode.ai.model.HtmlCodeResult;
import com.lgg.lggaicode.ai.model.MultiFileCodeResult;
import com.lgg.lggaicode.service.AiCodeGeneratorService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("做个工作记录小工具");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode("做个留言板");
        Assertions.assertNotNull(multiFileCodeResult);
    }

}
