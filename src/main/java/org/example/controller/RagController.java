package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.RagDocumentListResponse;
import org.example.dto.RagDocumentResponse;
import org.example.dto.RagRetrieveRequest;
import org.example.dto.RagRetrieveResponse;
import org.example.service.RagDocumentService;
import org.example.service.RagRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger logger = LoggerFactory.getLogger(RagController.class);

    private final RagDocumentService ragDocumentService;
    private final RagRetrievalService ragRetrievalService;

    public RagController(RagDocumentService ragDocumentService,
                         RagRetrievalService ragRetrievalService) {
        this.ragDocumentService = ragDocumentService;
        this.ragRetrievalService = ragRetrievalService;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RagDocumentResponse>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            RagDocumentResponse response = ragDocumentService.uploadDocument(file);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("RAG 文档上传或索引失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("RAG 文档上传或索引失败: " + e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<RagDocumentListResponse>> listDocuments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        try {
            RagDocumentListResponse response = ragDocumentService.listDocuments(page, pageSize, keyword);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            logger.error("查询 RAG 文档列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/retrieve")
    public ResponseEntity<ApiResponse<RagRetrieveResponse>> retrieve(@RequestBody RagRetrieveRequest request) {
        try {
            RagRetrieveResponse response = ragRetrievalService.retrieve(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("RAG 召回失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
