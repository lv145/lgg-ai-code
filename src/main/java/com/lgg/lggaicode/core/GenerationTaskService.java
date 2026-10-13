package com.lgg.lggaicode.core;

import com.lgg.lggaicode.ai.cancel.StreamCancelManager;
import com.lgg.lggaicode.langgraph4j.CodeGenWorkflow;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.service.AppService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class GenerationTaskService {

    @Resource
    private AppService appService;

    @Resource
    private GenerationTaskManager taskManager;

    @Resource
    private StreamCancelManager streamCancelManager;

    public void start(
            Long appId,
            String message,
            User loginUser,
            String requestId
    ) {
        streamCancelManager.register(
                requestId,
                appId,
                loginUser.getId()
        );

        Thread.startVirtualThread(() -> {
            try {
                App app = appService.getById(appId);

                Flux<String> source;

                if (Boolean.TRUE.equals(app.getIsAgentModel())
                        && !Boolean.TRUE.equals(app.getIsBuilderComplete())) {
                    source = new CodeGenWorkflow()
                            .executeWorkflowWithFlux(
                                    message,
                                    appId,
                                    loginUser,
                                    requestId
                            );
                } else {
                    source = appService.chatToGenCode(
                            appId,
                            message,
                            loginUser,
                            requestId
                    );
                }
                // 订阅流
                source.subscribe(
                        chunk -> publishChunk(requestId, chunk),
                        error -> {
                            taskManager.error(
                                    requestId,
                                    error.getMessage()
                            );
                            streamCancelManager.remove(
                                    requestId,
                                    appId,
                                    loginUser.getId()
                            );
                        },
                        () -> {
                            taskManager.complete(requestId);
                            streamCancelManager.remove(
                                    requestId,
                                    appId,
                                    loginUser.getId()
                            );
                        }
                );

            } catch (Exception e) {
                taskManager.error(requestId, e.getMessage());
                streamCancelManager.remove(
                        requestId,
                        appId,
                        loginUser.getId()
                );
            }
        });
    }

    private void publishChunk(String requestId, String chunk) {
        String eventType = "ai_response";

        if (chunk.contains("\"type\":\"tool_request\"")) {
            eventType = "tool_request";
        } else if (chunk.contains("\"type\":\"tool_executed\"")) {
            eventType = "tool_executed";
        } else if (chunk.startsWith("event:")) {
            eventType = "workflow_step";
        }

        taskManager.publish(requestId, eventType, chunk);
    }
}
