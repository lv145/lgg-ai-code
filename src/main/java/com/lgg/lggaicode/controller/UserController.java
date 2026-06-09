package com.lgg.lggaicode.controller;

import com.lgg.lggaicode.annotation.AuthCheck;
import com.lgg.lggaicode.common.BaseResponse;
import com.lgg.lggaicode.common.DeleteRequest;
import com.lgg.lggaicode.common.PageRequest;
import com.lgg.lggaicode.common.ResultUtils;
import com.lgg.lggaicode.constant.UserConstant;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.exception.ThrowUtils;
import com.lgg.lggaicode.model.dto.*;
import com.lgg.lggaicode.model.vo.LoginUserVO;
import com.lgg.lggaicode.model.vo.UserVO;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.lgg.lggaicode.model.entity.User;
import com.lgg.lggaicode.service.UserService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 用户 控制层。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest==null,ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(userAddRequest,user);
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public  BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest){
        ThrowUtils.throwIf(deleteRequest==null||deleteRequest.getId()==null,ErrorCode.PARAMS_ERROR);
        boolean result = userService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest){
        ThrowUtils.throwIf(userUpdateRequest==null||userUpdateRequest.getId()==null,ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest,user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id){
        ThrowUtils.throwIf(id<=0,ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user==null,ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);

    }
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id){
        User user = getUserById(id).getData();
        return ResultUtils.success(userService.getUserVO(user));
    }


    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest==null, ErrorCode.PARAMS_ERROR);
        long result = userService.userRegister(userRegisterRequest.getUserAccount(),
                userRegisterRequest.getUserPassword(), userRegisterRequest.getCheckPassword());
        return ResultUtils.success(result);
    }
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> login(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest==null, ErrorCode.PARAMS_ERROR);
        LoginUserVO loginUserVO = userService.userLogin(userLoginRequest.getUserAccount(), userLoginRequest.getUserPassword(), request);
        return ResultUtils.success(loginUserVO);
    }
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest==null, ErrorCode.PARAMS_ERROR);
        int pageNum = userQueryRequest.getPageNum();
        int pageSize = userQueryRequest.getPageSize();
        Page<User> page = userService.page(
                Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        //数据脱敏
        Page<UserVO> userVOPage=new Page<>(pageNum,pageSize,page.getTotalRow());
        List<UserVO> userVOList = userService.getUserVOList(page.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);

    }

}
