package com.lgg.lggaicode.ai.factory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lgg.lggaicode.ai.config.MyRedisChatMemoryStore;
import com.lgg.lggaicode.ai.guardrail.RetryOutputGuardrail;
import com.lgg.lggaicode.ai.tools.*;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.ai.service.AiCodeGeneratorService;
import com.lgg.lggaicode.service.ChatHistoryOriginalService;
import com.lgg.lggaicode.service.ChatHistoryService;
import com.lgg.lggaicode.utils.SpringContextUtil;
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
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;
    @Resource
    private MyRedisChatMemoryStore myRedisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private ToolManager toolManager;
    @Resource
    private ChatHistoryOriginalService chatHistoryOriginalService;


    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache= Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI服务实例被移除,appId:{},原因:{}",key,cause);
            })
            .build();

    public AiCodeGeneratorService createAiCodeGeneratorService(Long appId, CodeGenTypeEnum codeGenType) {
        log.info("为appId:{}构建新的AI服务实例", appId);
        //根据appId构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(myRedisChatMemoryStore)
                .maxMessages(60)// 一次工具调用也算一次记忆，maxMessages得设置得大一点，不然模型会失忆一直循环调用工具
                .build();

        return switch (codeGenType) {
            case VUE_PROJECT -> {
                //使用多例模式的StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                // 从数据库加载历史对话到缓存中，由于多了工具调用相关信息，加载的最大数量稍微多一些
                chatHistoryOriginalService.loadOriginalChatHistoryToMemory(appId, chatMemory, 50);
                yield  AiServices.builder(AiCodeGeneratorService.class)
                        .outputGuardrailClasses(RetryOutputGuardrail.class)
                        .maxSequentialToolsInvocations(20) // 最大并发工具调用次数
                        // 为每个appId构建独立的对话记忆
                        .chatMemoryProvider(memoryId -> chatMemory)
                        // 添加文件写入工具
                        .tools(toolManager.getAllTools())
                        //配置推理模型
                        .streamingChatModel(reasoningStreamingChatModel)
                        // 配置幻觉工具名称策略
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                        toolExecutionRequest, "Error,there is not tool with name:" + toolExecutionRequest.name()
                                )
                        )
                        .build();
            }
            case HTML, MULTI_FILE -> {
                //使用多例模式的StreamingChatModel 解决并发问题
                StreamingChatModel streamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                //从数据库加载历史对话打记忆中
                chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
                yield AiServices.builder(AiCodeGeneratorService.class)
//                        .outputGuardrailClasses(RetryOutputGuardrail.class) // 配置重试输出守卫
                        .maxSequentialToolsInvocations(20) // 最大并发工具调用次数
                        .chatModel(chatModel)
                        .streamingChatModel(streamingChatModel)
                        .chatMemory(chatMemory)
                        .build();
            }

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
