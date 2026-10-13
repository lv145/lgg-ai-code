package com.lgg.lggaicode.model.dto;

import lombok.Data;


import java.io.Serializable;
@Data
public class AppDeployRequest implements Serializable {
    /**
     * 应用ID
     */
    private Long appId;

    public static final Long  serialVersionUID = 1L;
}
