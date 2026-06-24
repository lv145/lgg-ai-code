package com.lgg.lggaicode.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lgg.lggaicode.common.PageRequest;
import com.lgg.lggaicode.service.AiCodeGeneratorService;
import com.lgg.lggaicode.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
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
    private StreamingChatModel streamingChatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;


    public AiCodeGeneratorService createAiCodeGeneratorService(Long appId) {
       log.info("为appId:{}构建新的AI服务实例",appId);
        //根据appId构建独立的对话记忆
        MessageWindowChatMemory chatMemory=MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        //从数据库加载历史对话打记忆中
        chatHistoryService.loadChatHistoryToMemory(appId,chatMemory,20);
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .build();
       }

       private final Cache<Long, AiCodeGeneratorService> serviceCache= Caffeine.newBuilder()
               .maximumSize(1000)
               .expireAfterWrite(Duration.ofMillis(30))
               .expireAfterAccess(Duration.ofMillis(10))
               .removalListener((key, value, cause) -> {
                  log.debug("AI服务实例被移除,appId:{},原因:{}",key,cause);
               })
               .build();


    /**
     * 根据appId获取AI服务实例(带缓存)
     * @return
     */
       public AiCodeGeneratorService getAiCodeGeneratorService(Long appId) {
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
       }

}
