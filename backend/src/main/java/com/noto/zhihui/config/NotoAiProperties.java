package com.noto.zhihui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "noto.ai")
public class NotoAiProperties {

    private boolean enabled = false;
    private String model = "qwen-plus";
    private String embeddingModel = "text-embedding-v3";
    private String apiKey = "";
    private boolean ragEnabled = true;
    private int ragChunkSize = 600;
    private int ragChunkOverlap = 80;
    /** 向量检索单次最多扫描的分块数，防止超大知识库 OOM（仅内存检索 fallback 时使用） */
    private int ragMaxChunksScan = 3000;
    /** 是否启用 pgvector 数据库向量检索 */
    private boolean ragPgvectorEnabled = true;
    /** 与 text-embedding-v3 默认维度一致 */
    private int ragEmbeddingDimensions = 1024;
}
