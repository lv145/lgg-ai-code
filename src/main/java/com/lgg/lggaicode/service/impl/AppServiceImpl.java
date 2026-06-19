package com.lgg.lggaicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.core.AiCodeGeneratorFacade;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.exception.ThrowUtils;
import com.lgg.lggaicode.mapper.AppMapper;
import com.lgg.lggaicode.model.dto.AppQueryRequest;
import com.lgg.lggaicode.model.dto.AppUserQueryRequest;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;
import com.lgg.lggaicode.model.vo.AppVO;
import com.lgg.lggaicode.service.AppService;
import com.lgg.lggaicode.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService, AppConstant {
    @Resource
    AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    ChatHistoryService chatHistoryService;

    @Override
    public String deployApp(Long appId, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId == null||appId<=0, ErrorCode.PARAMS_ERROR, "应用id不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.验效用户是否有权限
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "您没有权限操作该应用");
        //4.检查是否存在
        String deployKey = app.getDeployKey();
        //没有则生成6位deployKey(大小写字母+数字)
        if (StrUtil.isBlank(deployKey)) {
            deployKey= RandomUtil.randomString(6);
        }
        //5.获取代码生成类型,构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName=codeGenType+"_"+appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR+java.io.File.separator + sourceDirName;
        //6.检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists()||!sourceDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "应用代码目录不存在,请先生成应用代码");
        //7.复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR+java.io.File.separator + deployKey;
        File deployDir = new File(deployDirPath);
        try {
            //复制文件到部署目录
            FileUtil.copyContent(sourceDir, deployDir,true);
        }catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败:"+e.getMessage());
        }
        //8.更新应用信息
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.SYSTEM_ERROR, "更新应用部署信息失败");
        //返回可访问的URL
        return String.format("%s/%s",AppConstant.CODE_DEPLOY_HOST,deployKey);
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String userMessage, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId == null||appId<=0, ErrorCode.PARAMS_ERROR, "应用id不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(userMessage), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, "应用不存在");
        //3.验效用户是否有权限
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.PARAMS_ERROR, "您没有权限操作该应用");
        //4.调用代码生成接口
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的代码生成类型");
        Long userMessageId = chatHistoryService.addChatMessage(appId, loginUser.getId(), userMessage,
                ChatHistoryMessageTypeEnum.USER, null);
        StringBuilder aiResponseBuilder = new StringBuilder();
        try {
            return aiCodeGeneratorFacade.generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId)
                    .doOnNext(aiResponseBuilder::append)
                    .doOnComplete(() -> {
                        String aiResponse = aiResponseBuilder.toString();
                        if (StrUtil.isBlank(aiResponse)) {
                            aiResponse = "AI 回复为空";
                        }
                        chatHistoryService.addChatMessage(appId, loginUser.getId(), aiResponse,
                                ChatHistoryMessageTypeEnum.AI, userMessageId);
                    })
                    .doOnError(e -> chatHistoryService.addChatMessage(appId, loginUser.getId(),
                            "AI 回复失败：" + e.getMessage(), ChatHistoryMessageTypeEnum.AI, userMessageId));
        } catch (Exception e) {
            chatHistoryService.addChatMessage(appId, loginUser.getId(),
                    "AI 回复失败：" + e.getMessage(), ChatHistoryMessageTypeEnum.AI, userMessageId);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteApp(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用id无效");
        long historyCount = chatHistoryService.count(QueryWrapper.create().eq("appId", appId));
        if (historyCount > 0) {
            boolean removeHistoryResult = chatHistoryService.remove(QueryWrapper.create().eq("appId", appId));
            ThrowUtils.throwIf(!removeHistoryResult, ErrorCode.OPERATION_ERROR, "删除应用对话历史失败");
        }
        return this.removeById(appId);
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .like("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public QueryWrapper getMyQueryWrapper(AppUserQueryRequest appUserQueryRequest, Long userId) {
        if (appUserQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String appName = appUserQueryRequest.getAppName();
        String sortField = appUserQueryRequest.getSortField();
        String sortOrder = appUserQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("userId", userId)
                .like("appName", appName)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public QueryWrapper getFeaturedQueryWrapper(AppUserQueryRequest appUserQueryRequest) {
        if (appUserQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String appName = appUserQueryRequest.getAppName();
        String sortField = appUserQueryRequest.getSortField();
        String sortOrder = appUserQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .gt("priority", 0)
                .like("appName", appName);
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            queryWrapper.orderBy("priority", false)
                    .orderBy("createTime", false);
        }
        return queryWrapper;
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        return appList.stream().map(this::getAppVO).collect(Collectors.toList());
    }

    @Override
    public App getValidApp(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        App app = this.getById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return app;
    }

    @Override
    public void checkAppAuth(User loginUser, App app) {
        if (loginUser == null || app == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    @Override
    public void checkAppViewAuth(User loginUser, App app) {
        if (loginUser == null || app == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (app.getUserId().equals(loginUser.getId())) {
            return;
        }
        if (app.getPriority() != null && app.getPriority() > 0) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }

    @Override
    public void validUserPageParams(AppUserQueryRequest appUserQueryRequest) {
        if (appUserQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        int pageSize = appUserQueryRequest.getPageSize();
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页最多 " + MAX_PAGE_SIZE + " 个");
        }
    }

}
