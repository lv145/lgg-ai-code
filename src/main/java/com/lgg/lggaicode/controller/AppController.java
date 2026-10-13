package com.lgg.lggaicode.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lgg.lggaicode.annotation.AuthCheck;
import com.lgg.lggaicode.common.BaseResponse;
import com.lgg.lggaicode.common.DeleteRequest;
import com.lgg.lggaicode.common.ResultUtils;
import com.lgg.lggaicode.ai.cancel.StreamCancelManager;
import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.constant.UserConstant;
import com.lgg.lggaicode.core.GenerationTaskManager;
import com.lgg.lggaicode.core.GenerationTaskService;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.exception.ThrowUtils;
import com.lgg.lggaicode.langgraph4j.CodeGenWorkflow;
import com.lgg.lggaicode.model.dto.*;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.model.vo.AppVO;
import com.lgg.lggaicode.ratelimiter.annotation.RateLimit;
import com.lgg.lggaicode.ratelimiter.enums.RateLimitType;
import com.lgg.lggaicode.service.AppService;
import com.lgg.lggaicode.service.ProjectDownloadService;
import com.lgg.lggaicode.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 应用 控制层。
 */
@Slf4j
@RestController
@RequestMapping("/app")
public class AppController {

    @Autowired
    private AppService appService;

    @Autowired
    private UserService userService;

    @Resource
    private ProjectDownloadService projectDownloadService;
    @Resource
    private StreamCancelManager streamCancelManager;

    @Resource
    private GenerationTaskManager generationTaskManager;

    @Resource
    private GenerationTaskService generationTaskService;

    @PostMapping("/chat/gen/task")
    @RateLimit(limitType = RateLimitType.USER, rate = 4, rateInterval = 60, message = "AI对话请求过于频繁，请稍后再试")
    public BaseResponse<String> startGeneration(
            @RequestParam Long appId,
            @RequestParam String message,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用id无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");

        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(
                !app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR,
                "您没有权限操作该应用"
        );

        String requestId = UUID.randomUUID().toString();

        generationTaskManager.create(requestId);

        generationTaskService.start(
                appId,
                message,
                loginUser,
                requestId
        );

        return ResultUtils.success(requestId);
    }

