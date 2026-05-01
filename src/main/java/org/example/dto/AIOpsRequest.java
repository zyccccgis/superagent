package org.example.dto;

import lombok.Data;

/**
 * AIOps 任务创建请求 DTO
 */
@Data
public class AIOpsRequest {

    /**
     * 用户补充的任务描述，可为空
     */
    private String userRequest;

    /**
     * 会话ID，可为空
     */
    private String sessionId;

    /**
     * 触发来源，默认 MANUAL
     */
    private String triggerSource;

    /**
     * 创建人/调用方，可为空
     */
    private String createdBy;
}
