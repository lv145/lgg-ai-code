package com.lgg.lggaicode.ai.message;

import com.lgg.lggaicode.model.enums.StreamMessageTypeEnum;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import dev.langchain4j.model.chat.response.PartialToolCall;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具调用消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {

    private String id;

    private String name;

    private String arguments;
    private String text;

    public ToolRequestMessage(PartialToolCall toolCallRequest) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.id = toolCallRequest.id();
        this.name = toolCallRequest.name();
        this.arguments = toolCallRequest.partialArguments();

    }
}
