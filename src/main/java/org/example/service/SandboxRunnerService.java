package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class SandboxRunnerService {

    private final boolean enabled;
    private final String dockerCommand;
    private final String pythonImage;
    private final String pythonEntrypoint;
    private final Path appRoot;
    private final String mountType;
    private final String volumeName;
    private final String hostRoot;
    private final String containerRoot;
    private final String networkMode;
    private final String cpus;
    private final String memory;
    private final int pidsLimit;
    private final int timeoutSeconds;
    private final int maxOutputChars;
    private final boolean keepRuns;

    public SandboxRunnerService(@Value("${sandbox.enabled:true}") boolean enabled,
                                @Value("${sandbox.docker-command:docker}") String dockerCommand,
                                @Value("${sandbox.python-image:python:3.11-slim}") String pythonImage,
                                @Value("${sandbox.python-entrypoint:python3}") String pythonEntrypoint,
                                @Value("${sandbox.app-root:./sandbox}") String appRoot,
                                @Value("${sandbox.mount-type:bind}") String mountType,
                                @Value("${sandbox.volume-name:superbiz-sandbox-data}") String volumeName,
                                @Value("${sandbox.host-root:./sandbox}") String hostRoot,
                                @Value("${sandbox.container-root:/sandbox}") String containerRoot,
                                @Value("${sandbox.network:none}") String networkMode,
                                @Value("${sandbox.cpus:1}") String cpus,
                                @Value("${sandbox.memory:512m}") String memory,
                                @Value("${sandbox.pids-limit:128}") int pidsLimit,
                                @Value("${sandbox.timeout-seconds:30}") int timeoutSeconds,
                                @Value("${sandbox.max-output-chars:12000}") int maxOutputChars,
                                @Value("${sandbox.keep-runs:false}") boolean keepRuns) {
        this.enabled = enabled;
        this.dockerCommand = dockerCommand;
        this.pythonImage = pythonImage;
        this.pythonEntrypoint = pythonEntrypoint;
        this.appRoot = Path.of(appRoot).toAbsolutePath().normalize();
        this.mountType = mountType;
        this.volumeName = volumeName;
        this.hostRoot = hostRoot;
        this.containerRoot = trimTrailingSlash(containerRoot);
        this.networkMode = networkMode;
        this.cpus = cpus;
        this.memory = memory;
        this.pidsLimit = pidsLimit;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputChars = maxOutputChars;
        this.keepRuns = keepRuns;
    }

    public SandboxResult runPythonScript(Path sourceScript, List<String> args) throws Exception {
        if (!enabled) {
            return new SandboxResult("", pythonImage, "", "", "Docker 沙箱已禁用。", -1, 0, false);
        }
        String runId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String containerName = "superbiz-sandbox-" + runId;
        Path runDir = appRoot.resolve("runs").resolve(runId).normalize();
        Path workDir = runDir.resolve("work").normalize();
        Files.createDirectories(workDir);

        copyScriptDirectory(sourceScript, workDir);
        makeWritableForSandbox(runDir);
        String scriptName = sourceScript.getFileName().toString();

        List<String> command = buildDockerCommand(containerName, runId, scriptName, args);
        long startedAt = System.currentTimeMillis();
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stdoutThread = drainAsync(process.getInputStream(), stdout);
        Thread stderrThread = drainAsync(process.getErrorStream(), stderr);

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            forceRemoveContainer(containerName);
            cleanupRun(runDir);
            long durationMs = System.currentTimeMillis() - startedAt;
            return new SandboxResult(runId,
                    pythonImage,
                    String.join(" ", command),
                    truncate(stdout.toString(StandardCharsets.UTF_8)),
                    stderr.toString(StandardCharsets.UTF_8) + "\n执行超时，已强制销毁沙箱容器。",
                    -1,
                    durationMs,
                    true);
        }

        stdoutThread.join(TimeUnit.SECONDS.toMillis(2));
        stderrThread.join(TimeUnit.SECONDS.toMillis(2));
        long durationMs = System.currentTimeMillis() - startedAt;
        SandboxResult result = new SandboxResult(
                runId,
                pythonImage,
                String.join(" ", command),
                truncate(stdout.toString(StandardCharsets.UTF_8)),
                truncate(stderr.toString(StandardCharsets.UTF_8)),
                process.exitValue(),
                durationMs,
                false);
        cleanupRun(runDir);
        return result;
    }

    public SandboxResult runInlinePython(String code, List<String> args) throws Exception {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code 不能为空");
        }
        String sourceId = "inline-" + UUID.randomUUID().toString().substring(0, 8);
        Path sourceDir = appRoot.resolve("sources").resolve(sourceId).normalize();
        Path script = sourceDir.resolve("main.py").normalize();
        Files.createDirectories(sourceDir);
        Files.writeString(script, code, StandardCharsets.UTF_8);
        try {
            return runPythonScript(script, args);
        } finally {
            cleanupRun(sourceDir);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("dockerCommand", dockerCommand);
        status.put("pythonImage", pythonImage);
        status.put("pythonEntrypoint", pythonEntrypoint);
        status.put("appRoot", appRoot.toString());
        status.put("mountType", mountType);
        status.put("volumeName", volumeName);
        status.put("hostRoot", hostRoot);
        status.put("containerRoot", containerRoot);
        status.put("networkMode", networkMode);
        status.put("cpus", cpus);
        status.put("memory", memory);
        status.put("pidsLimit", pidsLimit);
        status.put("timeoutSeconds", timeoutSeconds);
        status.put("keepRuns", keepRuns);
        status.put("mountSpec", resolveMountSpec());
        status.put("dockerAvailable", checkDockerAvailable());
        status.put("imageAvailable", checkImageAvailable());
        return status;
    }

    private boolean checkDockerAvailable() {
        try {
            Process process = new ProcessBuilder(dockerCommand, "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkImageAvailable() {
        try {
            Process process = new ProcessBuilder(dockerCommand, "image", "inspect", pythonImage)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> buildDockerCommand(String containerName, String runId, String scriptName, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(dockerCommand);
        command.add("run");
        command.add("--rm");
        command.add("--name");
        command.add(containerName);
        command.add("--network");
        command.add(StringUtils.hasText(networkMode) ? networkMode : "none");
        command.add("--cpus");
        command.add(cpus);
        command.add("--memory");
        command.add(memory);
        command.add("--pids-limit");
        command.add(String.valueOf(pidsLimit));
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp:rw,noexec,nosuid,size=64m");
        command.add("-v");
        command.add(resolveMountSpec());
        command.add("-w");
        command.add(containerRoot + "/runs/" + runId + "/work");
        command.add("--entrypoint");
        command.add(pythonEntrypoint);
        command.add(pythonImage);
        command.add(scriptName);
        if (args != null) {
            args.stream()
                    .filter(arg -> arg != null)
                    .forEach(command::add);
        }
        return command;
    }

    private String resolveMountSpec() {
        if ("volume".equalsIgnoreCase(mountType)) {
            return volumeName + ":" + containerRoot + ":rw";
        }
        Path root = StringUtils.hasText(hostRoot) ? Path.of(hostRoot).toAbsolutePath().normalize() : appRoot;
        return root + ":" + containerRoot + ":rw";
    }

    private void copyScriptDirectory(Path sourceScript, Path workDir) throws Exception {
        Path scriptDir = sourceScript.getParent();
        try (Stream<Path> stream = Files.list(scriptDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                Path target = workDir.resolve(file.getFileName().toString()).normalize();
                if (!target.startsWith(workDir)) {
                    throw new IllegalArgumentException("非法脚本文件路径: " + file.getFileName());
                }
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void makeWritableForSandbox(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.toList()) {
                var file = path.toFile();
                file.setReadable(true, false);
                file.setWritable(true, false);
                if (Files.isDirectory(path)) {
                    file.setExecutable(true, false);
                }
            }
        }
    }

    private void forceRemoveContainer(String containerName) {
        try {
            new ProcessBuilder(dockerCommand, "rm", "-f", containerName)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Best-effort cleanup. The timeout result is still returned to the model.
        }
    }

    private void cleanupRun(Path runDir) {
        if (keepRuns) {
            return;
        }
        try {
            if (!runDir.startsWith(appRoot) || !Files.exists(runDir)) {
                return;
            }
            try (Stream<Path> stream = Files.walk(runDir)) {
                for (Path path : stream.sorted((left, right) -> right.compareTo(left)).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (Exception ignored) {
            // Sandbox run directories are temporary; failed cleanup can be handled by scheduled maintenance later.
        }
    }

    private Thread drainAsync(InputStream inputStream, ByteArrayOutputStream outputStream) {
        Thread thread = new Thread(() -> {
            try (inputStream) {
                inputStream.transferTo(outputStream);
            } catch (Exception ignored) {
                // Best-effort output capture; the process result still carries exit code.
            }
        }, "sandbox-output-drain");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= maxOutputChars) {
            return value;
        }
        return value.substring(0, maxOutputChars) + "\n...输出已截断";
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value) || "/".equals(value)) {
            return "/sandbox";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record SandboxResult(String runId, String image, String command, String stdout, String stderr,
                                int exitCode, long durationMs, boolean timeout) {
    }
}
