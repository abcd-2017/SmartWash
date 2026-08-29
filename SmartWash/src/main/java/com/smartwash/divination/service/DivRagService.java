package com.smartwash.divination.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.smartwash.divination.entity.DivRagChunk;
import com.smartwash.divination.entity.DivRagDocument;
import com.smartwash.divination.mapper.DivRagChunkMapper;
import com.smartwash.divination.mapper.DivRagDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 语料服务：语料切片 / Embedding / 内存余弦检索。
 *
 * 实现取舍：embedding 存 JSON 列，启动时加载进进程内存（2 万 chunk ≈ 40MB），
 * 余弦 top-k 在 Java 内完成（2 万×1024 维 ≈ 毫秒级）。
 * 语料超过 div.rag.max-chunk 上限再升级 RediSearch/ES。
 */
@Slf4j
@Service
public class DivRagService {

    private final DivRagDocumentMapper documentMapper;
    private final DivRagChunkMapper chunkMapper;

    @Value("${div.rag.top-k:6}")
    private int topK;

    /** 内存向量索引：chunkId → embedding */
    private final Map<Long, float[]> vectorIndex = new LinkedHashMap<>();
    /** chunk 元数据 */
    private final Map<Long, DivRagChunk> chunkMeta = new LinkedHashMap<>();
    /** 是否已预热 */
    private volatile boolean warmed = false;

    public DivRagService(DivRagDocumentMapper documentMapper, DivRagChunkMapper chunkMapper) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    /**
     * 上传古籍文档并切片（简化：按段落切片，每段一个 chunk）。
     */
    public Long uploadDocument(String title, String book, String method, String content) {
        DivRagDocument doc = new DivRagDocument();
        doc.setTitle(title);
        doc.setBook(book);
        doc.setMethod(method);
        doc.setStatus(0); // 导入中
        documentMapper.insert(doc);

        // 切片（按段落，每段 ~2000 字）
        String[] paragraphs = content.split("\\n\\n|\n");
        int seq = 0;
        for (String para : paragraphs) {
            if (!StringUtils.hasText(para.trim())) continue;
            DivRagChunk chunk = new DivRagChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setSeq(seq++);
            chunk.setContent(para.trim());
            chunk.setEmbedding(generatePlaceholderEmbedding(para.trim()));
            chunk.setTokenCount(para.length() / 2); // 粗略估算
            chunkMapper.insert(chunk);
        }

        // 标记可用
        doc.setStatus(1);
        documentMapper.updateById(doc);

        // 刷新内存索引
        warmUp();

        log.info("上传古籍文档, id: {}, title: {}, chunks: {}", doc.getId(), title, seq);
        return doc.getId();
    }

    /**
     * 内存余弦检索 top-k。
     */
    public List<DivRagChunk> search(String query, String method, int k) {
        if (!warmed) warmUp();
        float[] queryVec = parseEmbedding(generatePlaceholderEmbedding(query));

        // 计算余弦相似度
        return vectorIndex.entrySet().stream()
                .filter(e -> {
                    DivRagChunk meta = chunkMeta.get(e.getKey());
                    return meta != null && (method == null || method.equals(getMethodByDocId(meta.getDocumentId())));
                })
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), cosineSimilarity(queryVec, e.getValue())))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(e -> chunkMeta.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 预热内存向量索引 */
    public synchronized void warmUp() {
        vectorIndex.clear();
        chunkMeta.clear();
        List<DivRagChunk> chunks = chunkMapper.selectList(null);
        for (DivRagChunk chunk : chunks) {
            try {
                List<Float> emb = JSON.parseObject(chunk.getEmbedding(), new TypeReference<List<Float>>() {});
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) vec[i] = emb.get(i);
                vectorIndex.put(chunk.getId(), vec);
                chunkMeta.put(chunk.getId(), chunk);
            } catch (Exception e) {
                log.warn("加载 chunk embedding 失败, id: {}", chunk.getId(), e);
            }
        }
        warmed = true;
        log.info("RAG 向量索引预热完成, chunks: {}", vectorIndex.size());
    }

    /** 解析 embedding JSON 字符串为 float[] */
    private float[] parseEmbedding(String embeddingJson) {
        List<Float> emb = JSON.parseObject(embeddingJson, new TypeReference<List<Float>>() {});
        float[] vec = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) vec[i] = emb.get(i);
        return vec;
    }

    /** 余弦相似度 */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /** 生成占位 embedding（真实实现需调用 Embedding API） */
    private String generatePlaceholderEmbedding(String text) {
        // 简化：基于文本哈希生成确定性伪向量（128 维）
        int dim = 128;
        float[] vec = new float[dim];
        Random rng = new Random(text.hashCode());
        for (int i = 0; i < dim; i++) {
            vec[i] = rng.nextFloat() * 2 - 1;
        }
        // 归一化
        double norm = 0;
        for (float v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) vec[i] /= norm;
        }
        List<Float> list = new ArrayList<>();
        for (float v : vec) list.add(v);
        return JSON.toJSONString(list);
    }

    private String getMethodByDocId(Long docId) {
        DivRagDocument doc = documentMapper.selectById(docId);
        return doc != null ? doc.getMethod() : null;
    }
}
