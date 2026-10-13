package com.lgg.lggaicode.model.dto;

import lombok.Data;

/**
 * 停止流式生成请求。
 *
 * 备注：requestId 必须和开始流式生成时传入的 requestId 一致。
 */
@Data
public class StreamStopRequest {

    private Long appId;

    private String requestId;
}
