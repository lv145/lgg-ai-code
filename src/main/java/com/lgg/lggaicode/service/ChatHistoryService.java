package com.lgg.lggaicode.service;

import com.lgg.lggaicode.model.dto.ChatHistoryAppQueryRequest;
import com.lgg.lggaicode.model.dto.ChatHistoryQueryRequest;
import com.mybatisflex.core.service.IService;
import com.lgg.lggaicode.model.entity.ChatHistory;
import com.lgg.lggaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.lgg.lggaicode.model.vo.ChatHistoryVO;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 保存对话消息
     */
    Long addChatMessage(Long appId, Long userId, String message, ChatHistoryMessageTypeEnum messageTypeEnum, Long parentId);

    /**
     * 查询某个应用的历史消息
     */
    QueryWrapper getAppQueryWrapper(ChatHistoryAppQueryRequest chatHistoryAppQueryRequest);

    /**
     * 管理员查询历史消息
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 获取对话历史视图对象
     */
    ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory);

    /**
     * 获取对话历史视图对象列表
     */
    List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList);

    /**
     * 校验应用历史查询分页参数
     */
    void validAppPageParams(ChatHistoryAppQueryRequest chatHistoryAppQueryRequest);

}
