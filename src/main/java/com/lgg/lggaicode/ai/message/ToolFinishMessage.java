package com.lgg.lggaicode.ai.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolFinishMessage extends StreamMessage {
    private String id;
    private String name;
    private String arguments;
    private String result;
    public ToolFinishMessage(ToolExecutionRequest req, String resultText) {
        super("tool_finish");
        this.id = req.id();
        this.name = req.name();
        this.arguments = req.arguments();
        this.result = resultText;
    }
}