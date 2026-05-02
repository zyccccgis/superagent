package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkillInstallRequest {
    private String sourceUrl;
    private Boolean overwrite;
}
