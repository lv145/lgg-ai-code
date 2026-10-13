package com.lgg.lggaicode.langgraph4j.node;

import com.lgg.lggaicode.ai.factory.AiCodeGenTypeRoutingServiceFactory;
import com.lgg.lggaicode.langgraph4j.state.WorkflowContext;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.ai.service.AiCodeGenTypeRoutingService;
import com.lgg.lggaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class RouterNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            String originalPrompt = context.getOriginalPrompt();
            log.info("执行节点: 智能路由智能路由");
            CodeGenTypeEnum codeGenTypeEnum;
            try {
                //获取AI路由服务工厂创建新的路由服务实例
                AiCodeGenTypeRoutingServiceFactory routingServiceFactory = SpringContextUtil.getBean(AiCodeGenTypeRoutingServiceFactory.class);
                AiCodeGenTypeRoutingService typeRoutingService = routingServiceFactory.createAiCodeGenTypeRoutingService();
                codeGenTypeEnum = typeRoutingService.routeCodeGenType(originalPrompt);
            }catch (Exception e){
                log.error("AI智能路由失败,使用默认HTML类型:{}",e.getMessage(),e);
                codeGenTypeEnum = CodeGenTypeEnum.HTML;
            }

            // 更新状态
            context.setCurrentStep("智能路由");
            context.setCodeGenTypeEnum(codeGenTypeEnum);
            log.info("路由决策完成，选择类型: {}", codeGenTypeEnum.getText());
            return WorkflowContext.saveContext(context);
        });
    }
}
