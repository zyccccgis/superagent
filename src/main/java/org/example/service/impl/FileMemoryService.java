package org.example.service.impl;

import org.example.dto.MemoryContext;
import org.example.dto.MemoryFileListResponse;
import org.example.dto.MemoryFileRequest;
import org.example.dto.MemoryFileResponse;
import org.example.service.AgentExecutionMemoryService;
import org.example.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class FileMemoryService implements MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(FileMemoryService.class);
    private static final String INDEX_FILE = "MEMORY.md";
    private static final int MAX_TOPIC_FILES = 3;

    private final AgentExecutionMemoryService executionMemoryService;
    private final Path memoryRoot;
    private final int maxIndexLines;
    private final int shortMemoryPairs;

    public FileMemoryService(AgentExecutionMemoryService executionMemoryService,
                             @Value("${memory.base-path:./memory}") String memoryBasePath,
                             @Value("${memory.max-index-lines:200}") int maxIndexLines,
                             @Value("${memory.short-memory-pairs:6}") int shortMemoryPairs) {
        this.executionMemoryService = executionMemoryService;
        this.memoryRoot = Paths.get(memoryBasePath).normalize();
        this.maxIndexLines = maxIndexLines;
        this.shortMemoryPairs = shortMemoryPairs;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(memoryRoot.resolve("topics"));
        Path indexPath = memoryRoot.resolve(INDEX_FILE);
        if (!Files.exists(indexPath)) {
            Files.writeString(indexPath, "# MEMORY Index\n\n## Topics\n", StandardCharsets.UTF_8);
        }
    }

    @Override
    public MemoryContext loadContext(String sessionId, String question, List<Map<String, String>> recentHistory) {
        MemoryContext context = new MemoryContext();
        String memoryIndex = readStringIfExists(memoryRoot.resolve(INDEX_FILE));
        context.setMemoryIndex(memoryIndex);
        context.setTopicFiles(selectTopicFiles(memoryIndex, question));
        context.setShortMemory(buildShortMemory(sessionId, recentHistory));
        return context;
    }

    @Override
    public MemoryFileListResponse listFiles(String type, String keyword) {
        ensureRoot();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedType = type == null ? "all" : type.trim();
        List<MemoryFileResponse> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(memoryRoot, 2)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .map(this::toResponseWithoutContent)
                    .filter(file -> "all".equalsIgnoreCase(normalizedType) || file.getType().equalsIgnoreCase(normalizedType))
                    .filter(file -> normalizedKeyword.isEmpty()
                            || file.getPath().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                    .sorted(Comparator.comparing(MemoryFileResponse::getPath))
                    .forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("读取记忆文件列表失败", e);
        }

        MemoryFileListResponse response = new MemoryFileListResponse();
        response.setItems(files);
        response.setTotal(files.size());
        return response;
    }

    @Override
    public MemoryFileResponse readFile(String path) {
        Path resolved = resolveAllowedPath(path);
        MemoryFileResponse response = toResponseWithoutContent(resolved);
        response.setContent(readStringIfExists(resolved));
        return response;
    }

    @Override
    public MemoryFileResponse createFile(MemoryFileRequest request) {
        Path resolved = resolveAllowedPath(requiredPath(request));
        if (Files.exists(resolved)) {
            throw new IllegalArgumentException("记忆文件已存在");
        }
        writeFile(resolved, request.getContent());
        return readFile(request.getPath());
    }

    @Override
    public MemoryFileResponse updateFile(MemoryFileRequest request) {
        Path resolved = resolveAllowedPath(requiredPath(request));
        writeFile(resolved, request.getContent());
        return readFile(request.getPath());
    }

    @Override
    public void deleteFile(String path) {
        if (INDEX_FILE.equals(normalizeRelativePath(path))) {
            throw new IllegalArgumentException("MEMORY.md 索引文件不能删除");
        }
        Path resolved = resolveAllowedPath(path);
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException e) {
            throw new IllegalStateException("删除记忆文件失败", e);
        }
    }

    private List<MemoryFileResponse> selectTopicFiles(String memoryIndex, String question) {
        List<String> topicPaths = extractTopicPaths(memoryIndex);
        String normalizedQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<MemoryFileResponse> selected = new ArrayList<>();
        for (String path : topicPaths) {
            if (selected.size() >= MAX_TOPIC_FILES) {
                break;
            }
            if (matchesTopic(memoryIndex, path, normalizedQuestion)) {
                selected.add(readFile(path));
            }
        }
        if (selected.isEmpty() && !topicPaths.isEmpty()) {
            selected.add(readFile(topicPaths.get(0)));
        }
        return selected;
    }

    private boolean matchesTopic(String memoryIndex, String path, String normalizedQuestion) {
        if (!StringUtils.hasText(normalizedQuestion)) {
            return true;
        }
        String topicBlock = topicBlock(memoryIndex, path).toLowerCase(Locale.ROOT);
        String stem = fileStem(path.toLowerCase(Locale.ROOT));
        if (normalizedQuestion.contains(stem)) {
            return true;
        }
        for (String token : topicBlock.split("[,，\\s]+")) {
            String keyword = token.replace("keywords:", "").replace("-", "").trim();
            if (keyword.length() >= 2 && normalizedQuestion.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String topicBlock(String memoryIndex, String path) {
        String[] lines = nullToEmpty(memoryIndex).split("\\R");
        StringBuilder builder = new StringBuilder();
        boolean inBlock = false;
        for (String line : lines) {
            if (line.contains("`" + path + "`")) {
                inBlock = true;
            } else if (inBlock && line.startsWith("- `")) {
                break;
            }
            if (inBlock) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private List<String> extractTopicPaths(String memoryIndex) {
        List<String> paths = new ArrayList<>();
        for (String line : nullToEmpty(memoryIndex).split("\\R")) {
            int start = line.indexOf('`');
            int end = line.indexOf('`', start + 1);
            if (start >= 0 && end > start) {
                String path = line.substring(start + 1, end);
                if (path.startsWith("topics/") && path.endsWith(".md")) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private String buildShortMemory(String sessionId, List<Map<String, String>> recentHistory) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(sessionId)) {
            builder.append(executionMemoryService.recentSnapshot(sessionId, shortMemoryPairs));
        }
        if (recentHistory != null && !recentHistory.isEmpty()) {
            builder.append("\n\n--- Recent Conversation Window ---\n");
            int from = Math.max(0, recentHistory.size() - shortMemoryPairs * 2);
            for (Map<String, String> message : recentHistory.subList(from, recentHistory.size())) {
                builder.append(message.getOrDefault("role", "unknown"))
                        .append(": ")
                        .append(message.getOrDefault("content", ""))
                        .append("\n");
            }
        }
        return builder.toString().trim();
    }

    private void writeFile(Path resolved, String content) {
        try {
            Files.createDirectories(resolved.getParent());
            String value = content == null ? "" : content;
            if (INDEX_FILE.equals(memoryRoot.relativize(resolved).toString().replace('\\', '/'))) {
                enforceMemoryIndexLimit(value);
            }
            Files.writeString(resolved, value, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入记忆文件失败", e);
        }
    }

    private void enforceMemoryIndexLimit(String content) {
        int lines = content.isEmpty() ? 0 : content.split("\\R", -1).length;
        if (lines > maxIndexLines) {
            throw new IllegalArgumentException("MEMORY.md 不能超过 " + maxIndexLines + " 行");
        }
    }

    private MemoryFileResponse toResponseWithoutContent(Path path) {
        try {
            MemoryFileResponse response = new MemoryFileResponse();
            String relativePath = memoryRoot.relativize(path.normalize()).toString().replace('\\', '/');
            response.setPath(relativePath);
            response.setType(typeOf(relativePath));
            response.setUpdatedAt(Files.exists(path)
                    ? Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : null);
            response.setSize(Files.exists(path) ? Files.size(path) : 0L);
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("读取记忆文件元数据失败", e);
        }
    }

    private Path resolveAllowedPath(String path) {
        String normalized = normalizeRelativePath(path);
        boolean allowed = INDEX_FILE.equals(normalized)
                || (normalized.startsWith("topics/") && normalized.endsWith(".md"));
        if (!allowed) {
            throw new IllegalArgumentException("非法记忆文件路径");
        }
        Path resolved = memoryRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(memoryRoot.toAbsolutePath().normalize()) && !resolved.startsWith(memoryRoot.normalize())) {
            throw new IllegalArgumentException("非法记忆文件路径");
        }
        return resolved;
    }

    private String normalizeRelativePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("path 不能为空");
        }
        return path.trim().replace('\\', '/').replaceFirst("^memory/", "");
    }

    private String requiredPath(MemoryFileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        return request.getPath();
    }

    private String typeOf(String relativePath) {
        if (INDEX_FILE.equals(relativePath)) {
            return "LONG_TERM_INDEX";
        }
        if (relativePath.startsWith("topics/")) {
            return "LONG_TERM_TOPIC";
        }
        return "UNKNOWN";
    }

    private String readStringIfExists(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            logger.warn("读取记忆文件失败: {}", path, e);
            return "";
        }
    }

    private String fileStem(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.replace(".md", "");
    }

    private void ensureRoot() {
        try {
            init();
        } catch (IOException e) {
            throw new IllegalStateException("初始化记忆目录失败", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
