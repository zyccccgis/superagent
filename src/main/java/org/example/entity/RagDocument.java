package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("rag_documents")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String documentId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String extension;

    private String status;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime indexedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
