package com.lgg.lggaicode.langgraph4j.node;

import cn.hutool.core.util.RandomUtil;
import com.lgg.lggaicode.core.builder.VueProjectBuilder;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.langgraph4j.state.WorkflowContext;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ProjectBuilderNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            //获取必要的参数
            String generatedCodeDir = context.getGeneratedCodeDir();
            String buildResultDir;
            //vue 项目类型:使用VueProjectBuilder 进行构建
            try {
                //执行vue项目构建(非异步)
                VueProjectBuilder vueProjectBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                boolean buildSuccess = vueProjectBuilder.buildProject(generatedCodeDir);
                if (buildSuccess){
                    //构建成功,返回dist目录路径
                    buildResultDir=generatedCodeDir+ File.separator+"dist";
                    log.info("vue项目构建成功,dist目录:{}",buildResultDir);
                }else {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,"Vue项目构建失败");
                }
            }catch (Exception e){
                log.error("Vue项目构建异常:{}",e.getMessage(),e);
                buildResultDir=generatedCodeDir;
            }

            log.info("执行节点: 项目构建");
            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建完成，结果目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
