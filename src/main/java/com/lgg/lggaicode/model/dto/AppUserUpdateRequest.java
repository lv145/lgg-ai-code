package com.lgg.lggaicode.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新自己的应用请求
 */
@Data
public class AppUserUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}
