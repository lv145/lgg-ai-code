package com.lgg.lggaicode.service;

import com.lgg.lggaicode.model.dto.AppAddRequest;
import com.lgg.lggaicode.model.dto.AppQueryRequest;
import com.lgg.lggaicode.model.dto.AppUserQueryRequest;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;

import java.net.http.HttpRequest;
import java.util.List;

/**
 * 应用 服务层。
 */
public interface AppService extends IService<App> {
    /**
     * 部署应用
     * @param appId
     * @param loginUser
     * @return
     */
    String deployApp(Long appId,User loginUser);
    /**
     * 聊天生成代码
     * @param appId
     * @param userMessage
     * @param loginUser
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String userMessage, User loginUser);

    /**
     * 删除应用并清理关联对话历史
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    boolean deleteApp(Long appId);

    /**
     * 管理员查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 用户查询自己的应用条件
     */
    QueryWrapper getMyQueryWrapper(AppUserQueryRequest appUserQueryRequest, Long userId);

    /**
     * 精选应用查询条件
     */
    QueryWrapper getFeaturedQueryWrapper(AppUserQueryRequest appUserQueryRequest);

    /**
     * 获取应用视图对象
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用视图对象列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 校验应用是否存在
     */
    App getValidApp(Long id);

    /**
     * 校验用户是否有权限操作该应用（仅创建者可操作）
     */
    void checkAppAuth(User loginUser, App app);

    /**
     * 校验用户是否有权限查看该应用（创建者或精选应用）
     */
    void checkAppViewAuth(User loginUser, App app);

    /**
     * 校验用户分页参数
     */
    void validUserPageParams(AppUserQueryRequest appUserQueryRequest);

    /**
     * 创建应用
     * @param appAddRequest
     * @param loginUser
     * @return 应用id
     */

    Long createApp(AppAddRequest appAddRequest, User loginUser);
}
