package org.example.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.service.SandboxRunnerService;

@Getter
@Setter
public class SandboxRunResponse {
    private String runId;
    private String image;
    private String command;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long durationMs;
    private Boolean timeout;

    public static SandboxRunResponse from(SandboxRunnerService.SandboxResult result) {
        SandboxRunResponse response = new SandboxRunResponse();
        response.setRunId(result.runId());
        response.setImage(result.image());
        response.setCommand(result.command());
        response.setStdout(result.stdout());
        response.setStderr(result.stderr());
        response.setExitCode(result.exitCode());
        response.setDurationMs(result.durationMs());
        response.setTimeout(result.timeout());
        return response;
    }
}
