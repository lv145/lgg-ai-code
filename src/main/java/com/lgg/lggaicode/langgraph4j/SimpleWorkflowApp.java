package com.lgg.lggaicode.langgraph4j;

import com.lgg.lggaicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 简化版网站生成工作流应用 - 使用 MessagesState
 */
@Slf4j
public class SimpleWorkflowApp {

    /**
     * 创建带状态感知的工作节点
     */
    static AsyncNodeAction<MessagesState<String>> makeStatefulNode(String nodeName,String message) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: {}", message);
            if (context!=null) {
                context.setCurrentStep(nodeName);
            }
            return Map.of("messages", message);
        });
    }

    public static void main(String[] args) throws GraphStateException {
        // 创建工作流图
        CompiledGraph<MessagesState<String>> workflow = new MessagesStateGraph<String>()
                // 添加节点
                .addNode("image_collector", makeStatefulNode("image_collector","获取图片素材"))
                .addNode("prompt_enhancer", makeStatefulNode("prompt_enhancer","增强提示词"))
                .addNode("router", makeStatefulNode("router", "智能路由选择"))
                .addNode("code_generator", makeStatefulNode("code_generator", "网站代码生成"))
                .addNode("project_builder", makeStatefulNode("project_builder","项目构建"))

                // 添加边
                .addEdge(START, "image_collector")                // 开始 -> 图片收集
                .addEdge("image_collector", "prompt_enhancer")    // 图片收集 -> 提示词增强
                .addEdge("prompt_enhancer", "router")             // 提示词增强 -> 智能路由
                .addEdge("router", "code_generator")              // 智能路由 -> 代码生成
                .addEdge("code_generator", "project_builder")     // 代码生成 -> 项目构建
                .addEdge("project_builder", END)                  // 项目构建 -> 结束

                // 编译工作流
                .compile();
        //初始化 WorkflowContext -只设置基本信息
        WorkflowContext initialContext =WorkflowContext.builder()
                        .originalPrompt("创建一个LGG的个人博客网站")
                        .currentStep("初始化").build();
        log.info("初始输入:{}",initialContext.getOriginalPrompt());

        log.info("开始执行工作流");

        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流图: \n{}", graph.content());

        // 执行工作流
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(Map.of())) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            //显示当前状态
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext!=null){
                log.info("当前步骤上下文:{}",currentContext);
            }

            log.info("步骤输出: {}", step);
            stepCounter++;
        }

        log.info("工作流执行完成！");
    }
}
