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
import com.lgg.lggaicode.model.vo.ChatHistoryVO;
import com.lgg.lggaicode.service.AppService;
import com.lgg.lggaicode.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.lgg.lggaicode.service.ChatHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
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
     * 分页查询某个应用的对话历史（仅应用创建者和管理员可见）
     */
    @PostMapping("/app/list/page/vo")
    public BaseResponse<Page<ChatHistoryVO>> listAppChatHistoryVOByPage(
            @RequestBody ChatHistoryAppQueryRequest chatHistoryAppQueryRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(chatHistoryAppQueryRequest == null, ErrorCode.PARAMS_ERROR);
        chatHistoryService.validAppPageParams(chatHistoryAppQueryRequest);
        User loginUser = userService.getLoginUser(request);
        App app = appService.getValidApp(chatHistoryAppQueryRequest.getAppId());
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        boolean isAppCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isAppCreator, ErrorCode.NO_AUTH_ERROR);
        int pageNum = chatHistoryAppQueryRequest.getPageNum();
        int pageSize = chatHistoryAppQueryRequest.getPageSize();
        Page<ChatHistory> page = chatHistoryService.page(
                Page.of(pageNum, pageSize),
                chatHistoryService.getAppQueryWrapper(chatHistoryAppQueryRequest));
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        List<ChatHistoryVO> chatHistoryVOList = chatHistoryService.getChatHistoryVOList(page.getRecords());
        Collections.reverse(chatHistoryVOList);
        chatHistoryVOPage.setRecords(chatHistoryVOList);
        return ResultUtils.success(chatHistoryVOPage);
    }

    /**
     * 管理员分页查询所有对话历史
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryVOByPage(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = chatHistoryQueryRequest.getPageNum();
        int pageSize = chatHistoryQueryRequest.getPageSize();
        Page<ChatHistory> page = chatHistoryService.page(
                Page.of(pageNum, pageSize),
                chatHistoryService.getQueryWrapper(chatHistoryQueryRequest));
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        List<ChatHistoryVO> chatHistoryVOList = chatHistoryService.getChatHistoryVOList(page.getRecords());
        chatHistoryVOPage.setRecords(chatHistoryVOList);
        return ResultUtils.success(chatHistoryVOPage);
    }

}
