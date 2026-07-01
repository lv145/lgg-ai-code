package com.lgg.lggaicode.service;

import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * 代码生成类型路由服务
 */
public interface AiCodeGenTypeRoutingService {
    /**
     * 路由代码生成类型
     *
     * @param userPrompt 用户提示
     * @return 代码生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
