package com.lgg.lggaicode.controller;

import com.lgg.lggaicode.annotation.AuthCheck;
import com.lgg.lggaicode.common.BaseResponse;
import com.lgg.lggaicode.common.ResultUtils;
import com.lgg.lggaicode.constant.UserConstant;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.exception.ThrowUtils;
import com.lgg.lggaicode.model.dto.ChatHistoryAppQueryRequest;
import com.lgg.lggaicode.model.dto.ChatHistoryQueryRequest;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.entity.ChatHistory;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.model.enums.UserRoleEnum;
import com.lgg.lggaicode.service.AppService;
import com.lgg.lggaicode.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import com.lgg.lggaicode.service.ChatHistoryService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
@Slf4j
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private AppService appService;

    /**
     * 分页查询某个应用的对话历史游标查询（仅应用创建者和管理员可见）
     */
    @PostMapping("/app/list/page")
    public BaseResponse<Page<ChatHistory>> listAppChatHistoryByPage(
            @RequestParam Long appId, @RequestParam(required = false) LocalDateTime lastCreateTime,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, "应用不存在");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null ||loginUser.getId()<=0, ErrorCode.PARAMS_ERROR, "用户id无效");
        log.info("appId:{}, loginUser:{} ,{}", app.getUserId(), loginUser.getId(),app.getUserId().equals(loginUser.getId()));
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限,应用创建者才能查询");
        ChatHistoryAppQueryRequest chatHistoryAppQueryRequest = new ChatHistoryAppQueryRequest(
                appId, loginUser.getId(), lastCreateTime, pageSize);
        Page<ChatHistory> chatHistoryPage = chatHistoryService.page(
                Page.of(1, pageSize),
                chatHistoryService.getAppQueryWrapper(chatHistoryAppQueryRequest));
        return ResultUtils.success(chatHistoryPage);
    }

    /**
     * 管理员分页查询所有对话历史
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listChatHistoryByPage(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = chatHistoryQueryRequest.getPageNum();
        int pageSize = chatHistoryQueryRequest.getPageSize();
        Page<ChatHistory> page = chatHistoryService.page(
                Page.of(pageNum, pageSize),
                chatHistoryService.getQueryWrapper(chatHistoryQueryRequest));
        return ResultUtils.success(page);
    }

}
