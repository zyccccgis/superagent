package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ToolResponse {
    private String toolName;
    private String displayName;
    private String description;
    private String sourceClass;
    private String riskLevel;
    private Boolean enabled;
    private Boolean available;
    private Long updatedAt;
}
