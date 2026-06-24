package com.lgg.lggaicode.model.dto;

import com.lgg.lggaicode.common.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用对话历史查询请求
 */
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@Data
public class ChatHistoryAppQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;
    /**
     * 用户 id
     */
    private Long userId;
    /**
     * 最近创建时间戳
     */
    private LocalDateTime lastCreateTime;
    /**
     * 页面大小
     */
    private int pageSize;

    private static final long serialVersionUID = 1L;
}
