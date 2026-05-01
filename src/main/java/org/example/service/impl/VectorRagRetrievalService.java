package org.example.service.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.dto.RagRetrieveRequest;
import org.example.dto.RagRetrieveResponse;
import org.example.dto.RagRetrieveResult;
import org.example.service.RagRetrievalService;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VectorRagRetrievalService implements RagRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(VectorRagRetrievalService.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;
    private static final String SCORE_TYPE = "L2_DISTANCE";

    private final VectorSearchService vectorSearchService;

    public VectorRagRetrievalService(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public RagRetrieveResponse retrieve(RagRetrieveRequest request) {
        if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        String text = request.getText().trim();
        int topK = request.getTopK() == null ? DEFAULT_TOP_K : Math.max(1, Math.min(request.getTopK(), MAX_TOP_K));
        List<VectorSearchService.SearchResult> searchResults =
                vectorSearchService.searchSimilarDocuments(text, topK);

        List<RagRetrieveResult> results = new ArrayList<>();
        for (int i = 0; i < searchResults.size(); i++) {
            RagRetrieveResult result = toRagRetrieveResult(searchResults.get(i), i + 1);
            if (request.getMinScore() == null || result.getDistance() <= request.getMinScore()) {
                results.add(result);
            }
        }

        RagRetrieveResponse response = new RagRetrieveResponse();
        response.setText(text);
        response.setTopK(topK);
        response.setScoreType(SCORE_TYPE);
        response.setHigherScoreBetter(false);
        response.setResults(results);
        return response;
    }

    private RagRetrieveResult toRagRetrieveResult(VectorSearchService.SearchResult searchResult, int rank) {
        RagRetrieveResult result = new RagRetrieveResult();
        result.setRank(rank);
        result.setChunkId(searchResult.getId());
        result.setContent(searchResult.getContent());
        result.setDistance(searchResult.getScore());
        result.setScoreType(SCORE_TYPE);
        result.setMetadata(searchResult.getMetadata());

        fillMetadataFields(result, searchResult.getMetadata());
        return result;
    }

    private void fillMetadataFields(RagRetrieveResult result, String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return;
        }
        try {
            JsonObject metadataJson = JsonParser.parseString(metadata).getAsJsonObject();
            if (metadataJson.has("_source")) {
                result.setSource(metadataJson.get("_source").getAsString());
            }
            if (metadataJson.has("_file_name")) {
                result.setFileName(metadataJson.get("_file_name").getAsString());
            }
        } catch (Exception ignored) {
            logger.debug("解析 RAG metadata 失败: {}", metadata);
        }
    }
}
