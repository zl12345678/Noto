package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.dto.drive.DriveFolderCreateRequest;
import com.noto.zhihui.dto.share.ShareCreateRequest;
import com.noto.zhihui.entity.NoteAttachmentEntity;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.service.AttachmentService;
import com.noto.zhihui.service.DriveFolderService;
import com.noto.zhihui.service.NoteFolderService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.NoteTagService;
import com.noto.zhihui.service.ShareLinkService;
import com.noto.zhihui.service.TagService;
import com.noto.zhihui.vo.attachment.AttachmentVO;
import com.noto.zhihui.vo.drive.DriveFolderVO;
import com.noto.zhihui.vo.folder.FolderVO;
import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.share.ShareLinkVO;
import com.noto.zhihui.vo.tag.TagVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AgentWorkspaceTools {

    private final AttachmentService attachmentService;
    private final DriveFolderService driveFolderService;
    private final ShareLinkService shareLinkService;
    private final TagService tagService;
    private final NoteTagService noteTagService;
    private final NoteFolderService noteFolderService;
    private final NoteService noteService;
    private final ObjectMapper objectMapper;

    public AgentWorkspaceTools(
            AttachmentService attachmentService,
            DriveFolderService driveFolderService,
            ShareLinkService shareLinkService,
            TagService tagService,
            NoteTagService noteTagService,
            NoteFolderService noteFolderService,
            NoteService noteService,
            ObjectMapper objectMapper
    ) {
        this.attachmentService = attachmentService;
        this.driveFolderService = driveFolderService;
        this.shareLinkService = shareLinkService;
        this.tagService = tagService;
        this.noteTagService = noteTagService;
        this.noteFolderService = noteFolderService;
        this.noteService = noteService;
        this.objectMapper = objectMapper;
    }

    public Optional<String> tryExecute(String tool, Map<String, Object> args) {
        return switch (tool) {
            case "listDriveFiles" -> Optional.of(listDriveFiles(
                    str(args.get("keyword")),
                    str(args.get("folderKeyword"))
            ));
            case "listDriveFolders" -> Optional.of(listDriveFolders());
            case "deleteDriveFile" -> Optional.of(deleteDriveFile(asLong(args.get("attachmentId")), str(args.get("fileName"))));
            case "moveDriveFile" -> Optional.of(moveDriveFile(
                    asLong(args.get("attachmentId")),
                    str(args.get("fileName")),
                    asLong(args.get("folderId")),
                    str(args.get("folderName"))
            ));
            case "linkFileToNote" -> Optional.of(linkFileToNote(
                    asLong(args.get("attachmentId")),
                    str(args.get("fileName")),
                    asLong(args.get("noteId")),
                    str(args.get("noteTitle"))
            ));
            case "listShares" -> Optional.of(listShares());
            case "createNoteShare" -> Optional.of(createNoteShare(
                    asLong(args.get("noteId")),
                    str(args.get("noteTitle")),
                    asInteger(args.get("expiresInDays")),
                    str(args.get("password"))
            ));
            case "createFileShare" -> Optional.of(createFileShare(
                    asLong(args.get("attachmentId")),
                    str(args.get("fileName")),
                    asInteger(args.get("expiresInDays")),
                    str(args.get("password"))
            ));
            case "revokeShare" -> Optional.of(revokeShare(
                    str(args.get("token")),
                    str(args.get("resourceType")),
                    asLong(args.get("noteId")),
                    str(args.get("noteTitle")),
                    asLong(args.get("attachmentId")),
                    str(args.get("fileName"))
            ));
            case "listTags" -> Optional.of(listTags());
            case "createTag" -> Optional.of(createTag(str(args.get("name")), str(args.get("color"))));
            case "deleteTag" -> Optional.of(deleteTag(asLong(args.get("tagId")), str(args.get("tagName"))));
            case "tagNote" -> Optional.of(tagNote(
                    asLong(args.get("noteId")),
                    str(args.get("noteTitle")),
                    str(args.get("tagNames"))
            ));
            case "listNoteFolders" -> Optional.of(listNoteFolders());
            case "createNoteFolder" -> Optional.of(createNoteFolder(str(args.get("name")), asLong(args.get("parentId"))));
            case "createDriveFolder" -> Optional.of(createDriveFolder(str(args.get("name")), asLong(args.get("parentId"))));
            case "moveNoteToFolder" -> Optional.of(moveNoteToFolder(
                    asLong(args.get("noteId")),
                    str(args.get("noteTitle")),
                    asLong(args.get("folderId")),
                    str(args.get("folderName"))
            ));
            default -> Optional.empty();
        };
    }

    private String listDriveFiles(String keyword, String folderKeyword) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("keyword", nullToEmpty(keyword), "folderKeyword", nullToEmpty(folderKeyword));
        try {
            Long folderId = resolveDriveFolderId(null, folderKeyword, ctx);
            List<AttachmentVO> files = attachmentService.listByWorkspace(
                    ctx.getWorkspaceId(), folderId, folderId == null ? null : false, null, null, null, ctx.getUserId()
            );
            if (StringUtils.hasText(keyword)) {
                String lower = keyword.trim().toLowerCase(Locale.ROOT);
                files = files.stream()
                        .filter(file -> file.getFileName() != null
                                && file.getFileName().toLowerCase(Locale.ROOT).contains(lower))
                        .toList();
            }
            List<Map<String, Object>> items = files.stream().limit(30).map(this::attachmentRow).toList();
            String output = writeJson(Map.of("count", files.size(), "items", items));
            ctx.recordStep("listDriveFiles", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "列出网盘文件失败：" + ex.getMessage();
            ctx.recordStep("listDriveFiles", input, output, false, null);
            return output;
        }
    }

    private String listDriveFolders() {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of();
        try {
            List<DriveFolderVO> folders = driveFolderService.listFolders(ctx.getWorkspaceId(), ctx.getUserId(), null);
            List<Map<String, Object>> items = folders.stream()
                    .map(folder -> Map.<String, Object>of(
                            "folderId", folder.getId(),
                            "name", folder.getName(),
                            "parentId", folder.getParentId() == null ? "" : folder.getParentId()
                    ))
                    .toList();
            String output = writeJson(Map.of("count", items.size(), "items", items));
            ctx.recordStep("listDriveFolders", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "列出网盘文件夹失败：" + ex.getMessage();
            ctx.recordStep("listDriveFolders", input, output, false, null);
            return output;
        }
    }

    private String deleteDriveFile(Long attachmentId, String fileName) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        attachmentId = resolveAttachmentId(attachmentId, fileName, ctx);
        Map<String, Object> input = Map.of("attachmentId", attachmentId, "fileName", nullToEmpty(fileName));
        if (attachmentId == null) {
            String output = "未找到要删除的网盘文件";
            ctx.recordStep("deleteDriveFile", input, output, false, null);
            return output;
        }
        NoteAttachmentEntity existing = attachmentService.requireOwnedAttachment(attachmentId, ctx.getUserId());
        Map<String, Object> payload = Map.of("attachmentId", attachmentId, "fileName", existing.getFileName());
        if (ctx.isDryRun()) {
            String output = "预览：将删除网盘文件「" + existing.getFileName() + "」";
            ctx.recordStep("deleteDriveFile", input, output, true, payload);
            return output;
        }
        attachmentService.delete(attachmentId, ctx.getUserId());
        String output = "已删除网盘文件「" + existing.getFileName() + "」";
        ctx.recordStep("deleteDriveFile", input, output, false, payload);
        return output;
    }

    private String moveDriveFile(Long attachmentId, String fileName, Long folderId, String folderName) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        attachmentId = resolveAttachmentId(attachmentId, fileName, ctx);
        folderId = resolveDriveFolderId(folderId, folderName, ctx);
        Map<String, Object> input = new HashMap<>();
        input.put("attachmentId", attachmentId);
        input.put("fileName", fileName);
        input.put("folderId", folderId);
        input.put("folderName", folderName);
        if (attachmentId == null) {
            String output = "未找到要移动的网盘文件";
            ctx.recordStep("moveDriveFile", input, output, false, null);
            return output;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("attachmentId", attachmentId);
        payload.put("folderId", folderId);
        if (ctx.isDryRun()) {
            String output = folderId == null
                    ? "预览：将文件移到网盘根目录"
                    : "预览：将文件移到文件夹「" + folderName + "」";
            ctx.recordStep("moveDriveFile", input, output, true, payload);
            return output;
        }
        AttachmentVO moved = attachmentService.moveToFolder(attachmentId, folderId, ctx.getUserId());
        String output = writeJson(moved);
        ctx.recordStep("moveDriveFile", input, output, false, payload);
        return output;
    }

    private String linkFileToNote(Long attachmentId, String fileName, Long noteId, String noteTitle) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        attachmentId = resolveAttachmentId(attachmentId, fileName, ctx);
        noteId = resolveNoteId(noteId, noteTitle, ctx);
        Map<String, Object> input = new HashMap<>();
        input.put("attachmentId", attachmentId);
        input.put("fileName", fileName);
        input.put("noteId", noteId);
        input.put("noteTitle", noteTitle);
        if (attachmentId == null || noteId == null) {
            String output = "关联文件需要 attachmentId/fileName 与 noteId/noteTitle";
            ctx.recordStep("linkFileToNote", input, output, false, null);
            return output;
        }
        Map<String, Object> payload = Map.of("attachmentId", attachmentId, "noteId", noteId);
        if (ctx.isDryRun()) {
            String output = "预览：将把网盘文件关联到文档";
            ctx.recordStep("linkFileToNote", input, output, true, payload);
            return output;
        }
        AttachmentVO linked = attachmentService.linkToNote(attachmentId, noteId, ctx.getUserId());
        String output = writeJson(linked);
        ctx.recordStep("linkFileToNote", input, output, false, payload);
        return output;
    }

    private String listShares() {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of();
        try {
            List<ShareLinkVO> shares = shareLinkService.listMyShares(ctx.getUserId());
            List<Map<String, Object>> items = shares.stream().map(share -> {
                Map<String, Object> row = new HashMap<>();
                row.put("token", share.getToken());
                row.put("sharePath", share.getSharePath());
                row.put("title", share.getTitle());
                row.put("resourceType", share.getResourceType());
                row.put("resourceId", share.getResourceId());
                row.put("viewCount", share.getViewCount());
                row.put("passwordProtected", share.getPasswordProtected());
                return row;
            }).toList();
            String output = writeJson(Map.of("count", items.size(), "items", items));
            ctx.recordStep("listShares", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "列出分享失败：" + ex.getMessage();
            ctx.recordStep("listShares", input, output, false, null);
            return output;
        }
    }

    private String createNoteShare(Long noteId, String noteTitle, Integer expiresInDays, String password) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        noteId = resolveNoteId(noteId, noteTitle, ctx);
        Map<String, Object> input = new HashMap<>();
        input.put("noteId", noteId);
        input.put("noteTitle", noteTitle);
        input.put("expiresInDays", expiresInDays);
        if (noteId == null) {
            String output = "未找到要分享的文档";
            ctx.recordStep("createNoteShare", input, output, false, null);
            return output;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("noteId", noteId);
        payload.put("expiresInDays", expiresInDays);
        payload.put("password", password);
        if (ctx.isDryRun()) {
            String output = "预览：将为文档创建分享链接"
                    + (StringUtils.hasText(password) ? "（含密码）" : "");
            ctx.recordStep("createNoteShare", input, output, true, payload);
            return output;
        }
        ShareLinkVO share = shareLinkService.createNoteShare(noteId, buildShareRequest(expiresInDays, password), ctx.getUserId());
        String output = writeJson(share);
        ctx.recordStep("createNoteShare", input, output, false, payload);
        return output;
    }

    private String createFileShare(Long attachmentId, String fileName, Integer expiresInDays, String password) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        attachmentId = resolveAttachmentId(attachmentId, fileName, ctx);
        Map<String, Object> input = new HashMap<>();
        input.put("attachmentId", attachmentId);
        input.put("fileName", fileName);
        input.put("expiresInDays", expiresInDays);
        if (attachmentId == null) {
            String output = "未找到要分享的网盘文件";
            ctx.recordStep("createFileShare", input, output, false, null);
            return output;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("attachmentId", attachmentId);
        payload.put("expiresInDays", expiresInDays);
        payload.put("password", password);
        if (ctx.isDryRun()) {
            String output = "预览：将为网盘文件创建分享链接"
                    + (StringUtils.hasText(password) ? "（含密码）" : "");
            ctx.recordStep("createFileShare", input, output, true, payload);
            return output;
        }
        ShareLinkVO share = shareLinkService.createAttachmentShare(
                attachmentId, buildShareRequest(expiresInDays, password), ctx.getUserId()
        );
        String output = writeJson(share);
        ctx.recordStep("createFileShare", input, output, false, payload);
        return output;
    }

    private String revokeShare(
            String token,
            String resourceType,
            Long noteId,
            String noteTitle,
            Long attachmentId,
            String fileName
    ) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = new HashMap<>();
        input.put("token", token);
        input.put("resourceType", resourceType);
        input.put("noteId", noteId);
        input.put("attachmentId", attachmentId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("resourceType", resourceType);
        if (StringUtils.hasText(token)) {
            payload.put("token", token.trim());
        } else if ("note".equalsIgnoreCase(resourceType)) {
            noteId = resolveNoteId(noteId, noteTitle, ctx);
            if (noteId == null) {
                String output = "未找到要取消分享的文档";
                ctx.recordStep("revokeShare", input, output, false, null);
                return output;
            }
            payload.put("noteId", noteId);
        } else if ("attachment".equalsIgnoreCase(resourceType)) {
            attachmentId = resolveAttachmentId(attachmentId, fileName, ctx);
            if (attachmentId == null) {
                String output = "未找到要取消分享的文件";
                ctx.recordStep("revokeShare", input, output, false, null);
                return output;
            }
            payload.put("attachmentId", attachmentId);
        } else {
            String output = "取消分享需要 token，或 resourceType=note/attachment";
            ctx.recordStep("revokeShare", input, output, false, null);
            return output;
        }
        if (ctx.isDryRun()) {
            String output = "预览：将取消分享链接";
            ctx.recordStep("revokeShare", input, output, true, payload);
            return output;
        }
        if (payload.get("token") != null) {
            shareLinkService.revokeShareByToken(String.valueOf(payload.get("token")), ctx.getUserId());
        } else if (payload.get("noteId") != null) {
            shareLinkService.revokeNoteShare(asLong(payload.get("noteId")), ctx.getUserId());
        } else {
            shareLinkService.revokeAttachmentShare(asLong(payload.get("attachmentId")), ctx.getUserId());
        }
        String output = "分享已取消";
        ctx.recordStep("revokeShare", input, output, false, payload);
        return output;
    }

    private String listTags() {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of();
        List<TagEntity> tags = tagService.lambdaQuery()
                .eq(TagEntity::getWorkspaceId, ctx.getWorkspaceId())
                .orderByAsc(TagEntity::getName)
                .list();
        List<Map<String, Object>> items = tags.stream()
                .map(tag -> Map.<String, Object>of(
                        "tagId", tag.getId(),
                        "name", tag.getName(),
                        "color", tag.getColor() == null ? "" : tag.getColor()
                ))
                .toList();
        String output = writeJson(Map.of("count", items.size(), "items", items));
        ctx.recordStep("listTags", input, output, false, null);
        return output;
    }

    private String createTag(String name, String color) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("name", nullToEmpty(name), "color", nullToEmpty(color));
        if (!StringUtils.hasText(name)) {
            String output = "标签名称不能为空";
            ctx.recordStep("createTag", input, output, false, null);
            return output;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", ctx.getWorkspaceId());
        payload.put("name", name.trim());
        payload.put("color", color);
        if (ctx.isDryRun()) {
            String output = "预览：将创建标签「" + name.trim() + "」";
            ctx.recordStep("createTag", input, output, true, payload);
            return output;
        }
        if (tagService.lambdaQuery()
                .eq(TagEntity::getWorkspaceId, ctx.getWorkspaceId())
                .eq(TagEntity::getName, name.trim())
                .exists()) {
            String output = "标签已存在：" + name.trim();
            ctx.recordStep("createTag", input, output, false, null);
            return output;
        }
        TagEntity tag = new TagEntity();
        tag.setWorkspaceId(ctx.getWorkspaceId());
        tag.setName(name.trim());
        tag.setColor(StringUtils.hasText(color) ? color.trim() : "#1677ff");
        tagService.save(tag);
        String output = writeJson(new TagVO(tag.getId(), tag.getWorkspaceId(), tag.getName(), tag.getColor()));
        ctx.recordStep("createTag", input, output, false, payload);
        return output;
    }

    private String deleteTag(Long tagId, String tagName) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        tagId = resolveTagId(tagId, tagName, ctx);
        Map<String, Object> input = Map.of("tagId", tagId, "tagName", nullToEmpty(tagName));
        if (tagId == null) {
            String output = "未找到要删除的标签";
            ctx.recordStep("deleteTag", input, output, false, null);
            return output;
        }
        TagEntity tag = tagService.getById(tagId);
        Map<String, Object> payload = Map.of("tagId", tagId, "tagName", tag.getName());
        if (ctx.isDryRun()) {
            String output = "预览：将删除标签「" + tag.getName() + "」";
            ctx.recordStep("deleteTag", input, output, true, payload);
            return output;
        }
        tagService.removeById(tagId);
        String output = "已删除标签「" + tag.getName() + "」";
        ctx.recordStep("deleteTag", input, output, false, payload);
        return output;
    }

    private String tagNote(Long noteId, String noteTitle, String tagNames) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        noteId = resolveNoteId(noteId, noteTitle, ctx);
        Map<String, Object> input = Map.of("noteId", noteId, "noteTitle", nullToEmpty(noteTitle), "tagNames", nullToEmpty(tagNames));
        if (noteId == null || !StringUtils.hasText(tagNames)) {
            String output = "打标签需要 noteId/noteTitle 与 tagNames（多个用逗号分隔）";
            ctx.recordStep("tagNote", input, output, false, null);
            return output;
        }
        List<Long> tagIds = resolveTagIds(tagNames, ctx);
        if (tagIds.isEmpty()) {
            String output = "未找到匹配的标签，请先 createTag 或检查名称";
            ctx.recordStep("tagNote", input, output, false, null);
            return output;
        }
        NoteVO existing = noteService.getNoteDetail(noteId, ctx.getUserId());
        List<Long> merged = new ArrayList<>();
        if (existing.getTags() != null) {
            for (var tag : existing.getTags()) {
                merged.add(tag.getId());
            }
        }
        merged.addAll(tagIds);
        List<Long> distinctTagIds = merged.stream().distinct().toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("noteId", noteId);
        payload.put("title", existing.getTitle());
        payload.put("content", existing.getContent());
        payload.put("contentType", existing.getContentType());
        payload.put("tagIds", distinctTagIds);
        if (ctx.isDryRun()) {
            String output = "预览：将为文档添加标签";
            ctx.recordStep("tagNote", input, output, true, payload);
            return output;
        }
        noteTagService.syncNoteTags(noteId, ctx.getWorkspaceId(), distinctTagIds);
        String output = "已为文档添加标签";
        ctx.recordStep("tagNote", input, output, false, payload);
        return output;
    }

    private String listNoteFolders() {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of();
        List<FolderVO> folders = noteFolderService.lambdaQuery()
                .eq(NoteFolderEntity::getWorkspaceId, ctx.getWorkspaceId())
                .orderByDesc(NoteFolderEntity::getSortOrder)
                .list()
                .stream()
                .map(folder -> new FolderVO(
                        folder.getId(),
                        folder.getWorkspaceId(),
                        folder.getParentId(),
                        folder.getName(),
                        folder.getSortOrder(),
                        folder.getCreatedAt()
                ))
                .toList();
        List<Map<String, Object>> items = folders.stream()
                .map(folder -> Map.<String, Object>of(
                        "folderId", folder.getId(),
                        "name", folder.getName(),
                        "parentId", folder.getParentId() == null ? "" : folder.getParentId()
                ))
                .toList();
        String output = writeJson(Map.of("count", items.size(), "items", items));
        ctx.recordStep("listNoteFolders", input, output, false, null);
        return output;
    }

    private String createNoteFolder(String name, Long parentId) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("name", nullToEmpty(name), "parentId", parentId);
        if (!StringUtils.hasText(name)) {
            String output = "分组名称不能为空";
            ctx.recordStep("createNoteFolder", input, output, false, null);
            return output;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", ctx.getWorkspaceId());
        payload.put("name", name.trim());
        payload.put("parentId", parentId);
        if (ctx.isDryRun()) {
            String output = "预览：将创建文档分组「" + name.trim() + "」";
            ctx.recordStep("createNoteFolder", input, output, true, payload);
            return output;
        }
        NoteFolderEntity folder = new NoteFolderEntity();
        folder.setWorkspaceId(ctx.getWorkspaceId());
        folder.setParentId(parentId);
        folder.setName(name.trim());
        folder.setSortOrder(0);
        noteFolderService.save(folder);
        String output = writeJson(new FolderVO(
                folder.getId(), folder.getWorkspaceId(), folder.getParentId(),
                folder.getName(), folder.getSortOrder(), folder.getCreatedAt()
        ));
        ctx.recordStep("createNoteFolder", input, output, false, payload);
        return output;
    }

    private String createDriveFolder(String name, Long parentId) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("name", nullToEmpty(name), "parentId", parentId);
        if (!StringUtils.hasText(name)) {
            String output = "网盘文件夹名称不能为空";
            ctx.recordStep("createDriveFolder", input, output, false, null);
            return output;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", ctx.getWorkspaceId());
        payload.put("name", name.trim());
        payload.put("parentId", parentId);
        if (ctx.isDryRun()) {
            String output = "预览：将创建网盘文件夹「" + name.trim() + "」";
            ctx.recordStep("createDriveFolder", input, output, true, payload);
            return output;
        }
        DriveFolderCreateRequest request = new DriveFolderCreateRequest();
        request.setWorkspaceId(ctx.getWorkspaceId());
        request.setParentId(parentId);
        request.setName(name.trim());
        DriveFolderVO folder = driveFolderService.createFolder(request, ctx.getUserId());
        String output = writeJson(folder);
        ctx.recordStep("createDriveFolder", input, output, false, payload);
        return output;
    }

    private String moveNoteToFolder(Long noteId, String noteTitle, Long folderId, String folderName) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        noteId = resolveNoteId(noteId, noteTitle, ctx);
        folderId = resolveNoteFolderId(folderId, folderName, ctx);
        Map<String, Object> input = new HashMap<>();
        input.put("noteId", noteId);
        input.put("noteTitle", noteTitle);
        input.put("folderId", folderId);
        input.put("folderName", folderName);
        if (noteId == null) {
            String output = "未找到要移动的文档";
            ctx.recordStep("moveNoteToFolder", input, output, false, null);
            return output;
        }
        NoteVO existing = noteService.getNoteDetail(noteId, ctx.getUserId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("noteId", noteId);
        payload.put("title", existing.getTitle());
        payload.put("content", existing.getContent());
        payload.put("contentType", existing.getContentType());
        payload.put("folderId", folderId);
        if (ctx.isDryRun()) {
            String output = folderId == null
                    ? "预览：将文档移到未分组"
                    : "预览：将文档移到分组「" + folderName + "」";
            ctx.recordStep("moveNoteToFolder", input, output, true, payload);
            return output;
        }
        com.noto.zhihui.dto.note.NoteUpdateRequest request = new com.noto.zhihui.dto.note.NoteUpdateRequest();
        request.setTitle(existing.getTitle());
        request.setContent(existing.getContent());
        request.setContentType(existing.getContentType());
        request.setFolderId(folderId);
        NoteVO updated = noteService.updateNote(noteId, request, ctx.getUserId());
        String output = writeJson(updated);
        ctx.recordStep("moveNoteToFolder", input, output, false, payload);
        return output;
    }

    private Map<String, Object> attachmentRow(AttachmentVO file) {
        Map<String, Object> row = new HashMap<>();
        row.put("attachmentId", file.getId());
        row.put("fileName", file.getFileName());
        row.put("fileType", file.getFileType());
        row.put("fileSize", file.getFileSize());
        row.put("folderName", file.getFolderName());
        return row;
    }

    private Long resolveAttachmentId(Long attachmentId, String fileName, AgentExecutionContext ctx) {
        if (attachmentId != null && attachmentId > 0) {
            return attachmentId;
        }
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        return attachmentService.listByWorkspace(ctx.getWorkspaceId(), null, null, null, null, null, ctx.getUserId())
                .stream()
                .filter(file -> file.getFileName() != null
                        && file.getFileName().toLowerCase(Locale.ROOT).contains(lower))
                .map(AttachmentVO::getId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveDriveFolderId(Long folderId, String folderName, AgentExecutionContext ctx) {
        if (folderId != null && folderId > 0) {
            return folderId;
        }
        if (!StringUtils.hasText(folderName)) {
            return null;
        }
        return driveFolderService.listFolders(ctx.getWorkspaceId(), ctx.getUserId(), null).stream()
                .filter(folder -> folder.getName() != null && (
                        folder.getName().equals(folderName.trim())
                                || folder.getName().contains(folderName.trim())
                                || folderName.trim().contains(folder.getName())
                ))
                .map(DriveFolderVO::getId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveNoteFolderId(Long folderId, String folderName, AgentExecutionContext ctx) {
        if (folderId != null && folderId > 0) {
            return folderId;
        }
        if (!StringUtils.hasText(folderName)) {
            return null;
        }
        return noteFolderService.lambdaQuery()
                .eq(NoteFolderEntity::getWorkspaceId, ctx.getWorkspaceId())
                .list()
                .stream()
                .filter(folder -> folder.getName() != null && (
                        folder.getName().equals(folderName.trim())
                                || folder.getName().contains(folderName.trim())
                                || folderName.trim().contains(folder.getName())
                ))
                .map(NoteFolderEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveNoteId(Long noteId, String title, AgentExecutionContext ctx) {
        if (noteId != null && noteId > 0) {
            return noteId;
        }
        if (!StringUtils.hasText(title)) {
            return null;
        }
        try {
            return noteService.pageNotes(
                    ctx.getUserId(), 1, 5, title.trim(), 0, null,
                    ctx.getWorkspaceId(), null, null, true
            ).getRecords().stream().map(NoteVO::getId).findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private Long resolveTagId(Long tagId, String tagName, AgentExecutionContext ctx) {
        if (tagId != null && tagId > 0) {
            return tagId;
        }
        if (!StringUtils.hasText(tagName)) {
            return null;
        }
        List<TagEntity> matched = tagService.lambdaQuery()
                .eq(TagEntity::getWorkspaceId, ctx.getWorkspaceId())
                .eq(TagEntity::getName, tagName.trim())
                .list();
        return matched.isEmpty() ? null : matched.get(0).getId();
    }

    private List<Long> resolveTagIds(String tagNames, AgentExecutionContext ctx) {
        List<Long> ids = new ArrayList<>();
        for (String part : tagNames.split("[,，、]")) {
            String name = part.trim();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            tagService.lambdaQuery()
                    .eq(TagEntity::getWorkspaceId, ctx.getWorkspaceId())
                    .and(query -> query.eq(TagEntity::getName, name).or().like(TagEntity::getName, name))
                    .list()
                    .stream()
                    .map(TagEntity::getId)
                    .forEach(ids::add);
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private ShareCreateRequest buildShareRequest(Integer expiresInDays, String password) {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setExpiresInDays(expiresInDays);
        request.setPassword(password);
        return request;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text) || text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"error\":\"json serialize failed\"}";
        }
    }
}
