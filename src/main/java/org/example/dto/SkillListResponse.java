package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SkillListResponse {
    private List<SkillResponse> items;
    private int total;
}
