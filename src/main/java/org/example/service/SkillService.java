package org.example.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.SkillDetailResponse;
import org.example.dto.SkillInstallRequest;
import org.example.dto.SkillListResponse;
import org.example.dto.SkillResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SkillService {

    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);
    private static final long MAX_ZIP_BYTES = 20L * 1024L * 1024L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final Path skillsRoot;
    private final Path installedRoot;
    private final Path registryPath;

    public SkillService(@Value("${skills.base-path:./skills}") String skillsBasePath) {
        this.skillsRoot = Path.of(skillsBasePath).toAbsolutePath().normalize();
        this.installedRoot = skillsRoot.resolve("installed").normalize();
        this.registryPath = skillsRoot.resolve("registry.json").normalize();
    }

    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            Files.createDirectories(installedRoot);
            if (!Files.exists(registryPath)) {
                writeRegistry(new SkillRegistry());
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化 Skills 目录失败: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SkillListResponse listSkills() {
        lock.readLock().lock();
        try {
            SkillRegistry registry = readRegistry();
            List<SkillResponse> items = registry.skills.values().stream()
                    .sorted(Comparator.comparing(SkillRecord::getName))
                    .map(this::toResponse)
                    .toList();
            SkillListResponse response = new SkillListResponse();
            response.setItems(items);
            response.setTotal(items.size());
            return response;
        } catch (Exception e) {
            throw new IllegalStateException("查询 Skills 失败: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public SkillDetailResponse getSkill(String name) {
        lock.readLock().lock();
        try {
            SkillRecord record = requireSkill(readRegistry(), normalizeSkillName(name));
            SkillDetailResponse response = toDetailResponse(record);
            response.setContent(Files.readString(skillMdPath(record), StandardCharsets.UTF_8));
            return response;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取 Skill 失败: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public SkillResponse installSkill(SkillInstallRequest request) {
        if (request == null || !StringUtils.hasText(request.getSourceUrl())) {
            throw new IllegalArgumentException("sourceUrl 不能为空");
        }
        lock.writeLock().lock();
        try {
            Path zipPath = downloadZip(request.getSourceUrl().trim());
            Path extractDir = Files.createTempDirectory("skill-install-");
            try {
                unzip(zipPath, extractDir);
                Path skillMd = findSkillMd(extractDir);
                String content = Files.readString(skillMd, StandardCharsets.UTF_8);
                Map<String, String> metadata = parseFrontmatter(content);

                String name = normalizeSkillName(metadata.getOrDefault("name", skillMd.getParent().getFileName().toString()));
                boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());
                Path targetDir = installedRoot.resolve(name).normalize();
                if (!targetDir.startsWith(installedRoot)) {
                    throw new IllegalArgumentException("Skill 名称不合法");
                }
                if (Files.exists(targetDir) && !overwrite) {
                    throw new IllegalArgumentException("Skill 已存在: " + name);
                }
                if (Files.exists(targetDir)) {
                    deleteRecursively(targetDir);
                }
                copyDirectory(skillMd.getParent(), targetDir);

                long now = Instant.now().toEpochMilli();
                SkillRegistry registry = readRegistry();
                SkillRecord record = registry.skills.getOrDefault(name, new SkillRecord());
                record.name = name;
                record.displayName = metadata.getOrDefault("displayName", metadata.getOrDefault("title", name));
                record.description = metadata.getOrDefault("description", "");
                record.version = metadata.getOrDefault("version", "");
                record.author = metadata.getOrDefault("author", "");
                record.sourceType = sourceType(request.getSourceUrl());
                record.sourceUrl = request.getSourceUrl().trim();
                record.enabled = record.enabled == null || record.enabled;
                record.installedAt = record.installedAt == null ? now : record.installedAt;
                record.updatedAt = now;
                registry.skills.put(name, record);
                writeRegistry(registry);
                return toResponse(record);
            } finally {
                Files.deleteIfExists(zipPath);
                deleteRecursively(extractDir);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("安装 Skill 失败: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SkillResponse setEnabled(String name, Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        lock.writeLock().lock();
        try {
            SkillRegistry registry = readRegistry();
            SkillRecord record = requireSkill(registry, normalizeSkillName(name));
            record.enabled = enabled;
            record.updatedAt = Instant.now().toEpochMilli();
            writeRegistry(registry);
            return toResponse(record);
        } catch (Exception e) {
            throw new IllegalArgumentException("更新 Skill 开关失败: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteSkill(String name) {
        lock.writeLock().lock();
        try {
            String normalizedName = normalizeSkillName(name);
            SkillRegistry registry = readRegistry();
            requireSkill(registry, normalizedName);
            registry.skills.remove(normalizedName);
            deleteRecursively(installedRoot.resolve(normalizedName).normalize());
            writeRegistry(registry);
        } catch (Exception e) {
            throw new IllegalArgumentException("删除 Skill 失败: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String buildSkillIndexPrompt() {
        lock.readLock().lock();
        try {
            SkillRegistry registry = readRegistry();
            List<SkillRecord> enabledSkills = registry.skills.values().stream()
                    .filter(this::isEnabledAndInstalled)
                    .sorted(Comparator.comparing(SkillRecord::getName))
                    .toList();
            if (enabledSkills.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("--- 可用 Skills ---\n")
                    .append("你可以根据用户问题自主决定是否调用 read_skill 工具加载某个 Skill 的完整说明。")
                    .append("不要仅因为 Skill 存在就调用，只有当任务与 description 明确相关时再调用。\n");
            for (SkillRecord skill : enabledSkills) {
                builder.append("- ")
                        .append(skill.name)
                        .append(": ")
                        .append(StringUtils.hasText(skill.description) ? skill.description : "无描述")
                        .append('\n');
            }
            builder.append("--- Skills 列表结束 ---\n\n");
            return builder.toString();
        } catch (Exception e) {
            logger.warn("构建 Skills 索引失败", e);
            return "";
        } finally {
            lock.readLock().unlock();
        }
    }

    public ToolCallback[] buildSkillToolCallbacks() {
        if (!hasEnabledSkills()) {
            return new ToolCallback[0];
        }
        return new ToolCallback[]{new ReadSkillToolCallback()};
    }

    private boolean hasEnabledSkills() {
        lock.readLock().lock();
        try {
            return readRegistry().skills.values().stream().anyMatch(this::isEnabledAndInstalled);
        } catch (Exception e) {
            logger.warn("检查 Skills 可用性失败", e);
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean isEnabledAndInstalled(SkillRecord record) {
        return record != null
                && record.enabled != null
                && record.enabled
                && StringUtils.hasText(record.name)
                && Files.exists(skillMdPath(record));
    }

    private String readSkillForTool(String toolInput) {
        try {
            String skillName = extractSkillName(toolInput);
            if (!StringUtils.hasText(skillName)) {
                return "缺少 skill_name 参数。请传入需要加载的 Skill 名称。";
            }
            return readSkillContent(normalizeSkillName(skillName));
        } catch (Exception e) {
            logger.warn("read_skill 调用失败, input: {}", toolInput, e);
            return "读取 Skill 失败: " + e.getMessage();
        }
    }

    private String extractSkillName(String toolInput) throws Exception {
        if (!StringUtils.hasText(toolInput)) {
            return "";
        }
        String trimmed = toolInput.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        Map<?, ?> payload = objectMapper.readValue(trimmed, Map.class);
        Object value = payload.get("skill_name");
        if (value == null) {
            value = payload.get("name");
        }
        if (value == null) {
            value = payload.get("skillName");
        }
        return value == null ? "" : value.toString();
    }

    private String readSkillContent(String name) throws Exception {
        lock.readLock().lock();
        try {
            SkillRegistry registry = readRegistry();
            SkillRecord record = requireSkill(registry, name);
            if (!isEnabledAndInstalled(record)) {
                return "Skill 当前未启用或 SKILL.md 不存在: " + name;
            }
            Path skillMd = skillMdPath(record);
            return "--- Skill: " + record.name + " ---\n"
                    + "description: " + (StringUtils.hasText(record.description) ? record.description : "") + "\n"
                    + "base_path: " + installedRoot.resolve(record.name).normalize() + "\n\n"
                    + Files.readString(skillMd, StandardCharsets.UTF_8)
                    + "\n--- Skill 结束 ---";
        } finally {
            lock.readLock().unlock();
        }
    }

    private Path downloadZip(String sourceUrl) throws Exception {
        URI uri = URI.create(sourceUrl);
        if (uri.getScheme() == null || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("sourceUrl 必须是 http/https ZIP 地址");
        }
        Path zipPath = Files.createTempFile("skill-", ".zip");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "SuperBizAgent/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("下载失败: HTTP " + response.statusCode());
        }
        try (InputStream inputStream = response.body()) {
            Files.copy(inputStream, zipPath, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(zipPath) > MAX_ZIP_BYTES) {
            throw new IllegalArgumentException("ZIP 文件过大，最大允许 20MB");
        }
        return zipPath;
    }

    private void unzip(Path zipPath, Path targetDir) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IllegalArgumentException("ZIP 包含非法路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zipInputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path findSkillMd(Path extractDir) throws Exception {
        try (Stream<Path> stream = Files.walk(extractDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> "SKILL.md".equals(path.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("ZIP 中没有 SKILL.md"));
        }
    }

    private Map<String, String> parseFrontmatter(String content) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (content == null || !content.startsWith("---")) {
            return metadata;
        }
        String[] lines = content.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if ("---".equals(line.trim())) {
                break;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            metadata.put(key, value);
        }
        return metadata;
    }

    private String normalizeSkillName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new IllegalArgumentException("Skill 名称不能为空");
        }
        String name = rawName.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        name = name.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (!name.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Skill 名称不合法: " + rawName);
        }
        return name;
    }

    private String sourceType(String sourceUrl) {
        return sourceUrl.contains("github.com") ? "github_zip" : "zip";
    }

    private SkillRecord requireSkill(SkillRegistry registry, String name) {
        SkillRecord record = registry.skills.get(name);
        if (record == null) {
            throw new IllegalArgumentException("Skill 不存在: " + name);
        }
        return record;
    }

    private Path skillMdPath(SkillRecord record) {
        return installedRoot.resolve(record.name).resolve("SKILL.md").normalize();
    }

    private SkillResponse toResponse(SkillRecord record) {
        SkillResponse response = new SkillResponse();
        fillResponse(response, record);
        return response;
    }

    private SkillDetailResponse toDetailResponse(SkillRecord record) {
        SkillDetailResponse response = new SkillDetailResponse();
        fillResponse(response, record);
        return response;
    }

    private void fillResponse(SkillResponse response, SkillRecord record) {
        response.setName(record.name);
        response.setDisplayName(record.displayName);
        response.setDescription(record.description);
        response.setVersion(record.version);
        response.setAuthor(record.author);
        response.setSourceType(record.sourceType);
        response.setSourceUrl(record.sourceUrl);
        response.setEnabled(record.enabled != null && record.enabled);
        response.setInstalledAt(record.installedAt);
        response.setUpdatedAt(record.updatedAt);
        response.setSize(directorySize(installedRoot.resolve(record.name).normalize()));
    }

    private long directorySize(Path dir) {
        if (!Files.exists(dir)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (Exception e) {
                    return 0L;
                }
            }).sum();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void copyDirectory(Path source, Path target) throws Exception {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target)) {
                    throw new IllegalArgumentException("非法目标路径");
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (!path.normalize().startsWith(skillsRoot) && !path.getFileName().toString().startsWith("skill-install-")) {
            throw new IllegalArgumentException("拒绝删除非 Skills 目录: " + path);
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private SkillRegistry readRegistry() throws Exception {
        if (!Files.exists(registryPath)) {
            return new SkillRegistry();
        }
        SkillRegistry registry = objectMapper.readValue(Files.readString(registryPath, StandardCharsets.UTF_8), SkillRegistry.class);
        if (registry.skills == null) {
            registry.skills = new LinkedHashMap<>();
        }
        return registry;
    }

    private void writeRegistry(SkillRegistry registry) throws Exception {
        Files.createDirectories(skillsRoot);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), registry);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillRegistry {
        public Map<String, SkillRecord> skills = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillRecord {
        public String name;
        public String displayName;
        public String description;
        public String version;
        public String author;
        public String sourceType;
        public String sourceUrl;
        public Boolean enabled = true;
        public Long installedAt;
        public Long updatedAt;

        public String getName() {
            return name;
        }
    }

    private class ReadSkillToolCallback implements ToolCallback {

        private final ToolDefinition toolDefinition = ToolDefinition.builder()
                .name("read_skill")
                .description("""
                        Load the full SKILL.md content for one enabled Skill by name. Use this when the user's task clearly matches one of the available Skill descriptions in the system prompt. The input must be JSON like {"skill_name":"frontend-design"}.
                        """)
                .inputSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "skill_name": {
                              "type": "string",
                              "description": "The exact enabled Skill name to load."
                            }
                          },
                          "required": ["skill_name"]
                        }
                        """)
                .build();

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return readSkillForTool(toolInput);
        }
    }
}
