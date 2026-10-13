package com.lgg.lggaicode.service;

import com.lgg.lggaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.mybatisflex.core.service.IService;
import com.lgg.lggaicode.model.entity.ChatHistoryOriginal;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
public interface ChatHistoryOriginalService extends IService<ChatHistoryOriginal> {
    /**
     * 添加原始对话历史消息
     * @param appId 应用id
     * @param message 消息内容
     * @param messageType 消息类型 user/ai/toolExecutionRequest/toolExecutionResult
     * @param userId 创建用户id
     * @return 是否添加成功
     */
    boolean addOriginalChatMessage(Long appId, String message, ChatHistoryMessageTypeEnum messageType, Long userId);

    /**
     * 批量添加原始对话历史消息
     * @param messages 消息列表
     * @return 是否添加成功
     */
    boolean addOriginalChatMessageBatch(List<ChatHistoryOriginal> messages);


    /**
     * 删除原始对话历史消息
     * @param appId 应用id
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 加载原始对话历史消息到内存
     * @param appId 应用id
     * @param chatMemory 内存存储器
     * @param maxSize 最大加载数量
     * @return 加载的消息数量
     */
    int loadOriginalChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxSize);

}