    @GetMapping(
            value = "/chat/gen/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> subscribeGeneration(
            @RequestParam String requestId,
            @RequestHeader(
                    value = "Last-Event-ID",
                    required = false
            ) String lastEventId
    ) {
        long cursor = 0;

        if (StrUtil.isNotBlank(lastEventId)) {
            cursor = Long.parseLong(lastEventId);
        }

        return generationTaskManager
                .subscribe(requestId, cursor)
                .map(event -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(event.id()))
                        .event(event.event())
                        .data(event.data())
                        .build());
    }


    /**
     * 下载应用代码
     * @param appId
     * @param response
     * @param request
     */
    @GetMapping("/download/{appId}")
    public void download(@PathVariable Long appId, HttpServletResponse response, HttpServletRequest request) {
        //参数校验
        ThrowUtils.throwIf(appId == null||appId<=0, ErrorCode.PARAMS_ERROR, "应用id不能为空");
        //查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app==null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //验效用户是否有权限
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId())||!loginUser.getUserRole().equals(UserConstant.ADMIN_ROLE), ErrorCode.NO_AUTH_ERROR, "您没有权限操作该应用");
        //构建项目目录路径
        String appDirName=app.getCodeGenType()+"_"+appId;
        String appDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR+ File.separator+appDirName;
        File appDir = new File(appDirPath);
        ThrowUtils.throwIf(!appDir.exists()&&!appDir.isDirectory(), ErrorCode.NOT_FOUND_ERROR, "应用目录不存在");
        //调用项目下载服务下载项目
        String fileName =String.valueOf(appId);
        projectDownloadService.downloadProjectAsZip(appDirPath, fileName,  response);
    }
    /**
     * 部署应用
     * @param appDeployRequest
     * @param request
     * @return
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null||appId<=0, ErrorCode.PARAMS_ERROR,"应用id无效");
        //获取当前登录用户信息
        User loginUser = userService.getLoginUser(request);
        //调用部署应用服务
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 生成代码
     * @param appId 应用ID
     * @param message 用户消息提示
     * @param request HTTP请求对象
     * @return 生成代码的服务器发送事件流
     */
    @GetMapping(value = "/chat/gen/code",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 4, rateInterval = 60, message = "AI对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       @RequestParam String requestId,
                                                       HttpServletRequest request) {


        ThrowUtils.throwIf(appId == null||appId<=0, ErrorCode.PARAMS_ERROR,"应用id无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // requestId 是“本次流式生成”的唯一标识，不属于创建应用参数。
        //查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app==null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        User loginUser = userService.getLoginUser(request);
        // 注册本次流式任务。requestId 用来精准停止当前这一次生成，避免误停其他页面或其他请求。
        streamCancelManager.register(requestId, appId, loginUser.getId());
        Flux<String> stringFlux;
        try {
            //判断是否为智能体
            if (app.getIsAgentModel()&&!app.getIsBuilderComplete()){
                //Agent模式:使用工作流生成代码流
                stringFlux = new CodeGenWorkflow().executeWorkflowWithFlux(message,appId,loginUser, requestId);}
            else {
                //传统模式:使用AI模型生成代码流
                stringFlux = appService.chatToGenCode(appId, message, loginUser, requestId);}
        }catch (Exception e){
            streamCancelManager.remove(requestId, appId, loginUser.getId());
            log.error("生成代码失败",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"生成代码失败");
        }
        return  stringFlux
                .doOnCancel(() ->
                        streamCancelManager.cancel(
                                requestId,
                                appId,
                                loginUser.getId()
                        )
                )
                // 收到停止请求或客户端断开时，尽快结束 SSE 输出。
                .takeUntilOther(streamCancelManager.cancelSignal(requestId, appId, loginUser.getId()))
                .map(
                s -> {//将内容封装为JSON字符串 映射为 ServerSentEvent 对象
                    Map<String, String> map = Map.of("d", s);
                    String jsonStr = JSONUtil.toJsonStr(map);
                    return ServerSentEvent.<String>builder().data(jsonStr).build();
                })
                .concatWith(Mono.defer(() -> {
                    // 根据最终状态发送 done 或 stopped，前端可以据此恢复按钮状态。
                    boolean cancelled = streamCancelManager.isCancelled(requestId, appId, loginUser.getId());
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event(cancelled ? "stopped" : "done")
                            .data("")
                            .build());
                }))
                .doFinally(signalType -> {
                    // 无论正常完成、异常、取消，都要清理本次任务，避免内存泄漏。
                    streamCancelManager.remove(requestId, appId, loginUser.getId());
                });
    }

    /**
     * 中止某一次流式生成。
     *
     * 备注：requestId 必须和 /chat/gen/code 开始生成时传入的 requestId 一致。
     */
    @PostMapping("/chat/gen/code/stop")
    public BaseResponse<Boolean> stopChatToGenCode(@RequestBody StreamStopRequest streamStopRequest,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(streamStopRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = streamStopRequest.getAppId();
        String requestId = streamStopRequest.getRequestId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用id无效");
        ThrowUtils.throwIf(StrUtil.isBlank(requestId), ErrorCode.PARAMS_ERROR, "requestId 不能为空");

        User loginUser = userService.getLoginUser(request);
        boolean result = streamCancelManager.cancel(requestId, appId, loginUser.getId());

        return ResultUtils.success(result);
    }

    /**
     * 创建应用
     */
    @PostMapping("/add")
    @RateLimit(limitType = RateLimitType.API, rate = 1, rateInterval = 20, message = "创建应用过于频繁，请稍后再试")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 修改自己的应用（仅支持修改应用名称）
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUserUpdateRequest appUserUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appUserUpdateRequest == null || appUserUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(appUserUpdateRequest.getAppName()), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        User loginUser = userService.getLoginUser(request);
        App oldApp = appService.getValidApp(appUserUpdateRequest.getId());
        appService.checkAppAuth(loginUser, oldApp);
        App app = new App();
        app.setId(appUserUpdateRequest.getId());
        app.setAppName(appUserUpdateRequest.getAppName());
        app.setUpdateTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除自己的应用
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        App oldApp = appService.getValidApp(deleteRequest.getId());
        appService.checkAppAuth(loginUser, oldApp);
        ThrowUtils.throwIf(oldApp == null || oldApp.getId() <= 0, ErrorCode.OPERATION_ERROR, "应用不存在");
        boolean result = appService.deleteApp(oldApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查看应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        App app = appService.getValidApp(id);
        appService.checkAppViewAuth(loginUser, app);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页查询自己的应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppUserQueryRequest appUserQueryRequest,
                                                       HttpServletRequest request) {
        ThrowUtils.throwIf(appUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        appService.validUserPageParams(appUserQueryRequest);
        User loginUser = userService.getLoginUser(request);
        int pageNum = appUserQueryRequest.getPageNum();
        int pageSize = appUserQueryRequest.getPageSize();
        Page<App> page = appService.page(
                Page.of(pageNum, pageSize),
                appService.getMyQueryWrapper(appUserQueryRequest, loginUser.getId()));
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(page.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页查询精选应用列表
     */
    @PostMapping("/featured/list/page/vo")
    @Cacheable(
            value ="good_app_page",
            key ="T(com.lgg.lggaicode.utils.CacheKeyUtils).generateKey(#appUserQueryRequest)",
            condition = "#appUserQueryRequest.pageNum<=10"
    )
    public BaseResponse<Page<AppVO>> listFeaturedAppVOByPage(@RequestBody AppUserQueryRequest appUserQueryRequest,
                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(appUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        appService.validUserPageParams(appUserQueryRequest);
        userService.getLoginUser(request);
        int pageNum = appUserQueryRequest.getPageNum();
        int pageSize = appUserQueryRequest.getPageSize();
        Page<App> page = appService.page(
                Page.of(pageNum, pageSize),
                appService.getFeaturedQueryWrapper(appUserQueryRequest));
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(page.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    // endregion

    // region 管理员接口

    /**
     * 管理员删除任意应用
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adminDeleteApp(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        App oldApp = appService.getValidApp(deleteRequest.getId());
        boolean result = appService.deleteApp(oldApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员更新任意应用
     */
    @PostMapping("/admin/update")

    @CacheEvict(
            value = "good_app_page",
            allEntries = true,
            condition = "#appAdminUpdateRequest.priority==99||#appAdminUpdateRequest.priority!=99"
    )
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adminUpdateApp(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        appService.getValidApp(appAdminUpdateRequest.getId());
        App app = new App();
        BeanUtils.copyProperties(appAdminUpdateRequest, app);
        app.setUpdateTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页查询应用列表
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        Page<App> page = appService.page(
                Page.of(pageNum, pageSize),
                appService.getQueryWrapper(appQueryRequest));
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(page.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员查看应用详情
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<App> getAppById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getValidApp(id);
        return ResultUtils.success(app);
    }

    // endregion
}
