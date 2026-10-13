package com.lgg.lggaicode.langgraph4j.state;

import com.lgg.lggaicode.langgraph4j.model.QualityResult;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.javassist.compiler.CodeGen;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowContext implements Serializable {
    /**
     * WorkflowContext 在MessagesState中此存储的key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /**
     * 应用 ID
     */
    private Long appId;
    /**
     * 用户 ID
     */
    private Long userId;
    /**
     * 父消息 ID
     */
    private Long parentId;
    /**
     * 本次流式生成请求 ID，用于工作流节点中判断是否被用户中止。
     */
    private String requestId;


    /**
     * 当前步骤
     */
    private String currentStep;
    /**
     * 原始prompt
     */
    private String originalPrompt;
    /**
     * 图片资源字符串
     */
    private String imageListStr;
    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;
    /**
     * 增强prompt
     */
    private  String enhancedPrompt;
    /**
     * 代码生成类型枚举
     */
    private CodeGenTypeEnum codeGenTypeEnum;
    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;
    /**
     * 构建成功的目录
     */
    private String buildResultDir;
    /**
     * 错误信息
     */
    private  String errorMessage;

    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;

    @Serial // 序列化版本号
    private static final long serialVersionUID = 1L;
    /**
     * 从MessagesState中获取WorkflowContext
     * @param state MessagesState
     * @return WorkflowContext
     */
    public  static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }
    /**
     * 保存WorkflowContext到MessagesState
     * @param workflowContext WorkflowContext
     * @return Map<String,Object>
     */
    public  static Map<String,Object> saveContext(WorkflowContext workflowContext) {
        return Map.of(WORKFLOW_CONTEXT_KEY,workflowContext);
    }


}
