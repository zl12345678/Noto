package com.noto.zhihui.common.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 长文按块选取，避免简单 head 截断。
 */
public final class NoteContentChunkSelector {

    private NoteContentChunkSelector() {
    }

    public record ScoredChunk(NoteChunkSplitter.ContentChunk chunk, int score) {}

    public static List<NoteChunkSplitter.ContentChunk> selectForQuery(
            String content,
            String question,
            int maxChunks,
            int chunkSize,
            int overlap
    ) {
        List<NoteChunkSplitter.ContentChunk> all = NoteChunkSplitter.split(content, chunkSize, overlap);
        if (all.isEmpty()) {
            return all;
        }
        int limit = Math.max(1, maxChunks);
        if (!StringUtils.hasText(question)) {
            return spreadSample(all, limit);
        }
        String normalizedQuestion = question.trim().toLowerCase(Locale.ROOT);
        List<ScoredChunk> scored = new ArrayList<>();
        for (NoteChunkSplitter.ContentChunk chunk : all) {
            scored.add(new ScoredChunk(chunk, scoreChunk(chunk.content(), normalizedQuestion)));
        }
        scored.sort(Comparator.comparingInt(ScoredChunk::score).reversed());
        List<NoteChunkSplitter.ContentChunk> picked = new ArrayList<>();
        for (ScoredChunk item : scored) {
            if (picked.size() >= limit) {
                break;
            }
            if (item.score() > 0 || picked.isEmpty()) {
                picked.add(item.chunk());
            }
        }
        if (picked.isEmpty()) {
            return spreadSample(all, limit);
        }
        picked.sort(Comparator.comparingInt(chunk -> chunk.offsetStart()));
        return picked;
    }

    public static String joinForPrompt(List<NoteChunkSplitter.ContentChunk> chunks, int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (NoteChunkSplitter.ContentChunk chunk : chunks) {
            String block = """
                    [段落 #%d | 偏移 %d-%d]
                    %s
                    """.formatted(
                    chunk.index() + 1,
                    chunk.offsetStart(),
                    chunk.offsetEnd(),
                    chunk.content().trim()
            );
            if (builder.length() + block.length() > maxChars) {
                if (builder.isEmpty()) {
                    builder.append(block, 0, Math.min(block.length(), maxChars));
                    builder.append("\n...(段落已截断)");
                }
                break;
            }
            builder.append(block).append('\n');
        }
        return builder.toString().trim();
    }

    private static List<NoteChunkSplitter.ContentChunk> spreadSample(
            List<NoteChunkSplitter.ContentChunk> all,
            int limit
    ) {
        if (all.size() <= limit) {
            return all;
        }
        List<NoteChunkSplitter.ContentChunk> picked = new ArrayList<>();
        double step = (double) (all.size() - 1) / Math.max(1, limit - 1);
        for (int i = 0; i < limit; i++) {
            int index = (int) Math.round(i * step);
            index = Math.min(index, all.size() - 1);
            NoteChunkSplitter.ContentChunk chunk = all.get(index);
            if (picked.stream().noneMatch(item -> item.index() == chunk.index())) {
                picked.add(chunk);
            }
        }
        if (picked.isEmpty()) {
            picked.add(all.get(0));
        }
        picked.sort(Comparator.comparingInt(NoteChunkSplitter.ContentChunk::offsetStart));
        return picked;
    }

    private static int scoreChunk(String chunkContent, String normalizedQuestion) {
        if (!StringUtils.hasText(chunkContent) || !StringUtils.hasText(normalizedQuestion)) {
            return 0;
        }
        String lower = chunkContent.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : normalizedQuestion.split("[\\s,，。！？；：、.!?;:()\\[\\]\"'\\-—]+")) {
            if (token.length() < 2) {
                continue;
            }
            if (lower.contains(token)) {
                score += token.length() >= 4 ? 8 : 4;
            }
        }
        return score;
    }
}
