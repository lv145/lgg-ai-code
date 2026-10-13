package com.lgg.lggaicode.langgraph4j;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.lgg.lggaicode.ai.cancel.StreamCancelManager;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.langgraph4j.model.QualityResult;
import com.lgg.lggaicode.langgraph4j.node.*;
import com.lgg.lggaicode.langgraph4j.state.WorkflowContext;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.service.AppService;
import com.lgg.lggaicode.service.ChatHistoryService;
import com.lgg.lggaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
public class CodeGenWorkflow {

    public Flux<String> executeWorkflowWithFlux(String originalPrompt, Long appId, User loginUser, String requestId){
        ChatHistoryService chatHistoryService = SpringContextUtil.getBean(ChatHistoryService .class);
        StreamCancelManager streamCancelManager = SpringContextUtil.getBean(StreamCancelManager.class);

        Long parentId = chatHistoryService.addChatMessage(appId, loginUser.getId(), originalPrompt, ChatHistoryMessageTypeEnum.USER, null);
        return Flux.create(sink->{
           // 客户端断开时，也把当前工作流标记为中止。
           sink.onCancel(() -> streamCancelManager.cancel(requestId, appId, loginUser.getId()));
           Thread.startVirtualThread(()->{
               String nextStr;
               try {
                   if (streamCancelManager.isCancelled(requestId, appId, loginUser.getId())) {
                       sink.complete();
                       return;
                   }
                   AppService appService = SpringContextUtil.getBean(AppService.class);
                   CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                   WorkflowContext initialContext = WorkflowContext.builder().originalPrompt(originalPrompt).currentStep("初始化").build();
                  // 设置应用 ID 和用户 ID
                   initialContext.setAppId(appId);
                   initialContext.setUserId(loginUser.getId());
                   initialContext.setParentId(parentId);
                   initialContext.setRequestId(requestId);
                   nextStr=formatSseEvent("workflow_start",Map.of(
                           "message","开始执行代码生成工作流",
                           "originalPrompt",originalPrompt
                   ));
                   sink.next(nextStr);
                   chatHistoryService.addChatMessage(appId, loginUser.getId(), nextStr, ChatHistoryMessageTypeEnum.AI, parentId);
                   GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);

                   log.info("工作流图:\n{}",graph.content());

                   int stepCounter=1;
                   WorkflowContext finalContext = null;
                   for (NodeOutput<MessagesState<String>> step : workflow.stream(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY,initialContext))) {
                       if (streamCancelManager.isCancelled(requestId, appId, loginUser.getId()) || sink.isCancelled()) {
                           log.info("用户已中止工作流，appId={}, requestId={}", appId, requestId);
                           sink.complete();
                           return;
                       }
                       log.info("--- 第 {} 步完成 ---",stepCounter);
                       WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                       // 覆盖更新最终上下文
                       finalContext = currentContext;
                       if (currentContext!=null){
                           nextStr=formatSseEvent("step_completed",Map.of(
                                   "stepNumber",stepCounter,
                                   "currentStep",currentContext.getCurrentStep()
                           ));
                           sink.next(nextStr);

                           log.info("当前步骤上下文:{}",currentContext);
                       }
                       if (streamCancelManager.isCancelled(requestId, appId, loginUser.getId()) || sink.isCancelled()) {
                           log.info("用户已中止工作流，appId={}, requestId={}", appId, requestId);
                           sink.complete();
                           return;
                       }
                       chatHistoryService.addChatMessage(appId, loginUser.getId(), nextStr, ChatHistoryMessageTypeEnum.AI,parentId);
                       stepCounter++;
                   }
                   if (streamCancelManager.isCancelled(requestId, appId, loginUser.getId()) || sink.isCancelled()) {
                       log.info("用户已中止工作流，appId={}, requestId={}", appId, requestId);
                       sink.complete();
                       return;
                   }
                   nextStr=formatSseEvent("workflow_completed",Map.of(
                           "message","代码生成工作流执行完成"
                   ));
                   sink.next(nextStr);
                   App app=App.builder().id(appId)
                           .isBuilderComplete(true)
                           .codeGenType(finalContext.getCodeGenTypeEnum().getValue()).build();
                   appService.updateById(app);
                   chatHistoryService.addChatMessage(appId, loginUser.getId(), nextStr, ChatHistoryMessageTypeEnum.AI,parentId);
                   log.info("代码生成工作流执行完成!");
                   sink.complete();
               }catch (Exception e){
                   log.error("工作流执行失败: {}",e.getMessage(),e);
                   nextStr=formatSseEvent("workflow_error",Map.of(
                           "error",e.getMessage(),
                           "message","工作流执行失败"
                   ));
                   sink.next(nextStr);
                   chatHistoryService.addChatMessage(appId, loginUser.getId(), nextStr, ChatHistoryMessageTypeEnum.AI,parentId);
                   sink.error(e);
               }


           }) ;



        });

    }

    /**
     * 格式化SSE事件
     * @param eventType
     * @param data
     * @return
     */
    private String formatSseEvent(String eventType,Object data){
        try {
            String jsonStr = JSONUtil.toJsonStr(data);
            return "event:" + eventType + "\ndata:" + jsonStr+"\n\n";
        }catch (Exception e){
            log.error("格式化SSE事件失败:{}",e.getMessage(),e);
            return "event:error\ndata:{\"error\":\"格式化SSE事件失败\"}";

        }
    }

    /**
     * 质检通过后,根据项目类型判断是否构建项目
     * 如果是Vue项目,则构建项目
     * 如果不是Vue项目,则跳过构建项目
     * @param state
     * @return
     */
    private String routeAfterQualityCheck(MessagesState<String> state){
        WorkflowContext context=WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        //如果质检失败,重新生成代码
        if (qualityResult==null || !qualityResult.getIsValid()){
            log.error("代码质检失败,需要重新生成代码");
            return "fail";
        }
        log.info("代码质检通过,继续后续流程");
        return routeBuildOrSkip(state);
    }
    private String routeBuildOrSkip(MessagesState<String> state){
        WorkflowContext context = WorkflowContext.getContext(state);
        CodeGenTypeEnum codeGenTypeEnum = context.getCodeGenTypeEnum();
        if (codeGenTypeEnum==CodeGenTypeEnum.VUE_PROJECT){
            return "build";
        }
        return "skip_build";


    }

    /**
     * 创建代码生成工作流
     * @return
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {

            // 创建工作流图
            return new MessagesStateGraph<String>()
                    // 添加节点 - 使用真实的工作节点
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())
                    .addNode("code_quality_check", CodeQualityCheckNode.create())
                    // 添加边
                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator", "code_quality_check")
                    // 使用条件边：根据代码生成类型决定是否需要构建
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    "build", "project_builder",  // 需要构建的情况
                                    "skip_build", END,             // 跳过构建直接结束
                                    "fail","code_generator"
                            ))
                    .addEdge("project_builder", END)
                    // 编译工作流
                    .compile();
        } catch (GraphStateException e) {
            System.out.println(e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    /**
     *
     * @param originalPrompt
     * @return
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();

        // 初始化 WorkflowContext - 只设置基本信息
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();
        log.info("初始输入: {}", initialContext.getOriginalPrompt());
        log.info("开始执行工作流");

        // 显示工作流图
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流图:\n{}", graph.content());
        WorkflowContext finalContext=null;

        // 执行工作流
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            // 显示当前状态
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext=currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("工作流执行完成！");
        return finalContext;
    }

    /**
     * 执行工作流（SSE 流式输出版本）
     */
    public SseEmitter executeWorkflowWithSse(String originalPrompt) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        Thread.startVirtualThread(() -> {
            try {
                CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                WorkflowContext initialContext = WorkflowContext.builder()
                        .originalPrompt(originalPrompt)
                        .currentStep("初始化")
                        .build();
                sendSseEvent(emitter, "workflow_start", Map.of(
                        "message", "开始执行代码生成工作流",
                        "originalPrompt", originalPrompt
                ));
                GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                log.info("工作流图:\n{}", graph.content());

                int stepCounter = 1;
                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                    log.info("--- 第 {} 步完成 ---", stepCounter);
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        sendSseEvent(emitter, "step_completed", Map.of(
                                "stepNumber", stepCounter,
                                "currentStep", currentContext.getCurrentStep()
                        ));
                        log.info("当前步骤上下文: {}", currentContext);
                    }
                    stepCounter++;
                }
                sendSseEvent(emitter, "workflow_completed", Map.of(
                        "message", "代码生成工作流执行完成！"
                ));
                log.info("代码生成工作流执行完成！");
                emitter.complete();
            } catch (Exception e) {
                log.error("工作流执行失败: {}", e.getMessage(), e);
                sendSseEvent(emitter, "workflow_error", Map.of(
                        "error", e.getMessage(),
                        "message", "工作流执行失败"
                ));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 发送 SSE 事件的辅助方法
     */
    private void sendSseEvent(SseEmitter emitter, String eventType, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(data));
        } catch (IOException e) {
            log.error("发送 SSE 事件失败: {}", e.getMessage(), e);
        }
    }

}
