package com.lgg.lggaicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.exception.ThrowUtils;
import com.lgg.lggaicode.model.dto.ChatHistoryAppQueryRequest;
import com.lgg.lggaicode.model.dto.ChatHistoryQueryRequest;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lgg.lggaicode.model.entity.ChatHistory;
import com.lgg.lggaicode.mapper.ChatHistoryMapper;
import com.lgg.lggaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.lgg.lggaicode.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 对话历史 服务层实现。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{

    private final ChatMemoryStore chatMemoryStore;

    public ChatHistoryServiceImpl(ChatMemoryStore chatMemoryStore) {
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
    public Long addChatMessage(Long appId, Long userId, String message, ChatHistoryMessageTypeEnum messageTypeEnum, Long parentId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用id无效");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户id无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息不能为空");
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setUserId(userId);
        chatHistory.setMessage(message);
        chatHistory.setMessageType(messageTypeEnum.getValue());
        chatHistory.setParentId(parentId);
        boolean result = this.save(chatHistory);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存对话历史失败");
        return chatHistory.getId();
    }

    @Override
    public QueryWrapper getAppQueryWrapper(ChatHistoryAppQueryRequest chatHistoryAppQueryRequest) {
        if (chatHistoryAppQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long appId = chatHistoryAppQueryRequest.getAppId();
        Integer pageSize = chatHistoryAppQueryRequest.getPageSize();
        LocalDateTime lastCreateTime = chatHistoryAppQueryRequest.getLastCreateTime();
        Long userId = chatHistoryAppQueryRequest.getUserId();
        return QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .lt("createTime", lastCreateTime)
                .orderBy("createTime", false)
                .limit(0, pageSize);
    }

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = chatHistoryQueryRequest.getId();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        String messageType = chatHistoryQueryRequest.getMessageType();
        String message = chatHistoryQueryRequest.getMessage();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();

        if (StrUtil.isNotBlank(messageType)) {
            ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
            ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "消息类型不存在");
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("id", id)
                .eq("appId", appId)
                .eq("userId", userId)
                .eq("messageType", messageType)
                .like("message", message);
        //只使用createTime作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        //排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            queryWrapper.orderBy("createTime", false)
                    .orderBy("id", false);
        }
        return queryWrapper;
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory messageWindowChatMemory, int maxCount) {
        try {
            //直接构造查询条件
            QueryWrapper queryWrapper = QueryWrapper.create().eq("appId", appId)
                    //按创建时间升序排序
            .orderBy(ChatHistory::getCreateTime, false)
            .limit(1, maxCount);//
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollectionUtils.isEmpty(historyList)) {
                return 0;
            }
            historyList= historyList.reversed();
            int loadCount = 0;
            //先清理缓存防止重复加载
            messageWindowChatMemory.clear();
            //加载到内存
            for (ChatHistory chatHistory : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(chatHistory.getMessageType()))
                {
                    messageWindowChatMemory.add(UserMessage.from(chatHistory.getMessage()));
                    loadCount++;
                }else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(chatHistory.getMessageType()))
                {
                    messageWindowChatMemory.add(AiMessage.from(chatHistory.getMessage()));
                    loadCount++;
                }
            }
            log.info("成功为应用:{},加载了{}条对话", appId, loadCount);
            return loadCount;
        }catch (Exception e){
            log.error("为应用:{},加载对话失败", appId, e);
            return 0;
        }

    }

}
