package com.lgg.lggaicode.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集AI服务接口
 * 使用AI调用工具收集不同类型的图片资源
 */

public interface ImageCollectionService {
    /**
     * 更具用户提示词手机所需的图片资源
     * AI 会根据需求自主选择调用相应的工具
     * @param userPrompt
     * @return
     */
    @SystemMessage(fromResource = "prompt/image-collection-system-prompt.txt")
    String collectImages(@UserMessage String userPrompt);

}
