package com.lgg.lggaicode.core;

import cn.hutool.json.JSONUtil;
import com.lgg.lggaicode.ai.cancel.StreamCancelManager;
import com.lgg.lggaicode.ai.message.AiResponseMessage;
import com.lgg.lggaicode.ai.message.ToolExecutedMessage;
import com.lgg.lggaicode.ai.message.ToolRequestMessage;
import com.lgg.lggaicode.ai.model.HtmlCodeResult;
import com.lgg.lggaicode.ai.model.MultiFileCodeResult;


import com.lgg.lggaicode.ai.factory.AiCodeGeneratorServiceFactory;

import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.core.builder.VueProjectBuilder;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.mapper.AppMapper;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.parser.CodeParserExecutor;
import com.lgg.lggaicode.saver.CodeFileSaverExecutor;
import com.lgg.lggaicode.ai.service.AiCodeGeneratorService;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
    @Resource
    private AppMapper appMapper;
    @Resource
    private StreamCancelManager streamCancelManager;


    // 抽私有统一获取方法
    private AiCodeGeneratorService getAiService(Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        return aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
    }

    /**
     * 更新应用构建状态。
     *
     * 备注：这里直接使用 Mapper，避免 facade 反向注入 AppService 造成循环依赖。
     */
    private void updateBuilderComplete(Long appId, boolean isBuilderComplete) {
        appMapper.update(App.builder().id(appId).isBuilderComplete(isBuilderComplete).build(), true);
    }


    /**
     * 通用流式代码处理方法
     *
     * @param codeStream        代码流
     * @param codeGenType       代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType,
                                           Long appId, Long userId, String requestId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                // 收到停止信号后结束当前 Flux，避免继续向前端输出。
                .takeUntilOther(streamCancelManager.cancelSignal(requestId, appId, userId))
                .doOnNext(chunk -> {
                    // 实时收集代码片段；取消后 Reactor 会停止继续进入这里。
                    codeBuilder.append(chunk);
                    log.debug("收到代码分片：{}", chunk);
                })
                .doOnComplete(() -> {
                    // 用户中止时不保存半截代码，也不把构建状态标记为完成。
                    if (streamCancelManager.isCancelled(requestId, appId, userId)) {
                        updateBuilderComplete(appId, false);
                        log.info("用户已中止生成，不保存半截代码，appId={}, requestId={}", appId, requestId);
                        return;
                    }
                    try {
                        String completeCode = codeBuilder.toString();
                        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                        File savedDir = CodeFileSaverExecutor.executeSaver(codeGenType, parsedResult, appId);
                        updateBuilderComplete(appId, true);
                        log.info("保存成功，路径为：{}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        updateBuilderComplete(appId, false);
                        log.error("保存失败: {}", e.getMessage());
                    }
                })
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL) {
                        updateBuilderComplete(appId, false);
                    }
                });
    }


    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage       用户提示词
     * @param codeGenTypeEnum   生成类型
     * @return 保存的流
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum,
                                                  Long appId, Long userId, String requestId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = this.getAiService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(result, codeGenTypeEnum, appId, userId, requestId);
            }
            case MULTI_FILE -> {
                Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(result, codeGenTypeEnum, appId, userId, requestId);
            }
            case VUE_PROJECT -> {
                log.info("appId: {}, userMessage: {}", appId, userMessage);

                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, userId, requestId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream       TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, Long userId, String requestId) {
        return Flux.create(sink -> {
            // 客户端断开 SSE 时，也同步标记为用户中止，方便上层状态判断。
            sink.onCancel(() -> streamCancelManager.cancel(requestId, appId, userId));

            tokenStream.onPartialResponseWithContext((partialResponse, context) -> {
                        streamCancelManager.bindStreamingHandle(
                                requestId,
                                appId,
                                userId,
                                context.streamingHandle()
                        );

                        if (streamCancelManager.isCancelled(requestId, appId, userId)
                                || sink.isCancelled()) {
                            context.streamingHandle().cancel();
                            return;
                        }

                        AiResponseMessage message = new AiResponseMessage(partialResponse.text());
                        sink.next(JSONUtil.toJsonStr(message));
                    })
                    .onPartialToolCallWithContext((partialToolCall, context) -> {
                        streamCancelManager.bindStreamingHandle(
                                requestId,
                                appId,
                                userId,
                                context.streamingHandle()
                        );

                        if (streamCancelManager.isCancelled(requestId, appId, userId)
                                || sink.isCancelled()) {
                            context.streamingHandle().cancel();
                            return;
                        }

                        ToolRequestMessage message = new ToolRequestMessage(partialToolCall);
                        sink.next(JSONUtil.toJsonStr(message));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        if (streamCancelManager.isCancelled(requestId, appId, userId) || sink.isCancelled()) {
                            return;
                        }
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (streamCancelManager.isCancelled(requestId, appId, userId) || sink.isCancelled()) {
                            updateBuilderComplete(appId, false);
                            log.info("用户已中止 Vue 项目生成，不执行构建，appId={}, requestId={}", appId, requestId);
                            return;
                        }
                        // 异步构建Vue项目
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR+ File.separator+"vue_project_"+appId;
                        vueProjectBuilder.buildProjectAsync(projectPath)
                                .thenAccept(result -> updateBuilderComplete(appId, result));
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        updateBuilderComplete(appId, false);
                        if (sink.isCancelled()) {
                            return;
                        }
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = this.getAiService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(codeGenTypeEnum, htmlCodeResult, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiFileResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(codeGenTypeEnum, multiFileResult, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    /**
     * 生成多文件模式的代码并保存（流式）
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveMultiFileCodeStream(String userMessage, Long appId) {
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = this.getAiService(appId, CodeGenTypeEnum.MULTI_FILE);
        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
        // 当流式返回生成代码完成后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return result
                .doOnNext(chunk -> {
                    // 实时收集代码片段
                    codeBuilder.append(chunk);
                })
                .doOnComplete(() -> {
                    // 流式返回完成后保存代码
                    try {
                        String completeMultiFileCode = codeBuilder.toString();
                        MultiFileCodeResult multiFileResult = CodeParser.parseMultiFileCode(completeMultiFileCode);
                        // 保存代码到文件
                        File savedDir = CodeFileSaver.saveMultiFileCodeResult(multiFileResult);
                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }



//    /**
//     * 生成 HTML 模式的代码并保存
//     *
//     * @param userMessage 用户提示词
//     * @return 保存的目录
//     */
//    private File generateAndSaveHtmlCode(String userMessage) {
//        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
//        return CodeFileSaver.saveHtmlCodeResult(result);
//    }
//
//    /**
//     * 生成多文件模式的代码并保存
//     *
//     * @param userMessage 用户提示词
//     * @return 保存的目录
//     */
//    private File generateAndSaveMultiFileCode(String userMessage) {
//        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//        return CodeFileSaver.saveMultiFileCodeResult(result);
//    }
//    /**
//     * 生成 HTML 模式的代码并保存（流式）
//     *
//     * @param userMessage 用户提示词
//     * @param multiFile
//     * @return 保存的目录
//     */
//    public Flux<String> generateAndSaveHtmlCodeStream(String userMessage, CodeGenTypeEnum multiFile) {
//        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
//        // 当流式返回生成代码完成后，再保存代码
//        StringBuilder codeBuilder = new StringBuilder();
//        return result
//                .doOnNext(chunk -> {
//                    // 实时收集代码片段
//                    codeBuilder.append(chunk);
//                })
//                .doOnComplete(() -> {
//                    // 流式返回完成后保存代码
//                    try {
//                        String completeHtmlCode = codeBuilder.toString();
//                        HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(completeHtmlCode);
//                        // 保存代码到文件
//                        File savedDir = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
//                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
//                    } catch (Exception e) {
//                        log.error("保存失败: {}", e.getMessage());
//                    }
//                });
//    }
}
