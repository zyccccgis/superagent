package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.config.FileUploadConfig;
import org.example.dto.RagDocumentListResponse;
import org.example.dto.RagDocumentResponse;
import org.example.entity.RagDocument;
import org.example.entity.RagDocumentStatus;
import org.example.mapper.RagDocumentMapper;
import org.example.service.RagDocumentService;
import org.example.service.VectorIndexService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
public class FileSystemRagDocumentService implements RagDocumentService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final FileUploadConfig fileUploadConfig;
    private final VectorIndexService vectorIndexService;
    private final RagDocumentMapper ragDocumentMapper;

    public FileSystemRagDocumentService(FileUploadConfig fileUploadConfig,
                                        VectorIndexService vectorIndexService,
                                        RagDocumentMapper ragDocumentMapper) {
        this.fileUploadConfig = fileUploadConfig;
        this.vectorIndexService = vectorIndexService;
        this.ragDocumentMapper = ragDocumentMapper;
    }

    @Override
    public RagDocumentResponse uploadDocument(MultipartFile file) throws Exception {
        validateFile(file);

        String originalFilename = file.getOriginalFilename().trim();
        Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
        Files.createDirectories(uploadDir);

        Path filePath = uploadDir.resolve(originalFilename).normalize();
        if (!filePath.startsWith(uploadDir)) {
            throw new IllegalArgumentException("非法文件路径");
        }

        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
        Files.copy(file.getInputStream(), filePath);

        RagDocument document = upsertDocument(filePath, RagDocumentStatus.INDEXING, null, 0);

        try {
            int chunkCount = vectorIndexService.indexSingleFile(filePath.toString());
            document.setStatus(RagDocumentStatus.INDEXED.name());
            document.setChunkCount(chunkCount);
            document.setErrorMessage(null);
            document.setIndexedAt(LocalDateTime.now());
            ragDocumentMapper.updateById(document);
            return toResponse(document);
        } catch (Exception e) {
            document.setStatus(RagDocumentStatus.FAILED.name());
            document.setErrorMessage(e.getMessage());
            document.setUpdatedAt(LocalDateTime.now());
            ragDocumentMapper.updateById(document);
            throw e;
        }
    }

    @Override
    public RagDocumentListResponse listDocuments(Integer page, Integer pageSize, String keyword) throws Exception {
        int resolvedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 100);
        LambdaQueryWrapper<RagDocument> wrapper = new LambdaQueryWrapper<RagDocument>()
                .like(StringUtils.hasText(keyword), RagDocument::getFileName, keyword == null ? null : keyword.trim())
                .orderByDesc(RagDocument::getUpdatedAt);

        Long totalCount = ragDocumentMapper.selectCount(wrapper);
        int offset = (resolvedPage - 1) * resolvedPageSize;
        List<RagDocumentResponse> documents = ragDocumentMapper.selectList(wrapper
                        .last("limit " + resolvedPageSize + " offset " + offset))
                .stream()
                .map(this::toResponse)
                .toList();

        RagDocumentListResponse response = new RagDocumentListResponse();
        response.setItems(documents);
        response.setTotal(totalCount == null ? 0 : Math.toIntExact(totalCount));
        response.setPage(resolvedPage);
        response.setPageSize(resolvedPageSize);
        return response;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (!isAllowedFile(originalFilename)) {
            throw new IllegalArgumentException("不支持的文件格式，仅支持: " + fileUploadConfig.getAllowedExtensions());
        }
    }

    private RagDocument upsertDocument(Path filePath,
                                       RagDocumentStatus status,
                                       String errorMessage,
                                       int chunkCount) throws IOException {
        String normalizedPath = filePath.normalize().toString();
        String fileName = filePath.getFileName().toString();
        String documentId = UUID.nameUUIDFromBytes(normalizedPath.getBytes()).toString();
        LocalDateTime now = LocalDateTime.now();

        RagDocument document = ragDocumentMapper.selectOne(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getDocumentId, documentId)
                .last("limit 1"));
        if (document == null) {
            document = new RagDocument();
            document.setDocumentId(documentId);
            document.setCreatedAt(now);
            document.setDeleted(0);
        }

        document.setFileName(fileName);
        document.setFilePath(normalizedPath);
        document.setFileSize(Files.size(filePath));
        document.setExtension(extension(fileName));
        document.setStatus(status.name());
        document.setChunkCount(chunkCount);
        document.setErrorMessage(errorMessage);
        document.setUpdatedAt(now);

        if (document.getId() == null) {
            ragDocumentMapper.insert(document);
        } else {
            ragDocumentMapper.updateById(document);
        }
        return document;
    }

    private RagDocumentResponse toResponse(RagDocument document) {
        RagDocumentResponse response = new RagDocumentResponse();
        response.setDocumentId(document.getDocumentId());
        response.setFileName(document.getFileName());
        response.setFilePath(document.getFilePath());
        response.setFileSize(document.getFileSize());
        response.setExtension(document.getExtension());
        response.setStatus(document.getStatus());
        response.setUpdatedAt(toEpochMillis(document.getUpdatedAt()));
        return response;
    }

    private boolean isAllowedFile(String fileName) {
        String extension = extension(fileName);
        if (extension.isEmpty()) {
            return false;
        }
        String allowedExtensions = fileUploadConfig.getAllowedExtensions();
        if (allowedExtensions == null || allowedExtensions.isBlank()) {
            return false;
        }
        return List.of(allowedExtensions.toLowerCase(Locale.ROOT).split(",")).contains(extension);
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private Long toEpochMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
