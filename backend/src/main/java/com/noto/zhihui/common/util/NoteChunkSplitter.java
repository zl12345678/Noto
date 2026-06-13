package com.noto.zhihui.common.util;

import java.util.ArrayList;
import java.util.List;

public final class NoteChunkSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 600;
    private static final int DEFAULT_OVERLAP = 80;

    private NoteChunkSplitter() {
    }

    public record ContentChunk(
            int index,
            String content,
            int offsetStart,
            int offsetEnd
    ) {}

    public static List<ContentChunk> split(String content) {
        return split(content, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public static List<ContentChunk> split(String content, int chunkSize, int overlap) {
        List<ContentChunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        int size = Math.max(200, chunkSize);
        int step = Math.max(1, size - Math.max(0, overlap));
        int chunkIndex = 0;
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(content.length(), start + size);
            String slice = content.substring(start, end).trim();
            if (slice.isEmpty()) {
                if (end >= content.length()) {
                    break;
                }
                continue;
            }
            int trimmedStart = start;
            while (trimmedStart < end && Character.isWhitespace(content.charAt(trimmedStart))) {
                trimmedStart++;
            }
            chunks.add(new ContentChunk(chunkIndex++, slice, trimmedStart, end));
            if (end >= content.length()) {
                break;
            }
        }
        return chunks;
    }
}
