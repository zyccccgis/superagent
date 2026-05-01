package org.example.service;

import org.example.dto.RagDocumentListResponse;
import org.example.dto.RagDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface RagDocumentService {

    RagDocumentResponse uploadDocument(MultipartFile file) throws Exception;

    RagDocumentListResponse listDocuments(Integer page, Integer pageSize, String keyword) throws Exception;
}
