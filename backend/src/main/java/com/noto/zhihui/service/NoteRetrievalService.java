package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.util.NoteChunkSplitter;
import com.noto.zhihui.common.util.NoteContentChunkSelector;
import com.noto.zhihui.common.util.NoteTextUtils;
import com.noto.zhihui.common.util.TextMatchRange;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.vo.ai.AiReferenceVO;
import com.noto.zhihui.vo.note.NoteVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NoteRetrievalService {

    private static final int MAX_CHUNKS = 10;
    private static final int MAX_NOTE_CHUNKS = 6;
    private static final int MAX_CONTEXT_CHARS = 14_000;
    private static final int PROMPT_CHUNK_CHARS = 520;
    private static final int REF_SNIPPET_LENGTH = 220;
    private static final int MAX_CATALOG_NOTES = 60;
    private static final int MAX_CATALOG_NOTES_PER_FOLDER = 40;
    private static final Pattern CATALOG_QUESTION = Pattern.compile(
            "(?:有哪些|有什么|都有什么|列出|列表|清单|多少篇|几篇|几个|哪些).{0,24}(?:文档|笔记|文章|资料)"
                    + "|(?:文档|笔记|知识库|资料库|分组|文件夹).{0,24}(?:有哪些|有什么|列表|清单|多少)"
                    + "|都(?:有)?什么(?:文档|笔记)"
    );
    private static final Pattern CATALOG_NOISE = Pattern.compile(
            "(有哪些|有什么|都有什么|文档|笔记|文章|资料|列出|列表|清单|请问|帮我|告诉|一下|知识库|资料库|分组|文件夹|目录|里面|里边|其中|相关|关于|吗|呢|啊)"
    );
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,，。！？；：、.!?;:()\\[\\]\"'\\-—]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "什么", "如何", "怎么", "为什么", "哪些", "哪个", "这个", "那个",
            "吗", "呢", "啊", "和", "与", "或", "及", "请", "帮", "我", "你", "我们", "它们",
            "what", "how", "why", "when", "where", "which", "the", "a", "an", "is", "are", "to", "of", "in"
    );

    private final NoteService noteService;
    private final NoteFolderService noteFolderService;
    private final NoteRagSearchService noteRagSearchService;
    private final NotoAiProperties aiProperties;

    public NoteRetrievalService(
            NoteService noteService,
            NoteFolderService noteFolderService,
            NoteRagSearchService noteRagSearchService,
            NotoAiProperties aiProperties
    ) {
        this.noteService = noteService;
        this.noteFolderService = noteFolderService;
        this.noteRagSearchService = noteRagSearchService;
        this.aiProperties = aiProperties;
    }

    public record RetrievedNoteChunk(
            Long noteId,
            String noteTitle,
            String snippet,
            String matchedKeyword,
            int score,
            Integer offsetStart,
            Integer offsetEnd,
            String highlightKeyword
    ) {}

    public List<RetrievedNoteChunk> retrieveForQuestion(Long userId, Long workspaceId, String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        List<RetrievedNoteChunk> merged = new ArrayList<>();
        if (noteRagSearchService.isAvailable()) {
            merged.addAll(noteRagSearchService.search(
                    userId, workspaceId, null, question.trim(), MAX_CHUNKS
            ));
        }
        if (merged.isEmpty()) {
            merged.addAll(retrieveByKeyword(userId, workspaceId, question));
        } else {
            mergeKeywordResults(merged, userId, workspaceId, question);
        }
        return finalizeChunks(merged, question);
    }

    public List<RetrievedNoteChunk> retrieveForNote(Long userId, NoteEntity note, String question) {
        List<RetrievedNoteChunk> chunks = new ArrayList<>();
        if (noteRagSearchService.isAvailable() && StringUtils.hasText(question)) {
            chunks.addAll(noteRagSearchService.search(
                    userId, note.getWorkspaceId(), note.getId(), question.trim(), MAX_NOTE_CHUNKS
            ));
        }
        if (chunks.isEmpty()) {
            chunks.addAll(selectChunksFromNote(note, question, MAX_NOTE_CHUNKS));
        }
        if (chunks.isEmpty()) {
            AiReferenceVO ref = referenceForNote(note, question);
            chunks.add(new RetrievedNoteChunk(
                    note.getId(),
                    note.getTitle(),
                    ref.getSnippet(),
                    primaryKeyword(question),
                    100,
                    ref.getOffsetStart(),
                    ref.getOffsetEnd(),
                    ref.getHighlightKeyword()
            ));
        }
        return finalizeChunks(chunks, question);
    }

    public List<String> inferRetrievalGaps(List<RetrievedNoteChunk> chunks, String question) {
        if (isCatalogQuestion(question)) {
            return List.of();
        }
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        String topic = summarizeQuestionTopic(question);
        if (chunks.isEmpty()) {
            return List.of("建议新建：关于「" + topic + "」的背景笔记");
        }
        int maxScore = chunks.stream().mapToInt(RetrievedNoteChunk::score).max().orElse(0);
        if (maxScore < 35) {
            return List.of("现有资料匹配度较低，建议补充「" + topic + "」相关记录");
        }
        return List.of();
    }

    public boolean isCatalogQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        return CATALOG_QUESTION.matcher(question.trim()).find();
    }

    public String buildCatalogContext(Long userId, Long workspaceId, String question) {
        List<NoteFolderEntity> folders = noteFolderService.lambdaQuery()
                .eq(NoteFolderEntity::getWorkspaceId, workspaceId)
                .orderByDesc(NoteFolderEntity::getSortOrder)
                .orderByAsc(NoteFolderEntity::getCreatedAt)
                .list();

        String folderHint = resolveFolderHint(question, folders);
        List<NoteFolderEntity> targetFolders = filterFolders(folders, folderHint, question);

        if (targetFolders.isEmpty()) {
            return buildTitleKeywordCatalog(userId, workspaceId, folderHint, question);
        }

        StringBuilder builder = new StringBuilder();
        int totalListed = 0;
        long totalNotes = 0;

        for (NoteFolderEntity folder : targetFolders) {
            Page<NoteVO> page = noteService.pageNotes(
                    userId,
                    1,
                    MAX_CATALOG_NOTES_PER_FOLDER,
                    null,
                    0,
                    null,
                    workspaceId,
                    folder.getId(),
                    null,
                    true
            );
            if (page.getRecords().isEmpty()) {
                continue;
            }
            totalNotes += page.getTotal();
            builder.append("【").append(folder.getName()).append("】");
            if (page.getTotal() > page.getRecords().size()) {
                builder.append("共 ").append(page.getTotal()).append(" 篇，列出前 ")
                        .append(page.getRecords().size()).append(" 篇：\n");
            } else {
                builder.append(page.getTotal()).append(" 篇：\n");
            }
            for (NoteVO note : page.getRecords()) {
                if (totalListed >= MAX_CATALOG_NOTES) {
                    builder.append("- …（还有更多文档未列出）\n");
                    break;
                }
                builder.append("- ")
                        .append(note.getTitle() == null ? "未命名" : note.getTitle())
                        .append(" (#")
                        .append(note.getId())
                        .append(")\n");
                totalListed++;
            }
            builder.append('\n');
            if (totalListed >= MAX_CATALOG_NOTES) {
                break;
            }
        }

        if (builder.isEmpty()) {
            return buildTitleKeywordCatalog(userId, workspaceId, folderHint, question);
        }

        return ("知识库共约 " + totalNotes + " 篇符合条件的文档。\n" + builder).trim();
    }

    private String buildTitleKeywordCatalog(
            Long userId,
            Long workspaceId,
            String keyword,
            String question
    ) {
        String search = StringUtils.hasText(keyword) ? keyword : extractKeywords(question).stream()
                .filter(token -> token.length() >= 2)
                .findFirst()
                .orElse("");
        if (!StringUtils.hasText(search)) {
            return "（当前知识库中没有找到与问题匹配的文档目录）";
        }
        Page<NoteVO> page = noteService.pageNotes(
                userId, 1, MAX_CATALOG_NOTES, search, 0, null, workspaceId, null, null, true
        );
        if (page.getRecords().isEmpty()) {
            return "（未找到标题或正文包含「" + search + "」的文档）";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("按关键词「").append(search).append("」匹配到 ")
                .append(page.getTotal()).append(" 篇：\n");
        for (NoteVO note : page.getRecords()) {
            builder.append("- ")
                    .append(note.getTitle() == null ? "未命名" : note.getTitle())
                    .append(" (#")
                    .append(note.getId())
                    .append(")\n");
        }
        return builder.toString().trim();
    }

    private String resolveFolderHint(String question, List<NoteFolderEntity> folders) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String trimmed = question.trim();
        for (NoteFolderEntity folder : folders) {
            if (StringUtils.hasText(folder.getName()) && trimmed.contains(folder.getName())) {
                return folder.getName();
            }
        }

        String normalized = CATALOG_NOISE.matcher(trimmed).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        NoteFolderEntity best = null;
        int bestScore = 0;
        for (NoteFolderEntity folder : folders) {
            String name = folder.getName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            if (name.contains(normalized) || normalized.contains(name)) {
                int score = Math.min(name.length(), normalized.length()) + 10;
                if (score > bestScore) {
                    bestScore = score;
                    best = folder;
                }
            }
            for (String token : extractKeywords(normalized)) {
                if (token.length() >= 2 && name.contains(token)) {
                    int score = token.length() * 3;
                    if (score > bestScore) {
                        bestScore = score;
                        best = folder;
                    }
                }
            }
        }
        if (best != null) {
            return best.getName();
        }
        return normalized.length() >= 2 ? normalized : null;
    }

    private List<NoteFolderEntity> filterFolders(
            List<NoteFolderEntity> folders,
            String folderHint,
            String question
    ) {
        if (!StringUtils.hasText(folderHint)) {
            return folders;
        }
        if (question.contains(folderHint)) {
            List<NoteFolderEntity> exact = folders.stream()
                    .filter(folder -> folderHint.equals(folder.getName()))
                    .toList();
            if (!exact.isEmpty()) {
                return exact;
            }
        }
        List<NoteFolderEntity> matched = folders.stream()
                .filter(folder -> {
                    String name = folder.getName();
                    if (!StringUtils.hasText(name)) {
                        return false;
                    }
                    return name.equals(folderHint)
                            || name.contains(folderHint)
                            || folderHint.contains(name);
                })
                .toList();
        return matched.isEmpty() ? folders : matched;
    }

    public AiReferenceVO toReference(RetrievedNoteChunk chunk) {
        String displaySnippet = NoteTextUtils.excerpt(chunk.snippet(), REF_SNIPPET_LENGTH);
        return new AiReferenceVO(
                chunk.noteId(),
                chunk.noteTitle(),
                displaySnippet,
                chunk.offsetStart(),
                chunk.offsetEnd(),
                chunk.highlightKeyword(),
                chunk.score()
        );
    }

    public AiReferenceVO referenceForNote(NoteEntity note, String question) {
        String keyword = primaryKeyword(question);
        TextMatchRange range = StringUtils.hasText(keyword)
                ? NoteTextUtils.findMatchRange(note.getContent(), keyword, REF_SNIPPET_LENGTH)
                : null;
        String snippet = range != null
                ? range.snippet()
                : NoteTextUtils.excerpt(note.getContent(), REF_SNIPPET_LENGTH);
        if (!StringUtils.hasText(snippet)) {
            snippet = firstNonBlank(note.getSummary(), "（空文档）");
        }
        return new AiReferenceVO(
                note.getId(),
                note.getTitle(),
                snippet,
                range == null ? null : range.start(),
                range == null ? null : range.end(),
                range == null ? null : range.keyword(),
                100
        );
    }

    public String buildChunkContext(List<RetrievedNoteChunk> chunks) {
        if (chunks.isEmpty()) {
            return "（当前知识库暂无匹配资料）";
        }
        StringBuilder builder = new StringBuilder();
        for (RetrievedNoteChunk chunk : chunks) {
            String block = formatChunkBlock(chunk);
            if (builder.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            builder.append(block).append('\n');
        }
        return builder.toString().trim();
    }

    public String buildSingleNoteContext(NoteEntity note, String question, List<RetrievedNoteChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            AiReferenceVO ref = referenceForNote(note, question);
            return """
                    [文档 #%d] %s
                    %s
                    """.formatted(note.getId(), note.getTitle(), ref.getSnippet());
        }
        StringBuilder builder = new StringBuilder();
        for (RetrievedNoteChunk chunk : chunks) {
            String block = formatChunkBlock(chunk);
            if (builder.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            builder.append(block).append('\n');
        }
        return builder.toString().trim();
    }

    private List<RetrievedNoteChunk> retrieveByKeyword(Long userId, Long workspaceId, String question) {
        List<String> keywords = extractKeywords(question);
        Map<Long, RetrievedNoteChunk> ranked = new LinkedHashMap<>();

        mergeSearchResults(ranked, userId, workspaceId, question.trim(), 12);
        for (String keyword : keywords) {
            mergeSearchResults(ranked, userId, workspaceId, keyword, 8);
        }

        if (ranked.isEmpty()) {
            Page<NoteVO> recent = noteService.pageNotes(
                    userId, 1, MAX_CHUNKS, null, 0, null, workspaceId, null, null, true
            );
            for (NoteVO note : recent.getRecords()) {
                ranked.putIfAbsent(
                        note.getId(),
                        new RetrievedNoteChunk(
                                note.getId(),
                                note.getTitle(),
                                firstNonBlank(note.getExcerpt(), note.getSummary(), "暂无摘要"),
                                "",
                                1,
                                null,
                                null,
                                null
                        )
                );
            }
        }

        return new ArrayList<>(ranked.values());
    }

    private void mergeKeywordResults(
            List<RetrievedNoteChunk> vectorHits,
            Long userId,
            Long workspaceId,
            String question
    ) {
        Map<Long, RetrievedNoteChunk> keywordRanked = new LinkedHashMap<>();
        mergeSearchResults(keywordRanked, userId, workspaceId, question.trim(), 6);
        for (RetrievedNoteChunk keywordHit : keywordRanked.values()) {
            boolean exists = vectorHits.stream().anyMatch(item -> sameChunk(item, keywordHit));
            if (!exists) {
                vectorHits.add(boostWithKeywords(keywordHit, question));
            } else {
                for (int i = 0; i < vectorHits.size(); i++) {
                    RetrievedNoteChunk existing = vectorHits.get(i);
                    if (existing.noteId().equals(keywordHit.noteId())) {
                        int mergedScore = Math.max(existing.score(), keywordHit.score()) + 6;
                        vectorHits.set(i, withScore(existing, mergedScore));
                        break;
                    }
                }
            }
        }
    }

    private List<RetrievedNoteChunk> finalizeChunks(List<RetrievedNoteChunk> chunks, String question) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        List<RetrievedNoteChunk> boosted = chunks.stream()
                .map(chunk -> boostWithKeywords(chunk, question))
                .sorted(Comparator
                        .comparingInt(RetrievedNoteChunk::score).reversed()
                        .thenComparing(chunk -> chunk.noteTitle() == null ? "" : chunk.noteTitle()))
                .toList();

        List<RetrievedNoteChunk> deduped = new ArrayList<>();
        Map<String, RetrievedNoteChunk> seen = new LinkedHashMap<>();
        for (RetrievedNoteChunk chunk : boosted) {
            String key = chunk.noteId() + ":" + (chunk.offsetStart() == null ? "0" : chunk.offsetStart());
            RetrievedNoteChunk existing = seen.get(key);
            if (existing == null || chunk.score() > existing.score()) {
                seen.put(key, chunk);
            }
        }
        deduped.addAll(seen.values());
        deduped.sort(Comparator
                .comparingInt(RetrievedNoteChunk::score).reversed()
                .thenComparing(chunk -> chunk.noteTitle() == null ? "" : chunk.noteTitle()));
        return deduped.stream().limit(MAX_CHUNKS).toList();
    }

    private List<RetrievedNoteChunk> selectChunksFromNote(NoteEntity note, String question, int maxChunks) {
        String content = note.getContent();
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<NoteChunkSplitter.ContentChunk> selected = NoteContentChunkSelector.selectForQuery(
                content,
                question,
                maxChunks,
                aiProperties.getRagChunkSize(),
                aiProperties.getRagChunkOverlap()
        );
        List<RetrievedNoteChunk> chunks = new ArrayList<>();
        for (NoteChunkSplitter.ContentChunk item : selected) {
            chunks.add(new RetrievedNoteChunk(
                    note.getId(),
                    note.getTitle(),
                    trimForPrompt(item.content()),
                    primaryKeyword(question),
                    scoreChunkText(item.content(), question),
                    item.offsetStart(),
                    item.offsetEnd(),
                    primaryKeyword(question)
            ));
        }
        return chunks;
    }

    private void mergeSearchResults(
            Map<Long, RetrievedNoteChunk> ranked,
            Long userId,
            Long workspaceId,
            String keyword,
            int pageSize
    ) {
        Page<NoteVO> page = noteService.pageNotes(
                userId, 1, pageSize, keyword, 0, null, workspaceId, null, null, true
        );
        for (NoteVO note : page.getRecords()) {
            int score = scoreNote(note, keyword);
            RetrievedNoteChunk chunk = buildChunk(note, keyword, userId, score);
            ranked.merge(
                    note.getId(),
                    chunk,
                    (left, right) -> right.score() >= left.score() ? right : left
            );
        }
    }

    private int scoreNote(NoteVO note, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return 1;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int score = 0;
        if (note.getTitle() != null && note.getTitle().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            score += 14;
        }
        if (note.getSummary() != null && note.getSummary().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            score += 8;
        }
        if (note.getExcerpt() != null && note.getExcerpt().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            score += 5;
        }
        return Math.max(score, 2);
    }

    private RetrievedNoteChunk buildChunk(NoteVO note, String keyword, Long userId, int score) {
        try {
            NoteVO detail = noteService.getNoteDetail(note.getId(), userId);
            TextMatchRange range = NoteTextUtils.findMatchRange(detail.getContent(), keyword, PROMPT_CHUNK_CHARS);
            if (range != null && StringUtils.hasText(range.snippet())) {
                return new RetrievedNoteChunk(
                        note.getId(),
                        note.getTitle(),
                        trimForPrompt(range.snippet()),
                        keyword,
                        score + scoreChunkText(range.snippet(), keyword),
                        range.start(),
                        range.end(),
                        range.keyword()
                );
            }
            List<RetrievedNoteChunk> splitChunks = selectChunksFromNote(
                    toEntity(detail),
                    keyword,
                    2
            );
            if (!splitChunks.isEmpty()) {
                RetrievedNoteChunk best = splitChunks.get(0);
                return withScore(best, Math.max(score, best.score()));
            }
            String fallback = firstNonBlank(
                    detail.getSummary(),
                    NoteTextUtils.excerpt(detail.getContent(), PROMPT_CHUNK_CHARS),
                    "暂无摘要"
            );
            return new RetrievedNoteChunk(note.getId(), note.getTitle(), fallback, keyword, score, null, null, null);
        } catch (Exception ex) {
            return new RetrievedNoteChunk(
                    note.getId(),
                    note.getTitle(),
                    firstNonBlank(note.getExcerpt(), note.getSummary(), "暂无摘要"),
                    keyword,
                    score,
                    null,
                    null,
                    null
            );
        }
    }

    private NoteEntity toEntity(NoteVO detail) {
        NoteEntity entity = new NoteEntity();
        entity.setId(detail.getId());
        entity.setTitle(detail.getTitle());
        entity.setContent(detail.getContent());
        entity.setSummary(detail.getSummary());
        entity.setWorkspaceId(detail.getWorkspaceId());
        return entity;
    }

    private RetrievedNoteChunk boostWithKeywords(RetrievedNoteChunk chunk, String question) {
        int boost = scoreChunkText(chunk.snippet(), question);
        if (StringUtils.hasText(chunk.noteTitle()) && StringUtils.hasText(question)) {
            String lowerTitle = chunk.noteTitle().toLowerCase(Locale.ROOT);
            for (String token : extractKeywords(question)) {
                if (lowerTitle.contains(token.toLowerCase(Locale.ROOT))) {
                    boost += 10;
                    break;
                }
            }
        }
        return withScore(chunk, chunk.score() + boost);
    }

    private RetrievedNoteChunk withScore(RetrievedNoteChunk chunk, int score) {
        return new RetrievedNoteChunk(
                chunk.noteId(),
                chunk.noteTitle(),
                chunk.snippet(),
                chunk.matchedKeyword(),
                score,
                chunk.offsetStart(),
                chunk.offsetEnd(),
                chunk.highlightKeyword()
        );
    }

    private boolean sameChunk(RetrievedNoteChunk left, RetrievedNoteChunk right) {
        if (!Objects.equals(left.noteId(), right.noteId())) {
            return false;
        }
        if (left.offsetStart() == null || right.offsetStart() == null) {
            return true;
        }
        return left.offsetStart().equals(right.offsetStart());
    }

    private String formatChunkBlock(RetrievedNoteChunk chunk) {
        StringBuilder block = new StringBuilder();
        block.append("[文档 #").append(chunk.noteId()).append("] ")
                .append(chunk.noteTitle()).append("（相关度 ").append(chunk.score()).append("）\n");
        if (StringUtils.hasText(chunk.matchedKeyword())) {
            block.append("（匹配：").append(chunk.matchedKeyword()).append("）\n");
        }
        block.append(trimForPrompt(chunk.snippet()));
        return block.toString();
    }

    private String trimForPrompt(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= PROMPT_CHUNK_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, PROMPT_CHUNK_CHARS) + "...";
    }

    private int scoreChunkText(String text, String question) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(question)) {
            return 0;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : extractKeywords(question)) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                score += token.length() >= 4 ? 6 : 3;
            }
        }
        return score;
    }

    private String summarizeQuestionTopic(String question) {
        List<String> keywords = extractKeywords(question);
        if (keywords.isEmpty()) {
            String trimmed = question.trim();
            return trimmed.length() > 24 ? trimmed.substring(0, 24) + "…" : trimmed;
        }
        return keywords.stream().limit(3).collect(Collectors.joining("、"));
    }

    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(question.trim())) {
            String word = token.trim();
            if (word.length() < 2) {
                continue;
            }
            if (STOP_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!keywords.contains(word)) {
                keywords.add(word);
            }
            if (keywords.size() >= 5) {
                break;
            }
        }
        return keywords;
    }

    private String primaryKeyword(String question) {
        List<String> keywords = extractKeywords(question);
        return keywords.isEmpty() ? "" : keywords.get(0);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
