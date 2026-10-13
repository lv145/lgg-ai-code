package com.lgg.lggaicode.ai.streamhandler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lgg.lggaicode.ai.message.AiResponseMessage;
import com.lgg.lggaicode.ai.message.StreamMessage;
import com.lgg.lggaicode.ai.message.ToolExecutedMessage;
import com.lgg.lggaicode.ai.message.ToolRequestMessage;
import com.lgg.lggaicode.ai.tools.BaseTool;
import com.lgg.lggaicode.ai.tools.ToolManager;
import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.core.builder.VueProjectBuilder;
import com.lgg.lggaicode.model.entity.ChatHistoryOriginal;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.lgg.lggaicode.model.enums.StreamMessageTypeEnum;
import com.lgg.lggaicode.service.ChatHistoryOriginalService;
import com.lgg.lggaicode.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.tools.Tool;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;


    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param userId          用户ID
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               ChatHistoryOriginalService chatHistoryOriginalService,
                               long appId, Long userId,Long parentId) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 收集用于恢复对话记忆的数据
        StringBuilder aiResponseStringBuilder = new StringBuilder();
        // 每个 Flux 流可能包含多条工具调用和 AI_RESPONSE 响应信息，统一收集之后批量入库
        List<ChatHistoryOriginal> originalChatHistoryList = new ArrayList<>();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, aiResponseStringBuilder, originalChatHistoryList, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                            // 工具调用信息入库
                    if (!originalChatHistoryList.isEmpty()) {
                        // 完善 ChatHistoryOriginal 信息
                        originalChatHistoryList.forEach(chatHistory -> {
                            chatHistory.setAppId(appId);
                            chatHistory.setUserId(userId);
                        });
                        // 批量入库
                        chatHistoryOriginalService.addOriginalChatMessageBatch(originalChatHistoryList);
                    }
                    // Ai response 入库(两种情况：1. 没有进行工具调用。2. 工具调用结束之后 AI 一般还会有一句返回)
                    String aiResponseStr = aiResponseStringBuilder.toString();
                    chatHistoryOriginalService.addOriginalChatMessage(appId, aiResponseStr, ChatHistoryMessageTypeEnum.AI, userId);

                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, userId, aiResponse,ChatHistoryMessageTypeEnum.AI, parentId);
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, userId, errorMessage, ChatHistoryMessageTypeEnum.AI, parentId);
                    chatHistoryOriginalService.addOriginalChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI, userId);
                });
    }

    /**
     * 当 `String.format` 解析字符串时，把文本里原生的 `%` 识别为格式化占位符，找不到对应参数直接抛出异常：
     * `IllegalFormatConversionException: Format specifier '%s'`
     * 解决方法：转义百分号，避免String.format解析%为占位符
     * @param text 待处理的字符串
     * @return 转义后的字符串
     */
//    private String escapePercent(String text) {
//        if (StrUtil.isBlank(text)) {
//            return text;
//        }
//        return text.replace("%", "%%");
//    }
    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder,
                                          StringBuilder aiResponseStringBuilder,
                                          List<ChatHistoryOriginal> originalChatHistoryList,Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());

        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                // 对于 AI 响应内容，与展示数据处理逻辑相同
                aiResponseStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();

                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    BaseTool tool = toolManager.getTool(toolRequestMessage.getName());
                    return tool == null ? "" : tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                // 处理工具调用信息
                processToolExecutionMessage(aiResponseStringBuilder, chunk, originalChatHistoryList);

                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolName = toolExecutedMessage.getName();
                JSONObject arguments = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                BaseTool tool = toolManager.getTool(toolName);

                String result = tool.generateToolExecutedResult(arguments);
                chatHistoryStringBuilder.append(result);
                String out = String.format("\n\n%s\n\n", result);
                return out;
                //使用Hutool的StrUtil.format方法SLF4J风格 格式化字符串，避免%被解析为占位符
//                String result = StrUtil.format("""
//                        [工具调用] {}  {}
//                        ```{}
//                        {}
//                        ```
//                        """,toolExecutedMessage.getName(),relativeFilePath, suffix, content);

            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
        /**
         * 解析处理工具调用相关信息
         * @param aiResponseStringBuilder
         * @param chunk
         * @param originalChatHistoryList
         */
        private void processToolExecutionMessage(StringBuilder aiResponseStringBuilder, String chunk, List<ChatHistoryOriginal> originalChatHistoryList) {
            // 解析 chunk
            ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
            // 构造工具调用请求对象(工具调用结果的数据就是从调用请求里拿的，所以直接在这里处理调用请求信息)
            String aiResponseStr = aiResponseStringBuilder.toString();
            ToolRequestMessage toolRequestMessage = new ToolRequestMessage();
            toolRequestMessage.setId(toolExecutedMessage.getId());
            toolRequestMessage.setName(toolExecutedMessage.getName());
            toolRequestMessage.setArguments(toolExecutedMessage.getArguments());
            toolRequestMessage.setText(aiResponseStr);
            // 转换成 JSON
            String toolRequestJsonStr = JSONUtil.toJsonStr(toolRequestMessage);
            // 构造 ChatHistory 存入列表
            ChatHistoryOriginal toolRequestHistory = ChatHistoryOriginal.builder()
                    .message(toolRequestJsonStr)
                    .messageType(ChatHistoryMessageTypeEnum.TOOL_EXECUTION_REQUEST.getValue())
                    .build();
            originalChatHistoryList.add(toolRequestHistory);
            ChatHistoryOriginal toolResultHistory = ChatHistoryOriginal.builder()
                    .message(chunk)
                    .messageType(ChatHistoryMessageTypeEnum.TOOL_EXECUTION_RESULT.getValue())
                    .build();
            originalChatHistoryList.add(toolResultHistory);
            // AI 响应内容暂时结束，置空 aiResponseStringBuilder
            aiResponseStringBuilder.setLength(0);
        }
    }

