package org.example.service;

import org.example.dto.RagRetrieveRequest;
import org.example.dto.RagRetrieveResponse;

public interface RagRetrievalService {

    RagRetrieveResponse retrieve(RagRetrieveRequest request);
}
