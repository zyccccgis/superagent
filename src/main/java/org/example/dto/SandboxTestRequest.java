package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SandboxTestRequest {
    private String code;
    private List<String> args;
}
