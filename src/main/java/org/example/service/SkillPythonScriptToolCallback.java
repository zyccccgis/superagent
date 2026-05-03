package org.example.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SkillPythonScriptToolCallback implements ToolCallback {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "skill_name": {
                  "type": "string",
                  "description": "已启用 Skill 的名称，例如 webapp-testing"
                },
                "script_path": {
                  "type": "string",
                  "description": "Skill scripts 目录下的 Python 脚本路径，例如 scripts/with_server.py 或 with_server.py"
                },
                "args": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "传给脚本的命令行参数。先用 [\\"--help\\"] 查看用法。"
                }
              },
              "required": ["skill_name", "script_path"],
              "additionalProperties": false
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillService skillService;
    private final boolean enabled;
    private final String pythonCommand;
    private final int timeoutSeconds;
    private final int maxOutputChars;
    private final ToolDefinition toolDefinition;

    public SkillPythonScriptToolCallback(SkillService skillService,
                                         @Value("${skills.script-runner.enabled:true}") boolean enabled,
                                         @Value("${skills.script-runner.python-command:python3}") String pythonCommand,
                                         @Value("${skills.script-runner.timeout-seconds:30}") int timeoutSeconds,
                                         @Value("${skills.script-runner.max-output-chars:12000}") int maxOutputChars) {
        this.skillService = skillService;
        this.enabled = enabled;
        this.pythonCommand = pythonCommand;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputChars = maxOutputChars;
        this.toolDefinition = ToolDefinition.builder()
                .name("run_skill_python_script")
                .description("运行已启用 Skill 目录中 scripts/ 下的 Python 脚本。只能执行 Skill 自带 .py 脚本，适合按 SKILL.md 指令调用 with_server.py、quick_validate.py、evaluation.py 等黑盒脚本。")
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        if (!enabled) {
            return "run_skill_python_script 已禁用。";
        }
        try {
            RunSkillPythonScriptRequest request = objectMapper.readValue(toolInput, RunSkillPythonScriptRequest.class);
            validateRequest(request);
            Path script = skillService.resolveEnabledSkillScript(request.skillName(), request.scriptPath());
            Path skillRoot = skillService.getSkillRoot(request.skillName());

            List<String> command = new ArrayList<>();
            command.add(pythonCommand);
            command.add(script.toString());
            if (request.args() != null) {
                request.args().stream()
                        .filter(arg -> arg != null)
                        .forEach(command::add);
            }

            Process process = new ProcessBuilder(command)
                    .directory(skillRoot.toFile())
                    .redirectErrorStream(false)
                    .start();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread stdoutThread = drainAsync(process.getInputStream(), stdout);
            Thread stderrThread = drainAsync(process.getErrorStream(), stderr);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Skill 脚本执行超时，已终止。timeout=" + timeoutSeconds + "s";
            }
            stdoutThread.join(TimeUnit.SECONDS.toMillis(2));
            stderrThread.join(TimeUnit.SECONDS.toMillis(2));

            String output = stdout.toString(StandardCharsets.UTF_8);
            String error = stderr.toString(StandardCharsets.UTF_8);
            return formatResult(process.exitValue(), output, error);
        } catch (Exception e) {
            return "Skill 脚本执行失败: " + e.getMessage();
        }
    }

    private void validateRequest(RunSkillPythonScriptRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (!StringUtils.hasText(request.skillName())) {
            throw new IllegalArgumentException("skill_name 不能为空");
        }
        if (!StringUtils.hasText(request.scriptPath())) {
            throw new IllegalArgumentException("script_path 不能为空");
        }
    }

    private String formatResult(int exitCode, String stdout, String stderr) {
        StringBuilder result = new StringBuilder();
        result.append("exit_code: ").append(exitCode).append("\n");
        if (StringUtils.hasText(stdout)) {
            result.append("\nstdout:\n").append(stdout.strip()).append("\n");
        }
        if (StringUtils.hasText(stderr)) {
            result.append("\nstderr:\n").append(stderr.strip()).append("\n");
        }
        if (result.length() > maxOutputChars) {
            return result.substring(0, maxOutputChars) + "\n...输出已截断";
        }
        return result.toString();
    }

    private Thread drainAsync(InputStream inputStream, ByteArrayOutputStream outputStream) {
        Thread thread = new Thread(() -> {
            try (inputStream) {
                inputStream.transferTo(outputStream);
            } catch (Exception ignored) {
                // Best-effort output capture; the process result still carries exit code.
            }
        }, "skill-python-script-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RunSkillPythonScriptRequest {
        public String skillName;
        public String skill_name;
        public String scriptPath;
        public String script_path;
        public List<String> args;

        String skillName() {
            return StringUtils.hasText(skillName) ? skillName : skill_name;
        }

        String scriptPath() {
            return StringUtils.hasText(scriptPath) ? scriptPath : script_path;
        }

        List<String> args() {
            return args;
        }
    }
}
