package com.lgg.lggaicode.langgraph4j.node;

import com.lgg.lggaicode.ai.cancel.StreamCancelManager;
import com.lgg.lggaicode.ai.streamhandler.StreamHandlerExecutor;
import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.core.AiCodeGeneratorFacade;
import com.lgg.lggaicode.langgraph4j.model.QualityResult;
import com.lgg.lggaicode.langgraph4j.state.WorkflowContext;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.service.ChatHistoryOriginalService;
import com.lgg.lggaicode.service.ChatHistoryService;
import com.lgg.lggaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        StreamHandlerExecutor streamHandlerExecutor = SpringContextUtil.getBean(StreamHandlerExecutor.class);
        ChatHistoryOriginalService chatHistoryOriginalService = SpringContextUtil.getBean(ChatHistoryOriginalService.class);
        ChatHistoryService chatHistoryService = SpringContextUtil.getBean(ChatHistoryService.class);
        StreamCancelManager streamCancelManager = SpringContextUtil.getBean(StreamCancelManager.class);

        return node_async(state -> {

            WorkflowContext context = WorkflowContext.getContext(state);
            String userPrompt = context.getEnhancedPrompt();
            CodeGenTypeEnum codeGenTypeEnum = context.getCodeGenTypeEnum();
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("执行节点: 代码生成");

            // 将 requestId 传入生成链路，用户点击停止时，节点内部的阻塞等待也能尽快结束。
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                    userPrompt,
                    codeGenTypeEnum,
                    context.getAppId(),
                    context.getUserId(),
                    context.getRequestId()
            );

            Flux<String> stringFlux = streamHandlerExecutor.doExecute(
                    codeStream,
                    chatHistoryService,
                    chatHistoryOriginalService,
                    context.getAppId(),
                    context.getUserId(),
                    context.getCodeGenTypeEnum(),
                    context.getParentId()
            ).takeUntilOther(
                    streamCancelManager.cancelSignal(
                            context.getRequestId(),
                            context.getAppId(),
                            context.getUserId()
                    )
            );
            //同步等待流式输出完成
            stringFlux.blockLast(Duration.ofMinutes(10));//最多等待10分钟

            if (streamCancelManager.isCancelled(context.getRequestId(), context.getAppId(), context.getUserId())) {
                // 已中止时不再写“生成完成”的上下文，外层工作流会检测取消信号并结束。
                context.setCurrentStep("代码生成已中止");
                return WorkflowContext.saveContext(context);
            }

            //根据类型生成目录
            String generatedCodeDir = String.format("%s/%s_%s",
                    AppConstant.CODE_OUTPUT_ROOT_DIR,codeGenTypeEnum.getValue(),context.getAppId());

            log.info("AI 代码生成完成,生成目录:{}", generatedCodeDir);
            // 更新状态
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            log.info("代码生成完成，目录: {}", generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    private static String buildUserMessage(WorkflowContext context){
        String userMessage = context.getEnhancedPrompt();
        //检查是否存在质检失败结果
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)){
            userMessage+=buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     * @param qualityResult
     * @return
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult){
        return qualityResult != null && !qualityResult.getIsValid()&&qualityResult.getErrors()!=null&&!qualityResult.getErrors().isEmpty();
    }
    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult){
        StringBuilder errorInfo=new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题,请修复:\n");
        //添加错误列表
        qualityResult.getErrors().forEach(error->{
            errorInfo.append("- ").append(error).append("\n");
        });
        //添加修复建议
        errorInfo.append("\n\n## 修复建议:\n");
        if (qualityResult.getSuggestions()!=null&&!qualityResult.getSuggestions().isEmpty()){
            qualityResult.getSuggestions().forEach(suggestion->{
                errorInfo.append("- ").append(suggestion).append("\n");
            });
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码,确保修复所有提到的问题。");
        return errorInfo.toString();
    }

}
