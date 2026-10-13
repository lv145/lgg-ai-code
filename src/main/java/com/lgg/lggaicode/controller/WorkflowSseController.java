package com.lgg.lggaicode.controller;

import com.lgg.lggaicode.annotation.AuthCheck;
import com.lgg.lggaicode.langgraph4j.CodeGenWorkflow;
import com.lgg.lggaicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/workflow")
@Slf4j

public class WorkflowSseController {
//    /**
//     * 同步执行工作流
//     */
//    @GetMapping(value = "/execute",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public WorkflowContext executeWorkflow(@RequestParam String prompt) {
//        log.info("收到同步工作流执行请求:{}",prompt);
//        return new CodeGenWorkflow().executeWorkflow(prompt);
//    }
//    /**
//     * Flux 流式工作流
//     */
//    @GetMapping(value = "/execute-flux",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> executeWorkflowWithFlux(@RequestParam String prompt,Long appId) {
//        log.info("收到 Flux 工作流执行请求:{}",prompt);
//        return new CodeGenWorkflow().executeWorkflowWithFlux(prompt,appId);
//    }
//    /**
//     * SSE 流式工作流
//     */
//    @GetMapping(value = "/execute-sse",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public SseEmitter executeWorkflowWithSse(@RequestParam String prompt) {
//        log.info("收到 SSE 工作流执行请求:{}",prompt);
//        return new CodeGenWorkflow().executeWorkflowWithSse(prompt);
//    }
}
