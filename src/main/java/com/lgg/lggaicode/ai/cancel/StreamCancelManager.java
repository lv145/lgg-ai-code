package com.lgg.lggaicode.ai.cancel;

import dev.langchain4j.model.chat.response.StreamingHandle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式生成取消管理器。
 *
 * 备注：
 * 1. requestId 表示一次具体的流式生成请求，避免同一用户多个页面或多次生成互相误停。
 * 2. AtomicBoolean 给同步代码判断是否已停止。
 * 3. Sinks.One 给 Reactor Flux 链路触发取消信号。
 */
@Component
public class StreamCancelManager {

    private final Map<String, StreamCancelTask> taskMap = new ConcurrentHashMap<>();

    /**
     * 注册一次新的流式生成任务。
     */
    public void register(String requestId, Long appId, Long userId) {
        taskMap.put(buildKey(requestId, appId, userId), new StreamCancelTask());
    }

    /**
     * 绑定流式生成句柄到任务。
     * @param requestId
     * @param appId
     * @param userId
     * @param handle
     */
    public void bindStreamingHandle(
            String requestId,
            Long appId,
            Long userId,
            StreamingHandle handle
    ) {
        StreamCancelTask task = taskMap.get(buildKey(requestId, appId, userId));
        if (task == null) {
            return;
        }

        task.streamingHandle.set(handle);

        // 防止停止请求早于模型句柄产生
        if (task.cancelled.get()) {
            handle.cancel();
        }
    }
    /**
     * 标记任务已取消，并通知订阅了 cancelSignal 的 Flux 结束。
     */
    public boolean cancel(String requestId, Long appId, Long userId) {
        StreamCancelTask task = taskMap.get(buildKey(requestId, appId, userId));
        if (task== null) {
            return false;
        }
        task.cancelled.compareAndSet(false, true);//原子操作，确保只设置一次

        StreamingHandle handle = task.streamingHandle.get();
        if (handle != null) {
            handle.cancel();
        }

        task.cancelSink.tryEmitValue(true);
        return true;
    }

    /**
     * 同步判断任务是否已经被用户中止。
     */
    public boolean isCancelled(String requestId, Long appId, Long userId) {
        StreamCancelTask task = taskMap.get(buildKey(requestId, appId, userId));
        return task != null && task.cancelled.get();
    }

    /**
     * Reactor 取消信号，配合 takeUntilOther 使用。
     */
    public Mono<Boolean> cancelSignal(String requestId, Long appId, Long userId) {
        StreamCancelTask task = taskMap.get(buildKey(requestId, appId, userId));
        return task == null ? Mono.never() : task.cancelSink.asMono();
    }

    /**
     * 流结束后清理任务，避免内存泄漏。
     */
    public void remove(String requestId, Long appId, Long userId) {
        taskMap.remove(buildKey(requestId, appId, userId));
    }

    private String buildKey(String requestId, Long appId, Long userId) {
        return userId + ":" + appId + ":" + requestId;
    }

    private static class StreamCancelTask {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<StreamingHandle> streamingHandle =
                new AtomicReference<>();
        private final Sinks.One<Boolean> cancelSink = Sinks.one();
    }
}
