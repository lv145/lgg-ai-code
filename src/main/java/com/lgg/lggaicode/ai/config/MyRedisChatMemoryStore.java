package com.lgg.lggaicode.ai.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义LangChain4j对话记忆Redis存储
 * 使用Spring StringRedisTemplate，不依赖JedisPool（避免高版本Jedis过时警告）
 * 使用普通Redis String，不需要RedisJSON模块
 * 会话记录存完整JSON字符串，自动TTL过期，防止Redis内存无限膨胀
 */
@Component
public class MyRedisChatMemoryStore implements ChatMemoryStore {

    /**
     * Spring自动注入，复用application.yml中redis配置，连接池由Spring维护
     * 底层可以是Lettuce(默认) / Jedis，代码不用改动
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Redis key前缀，隔离业务，防止key冲突
     */
    private static final String KEY_PREFIX = "langchain4j:chat:mem:";

    /**
     * 会话过期时间，7天；单位Duration
     */
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    /**
     * 组装Redis完整key
     * @param memoryId 会话唯一ID
     * @return redis key
     */
    private String toRedisKey(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }

    /**
     * 读取会话历史消息
     * @param memoryId 会话ID
     * @return 消息列表，无记录返回空集合，不会返回null
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = toRedisKey(memoryId);
        String json = stringRedisTemplate.opsForValue().get(key);
        // json为null代表该会话没有历史记录，返回空ArrayList
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    /**
     * 更新/覆盖会话全部对话历史
     * @param memoryId 会话ID
     * @param messages 当前会话全部消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // langchain4j校验消息列表不能空
        ValidationUtils.ensureNotEmpty(messages, "messages");

        String key = toRedisKey(memoryId);
        // 使用框架自带序列化工具，消息列表转JSON字符串
        String json = ChatMessageSerializer.messagesToJson(messages);

        // set同时设置过期时间；底层SpringRedisTemplate自动处理网络、连接池
        stringRedisTemplate.opsForValue().set(key, json, SESSION_TTL);
    }

    /**
     * 删除指定会话全部记忆
     * @param memoryId 会话ID
     */
    @Override
    public void deleteMessages(Object memoryId) {
        String key = toRedisKey(memoryId);
        stringRedisTemplate.delete(key);
    }
}
