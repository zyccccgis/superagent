package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpToolResponse {
    private Long serverId;
    private String serverName;
    private String toolName;
    private String description;
}
