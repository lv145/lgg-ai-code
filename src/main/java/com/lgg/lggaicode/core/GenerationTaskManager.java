package com.lgg.lggaicode.core;

import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GenerationTaskManager {
    /**
     * 用于管理生成生成任务的事件流
     */
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    /**
     * 创建生成生成任务
     * @param requestId 生成任务ID
     */
    public void create(String requestId) {
        tasks.put(requestId, new Task());
    }

    /**
     * 发布生成生成任务事件
     * @param requestId 生成任务ID
     * @param event     事件类型
     * @param data      事件数据
     * @return 发布的事件
     */
    public GenerationEvent publish(String requestId, String event, String data) {
        Task task = tasks.get(requestId);
        if (task == null) {
            return null;
        }

        long id = task.sequence.incrementAndGet();
        GenerationEvent message = new GenerationEvent(id, event, data);
        task.sink.tryEmitNext(message);
        return message;
    }
    /**
     * 订阅生成生成任务事件
     * @param requestId 生成任务ID
     * @param lastEventId 最后一个事件ID
     * @return 生成生成任务事件流
     */
    public Flux<GenerationEvent> subscribe(String requestId, long lastEventId) {
        Task task = tasks.get(requestId);
        if (task == null) {
            return Flux.error(new BusinessException(
                    ErrorCode.NOT_FOUND_ERROR, "生成任务不存在"));
        }

        return task.sink.asFlux()
                .filter(event -> event.id() > lastEventId);
    }

    public void complete(String requestId) {
        publish(requestId, "done", "");
        Task task = tasks.get(requestId);
        if (task != null) {
            task.sink.tryEmitComplete();
        }
    }

    public void error(String requestId, String message) {
        publish(requestId, "error", message);
        Task task = tasks.get(requestId);
        if (task != null) {
            task.sink.tryEmitComplete();
        }
    }

    private static class Task {
        // 事件序列号
        private final AtomicLong sequence = new AtomicLong();
        // 事件流
        private final Sinks.Many<GenerationEvent> sink =
                Sinks.many().replay().limit(2000);
    }
}