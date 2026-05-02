package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkillResponse {
    private String name;
    private String displayName;
    private String description;
    private String version;
    private String author;
    private String sourceType;
    private String sourceUrl;
    private Boolean enabled;
    private Long installedAt;
    private Long updatedAt;
    private Long size;
}
