package com.lgg.lggaicode.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lgg.lggaicode.ai.tools.FileWriteTool;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.service.AiCodeGeneratorService;
import com.lgg.lggaicode.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 代码代码生成服务工厂
 */
@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {
    @Resource
    private ChatModel chatModel;
    // 流式模型
    @Resource
    private StreamingChatModel openAiStreamingChatModel;
    @Resource
    private StreamingChatModel reasoningStreamingChatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;

    private final Cache<String, AiCodeGeneratorService> serviceCache= Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMillis(30))
            .expireAfterAccess(Duration.ofMillis(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI服务实例被移除,appId:{},原因:{}",key,cause);
            })
            .build();

    public AiCodeGeneratorService createAiCodeGeneratorService(Long appId, CodeGenTypeEnum codeGenType) {
        log.info("为appId:{}构建新的AI服务实例", appId);
        //根据appId构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        //从数据库加载历史对话打记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (codeGenType) {
            case VUE_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
                    // 为每个appId构建独立的对话记忆
                    .chatMemoryProvider(memoryId->chatMemory)
                    // 添加文件写入工具
                    .tools(new FileWriteTool())
                    //配置推理模型
                    .streamingChatModel(reasoningStreamingChatModel)
                    // 配置幻觉工具名称策略
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest,"Error,there is not tool with name:"+toolExecutionRequest.name()
                            )
                    )
                    .build();
            case HTML, MULTI_FILE -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码类型: " + codeGenType);
        };
    }




    /**
     * 根据appId获取AI服务实例(带缓存)
     * @return
     */
       public AiCodeGeneratorService getAiCodeGeneratorService(Long appId) {
        return getAiCodeGeneratorService(appId,CodeGenTypeEnum.HTML);
       }
    /**
     * 根据appId获取AI服务实例(带缓存)
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(Long appId, CodeGenTypeEnum codeGenType) {
        String checkKey =buildCacheKey(appId, codeGenType);
        return serviceCache.get(checkKey,key -> createAiCodeGeneratorService(appId,codeGenType));
    }

       private String buildCacheKey(Long appId, CodeGenTypeEnum codeGenType){
            return appId+"_"+codeGenType;
       }
}
