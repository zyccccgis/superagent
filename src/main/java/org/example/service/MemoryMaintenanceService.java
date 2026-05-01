package org.example.service;

import org.example.dto.MemoryCompressRequest;
import org.example.dto.MemoryCompressResponse;
import org.example.dto.MemoryExtractRequest;
import org.example.dto.MemoryExtractResponse;

public interface MemoryMaintenanceService {

    MemoryExtractResponse extractLongTermMemory(MemoryExtractRequest request);

    MemoryCompressResponse compressShortTermMemory(MemoryCompressRequest request);
}
