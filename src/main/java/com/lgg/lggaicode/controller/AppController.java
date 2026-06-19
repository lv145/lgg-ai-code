package com.lgg.lggaicode.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lgg.lggaicode.annotation.AuthCheck;
import com.lgg.lggaicode.common.BaseResponse;
import com.lgg.lggaicode.common.DeleteRequest;
import com.lgg.lggaicode.common.ResultUtils;
import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.constant.UserConstant;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.exception.ThrowUtils;
import com.lgg.lggaicode.model.dto.*;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.model.vo.AppVO;
import com.lgg.lggaicode.service.AppService;
import com.lgg.lggaicode.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.MalformedInputException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 */
@RestController
@RequestMapping("/app")
public class AppController implements AppConstant {

    @Autowired
    private AppService appService;

    @Autowired
    private UserService userService;

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

    @GetMapping(value = "/chat/gen/code",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null||appId<=0, ErrorCode.PARAMS_ERROR,"应用id无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        User loginUser = userService.getLoginUser(request);

        Flux<String> stringFlux = appService.chatToGenCode(appId, message, loginUser);
        return  stringFlux.map(
                s -> {//将内容封装为JSON字符串 映射为 ServerSentEvent 对象
                    Map<String, String> map = Map.of("d", s);
                    String jsonStr = JSONUtil.toJsonStr(map);
                    return ServerSentEvent.<String>builder().data(jsonStr).build();
                }).concatWith(
                Mono.just(// 发送完成事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build())
        );
    }

    /**
     * 创建应用
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "initPrompt 不能为空");
        User loginUser = userService.getLoginUser(request);
        App app = new App();
        app.setAppName(initPrompt.substring(0,Math.min(initPrompt.length(), 12)));
        app.setInitPrompt(initPrompt);
        app.setUserId(loginUser.getId());
        app.setCodeGenType(CodeGenTypeEnum.MULTI_FILE.getValue());
        app.setPriority(0); ///
        boolean result = appService.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(app.getId());
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
        boolean result = appService.deleteApp(deleteRequest.getId());
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
        boolean result = appService.deleteApp(oldApp.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员更新任意应用
     */
    @PostMapping("/admin/update")
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
