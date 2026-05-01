package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionInfoResponse {
    private String sessionId;
    private int messagePairCount;
    private long createTime;
}
