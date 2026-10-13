package com.lgg.lggaicode.model.dto;

import com.lgg.lggaicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户查询应用列表请求（我的应用 / 精选应用）
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppUserQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}
