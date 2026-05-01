package org.example.service;

import org.example.dto.MemoryContext;
import org.example.dto.MemoryFileListResponse;
import org.example.dto.MemoryFileRequest;
import org.example.dto.MemoryFileResponse;

import java.util.List;
import java.util.Map;

public interface MemoryService {

    MemoryContext loadContext(String sessionId, String question, List<Map<String, String>> recentHistory);

    MemoryFileListResponse listFiles(String type, String keyword);

    MemoryFileResponse readFile(String path);

    MemoryFileResponse createFile(MemoryFileRequest request);

    MemoryFileResponse updateFile(MemoryFileRequest request);

    void deleteFile(String path);
}
