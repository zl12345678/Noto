package com.noto.zhihui.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.noto.zhihui.common.constants.TodoHorizon;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.reminder.ReminderCreateRequest;
import com.noto.zhihui.dto.todo.TodoCreateRequest;
import com.noto.zhihui.dto.todo.TodoUpdateRequest;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.entity.ReminderEntity;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.entity.UserEntity;
import com.noto.zhihui.entity.UserSettingEntity;
import com.noto.zhihui.mapper.UserSettingMapper;
import com.noto.zhihui.service.NoteFolderService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.NoteTagService;
import com.noto.zhihui.service.ReminderService;
import com.noto.zhihui.service.TagService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.service.UserService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.todo.TodoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(100)
@ConditionalOnProperty(prefix = "noto.demo", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String SEED_MARKER_KEY = "noto.demo.seeded";
    private static final String SEED_MARKER_V3 = "v3";

    private static final Map<String, String> TAG_COLORS = Map.of(
            "演示", "#1677ff",
            "工作", "#13c2c2",
            "学习", "#722ed1",
            "会议", "#fa8c16",
            "产品", "#52c41a",
            "客户", "#eb2f96",
            "待整理", "#8c8c8c",
            "复盘", "#2f54eb"
    );

    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final NoteFolderService noteFolderService;
    private final NoteService noteService;
    private final NoteTagService noteTagService;
    private final TodoService todoService;
    private final ReminderService reminderService;
    private final TagService tagService;
    private final UserSettingMapper userSettingMapper;
    private final NotoDemoProperties demoProperties;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            UserService userService,
            WorkspaceService workspaceService,
            NoteFolderService noteFolderService,
            NoteService noteService,
            NoteTagService noteTagService,
            TodoService todoService,
            ReminderService reminderService,
            TagService tagService,
            UserSettingMapper userSettingMapper,
            NotoDemoProperties demoProperties,
            PasswordEncoder passwordEncoder
    ) {
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.noteFolderService = noteFolderService;
        this.noteService = noteService;
        this.noteTagService = noteTagService;
        this.todoService = todoService;
        this.reminderService = reminderService;
        this.tagService = tagService;
        this.userSettingMapper = userSettingMapper;
        this.demoProperties = demoProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            UserEntity targetUser = resolveTargetUser();
            if (targetUser != null) {
                refreshDemoTodoDueDates(targetUser.getId());
            }
            seedIfNeeded();
        } catch (Exception ex) {
            log.warn("Demo data seed skipped: {}", ex.getMessage());
        }
    }

    @Transactional
    protected void seedIfNeeded() {
        UserEntity targetUser = resolveTargetUser();
        String username = resolveUsername();
        if (targetUser == null) {
            log.info("Demo seed skipped: target user '{}' not found", username);
            return;
        }
        Long userId = targetUser.getId();
        if (isAlreadySeeded(userId, SEED_MARKER_V3)) {
            log.info("Demo data v3 already present for {} ({} notes expected, due dates refreshed)",
                    username,
                    demoNotes().size());
            return;
        }

        log.info("Demo data seed starting for {} (v3, target {} notes)...",
                username,
                demoNotes().size());

        Long workspaceId = workspaceService.ensureDefaultWorkspace(userId);
        LocalDateTime now = LocalDateTime.now();

        Map<String, Long> folderIds = new HashMap<>();
        int sort = 1;
        for (String folderName : DemoDataCatalog.folderNames()) {
            folderIds.put(folderName, findOrCreateFolder(workspaceId, folderName, sort++));
        }
        Long defaultFolderId = resolveDefaultFolderId(workspaceId);

        Map<String, Long> tagIds = new HashMap<>();
        for (String tagName : DemoDataCatalog.tagDefinitions()) {
            tagIds.put(tagName, findOrCreateTag(workspaceId, tagName, TAG_COLORS.getOrDefault(tagName, "#1677ff")));
        }

        Map<String, Long> noteIdsByTitle = new HashMap<>();
        int noteCount = 0;
        for (DemoDataCatalog.NoteSeed seed : demoNotes()) {
            Long folderId = folderIds.getOrDefault(seed.folder(), defaultFolderId);
            Long parentId = seed.parentTitle() == null ? null : noteIdsByTitle.get(seed.parentTitle());
            List<Long> linkedTagIds = seed.tags().stream()
                    .map(tagIds::get)
                    .filter(id -> id != null)
                    .toList();
            NoteVO note = findOrCreateNote(
                    userId,
                    workspaceId,
                    folderId,
                    parentId,
                    seed.title(),
                    seed.content(),
                    linkedTagIds
            );
            touchNoteMeta(note.getId(), userId, seed.daysAgo(), seed.favorite());
            noteIdsByTitle.put(seed.title(), note.getId());
            noteCount++;
        }

        int todoCount = 0;
        TodoVO todoQ2 = null;
        TodoVO todoEmail = null;
        for (DemoDataCatalog.TodoSeed seed : DemoDataCatalog.extraTodos()) {
            Long noteId = noteIdsByTitle.get(seed.noteTitle());
            if (noteId == null) {
                continue;
            }
            LocalDateTime dueAt = seed.dueDaysOffset() >= 0
                    ? now.plusDays(seed.dueDaysOffset()).withHour(seed.dueHour()).withMinute(0).withSecond(0).withNano(0)
                    : now.minusDays(-seed.dueDaysOffset()).withHour(seed.dueHour()).withMinute(0).withSecond(0).withNano(0);
            TodoVO todo = upsertTodo(userId, workspaceId, noteId, seed.title(), seed.horizon(), dueAt, seed.status());
            if ("整理 Q2 目标".equals(seed.title())) {
                todoQ2 = todo;
            }
            if ("回复客户邮件".equals(seed.title())) {
                todoEmail = todo;
            }
            todoCount++;
        }

        if (todoQ2 != null) {
            createReminderIfAbsent(userId, workspaceId, todoQ2.getId(), now.plusHours(2),
                    "记得整理 Q2 目标，可从周会纪要提取子项");
        }
        if (todoEmail != null) {
            createReminderIfAbsent(userId, workspaceId, todoEmail.getId(), now.plusDays(1).withHour(9).withMinute(0),
                    "客户邮件需在今日跟进");
        }

        markSeeded(userId, SEED_MARKER_V3);
        log.info(
                "Demo data v3 seeded for {}: {} notes, {} folders, {} todos, {} tags, 2 reminders",
                username,
                noteCount,
                folderIds.size(),
                todoCount,
                tagIds.size()
        );
    }

    private UserEntity resolveTargetUser() {
        String username = resolveUsername();
        UserEntity user = userService.findByUsername(username);
        if (user != null) {
            syncDemoPasswordIfNeeded(user);
            workspaceService.ensureDefaultWorkspace(user.getId());
            return user;
        }
        if (!demoProperties.isCreateUser()) {
            return null;
        }
        return createDemoUser(username);
    }

    private void syncDemoPasswordIfNeeded(UserEntity user) {
        if (!demoProperties.isResetPassword()) {
            return;
        }
        String username = resolveUsername();
        if ("admin".equals(username) || !username.equals(user.getUsername())) {
            return;
        }
        String password = resolvePassword();
        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            return;
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        userService.updateById(user);
        log.info("Demo user '{}' password synchronized from demo configuration", username);
    }

    private UserEntity createDemoUser(String username) {
        String email = normalizeOrDefault(demoProperties.getEmail(), username + "@noto.local");
        UserEntity existingEmailUser = userService.findByEmail(email);
        if (existingEmailUser != null) {
            log.warn("Demo seed skipped: email '{}' already belongs to another user", email);
            return null;
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(normalizeOrDefault(demoProperties.getNickname(), "演示账号"));
        user.setPasswordHash(passwordEncoder.encode(resolvePassword()));
        user.setStatus(0);
        userService.save(user);
        workspaceService.createDefaultWorkspace(user.getId());
        log.info("Demo user '{}' created for demo seed", username);
        return user;
    }

    private String resolveUsername() {
        return normalizeOrDefault(demoProperties.getUsername(), "admin");
    }

    private String resolvePassword() {
        return normalizeOrDefault(demoProperties.getPassword(), "admin123");
    }

    private List<DemoDataCatalog.NoteSeed> demoNotes() {
        return DemoDataCatalog.allNotes(LocalDate.now(), resolveUsername(), resolvePassword());
    }

    private String normalizeOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private void refreshDemoTodoDueDates(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (DemoDataCatalog.TodoSeed seed : DemoDataCatalog.extraTodos()) {
            if (seed.status() > 1) {
                continue;
            }
            LocalDateTime dueAt = seed.dueDaysOffset() >= 0
                    ? now.plusDays(seed.dueDaysOffset()).withHour(seed.dueHour()).withMinute(0).withSecond(0).withNano(0)
                    : now.minusDays(-seed.dueDaysOffset()).withHour(seed.dueHour()).withMinute(0).withSecond(0).withNano(0);
            todoService.lambdaUpdate()
                    .eq(TodoItemEntity::getCreatedBy, userId)
                    .eq(TodoItemEntity::getTitle, seed.title())
                    .in(TodoItemEntity::getStatus, 0, 1)
                    .set(TodoItemEntity::getDueAt, dueAt)
                    .update();
        }
    }

    private boolean isAlreadySeeded(Long userId, String markerValue) {
        Long count = userSettingMapper.selectCount(
                Wrappers.<UserSettingEntity>lambdaQuery()
                        .eq(UserSettingEntity::getUserId, userId)
                        .eq(UserSettingEntity::getSettingKey, SEED_MARKER_KEY)
                        .eq(UserSettingEntity::getSettingValue, markerValue)
        );
        return count != null && count > 0;
    }

    private void markSeeded(Long userId, String markerValue) {
        Long count = userSettingMapper.selectCount(
                Wrappers.<UserSettingEntity>lambdaQuery()
                        .eq(UserSettingEntity::getUserId, userId)
                        .eq(UserSettingEntity::getSettingKey, SEED_MARKER_KEY)
                        .eq(UserSettingEntity::getSettingValue, markerValue)
        );
        if (count != null && count > 0) {
            return;
        }
        UserSettingEntity entity = new UserSettingEntity();
        entity.setUserId(userId);
        entity.setSettingKey(SEED_MARKER_KEY);
        entity.setSettingValue(markerValue);
        userSettingMapper.insert(entity);
    }

    private Long resolveDefaultFolderId(Long workspaceId) {
        NoteFolderEntity folder = noteFolderService.lambdaQuery()
                .eq(NoteFolderEntity::getWorkspaceId, workspaceId)
                .orderByAsc(NoteFolderEntity::getSortOrder)
                .last("LIMIT 1")
                .one();
        return folder == null ? null : folder.getId();
    }

    private Long findOrCreateFolder(Long workspaceId, String name, int sortOrder) {
        NoteFolderEntity existing = noteFolderService.lambdaQuery()
                .eq(NoteFolderEntity::getWorkspaceId, workspaceId)
                .eq(NoteFolderEntity::getName, name)
                .one();
        if (existing != null) {
            return existing.getId();
        }
        NoteFolderEntity folder = new NoteFolderEntity();
        folder.setWorkspaceId(workspaceId);
        folder.setName(name);
        folder.setSortOrder(sortOrder);
        noteFolderService.save(folder);
        return folder.getId();
    }

    private Long findOrCreateTag(Long workspaceId, String name, String color) {
        TagEntity existing = tagService.lambdaQuery()
                .eq(TagEntity::getWorkspaceId, workspaceId)
                .eq(TagEntity::getName, name)
                .one();
        if (existing != null) {
            return existing.getId();
        }
        TagEntity tag = new TagEntity();
        tag.setWorkspaceId(workspaceId);
        tag.setName(name);
        tag.setColor(color);
        tagService.save(tag);
        return tag.getId();
    }

    private NoteVO findOrCreateNote(
            Long userId,
            Long workspaceId,
            Long folderId,
            Long parentId,
            String title,
            String content,
            List<Long> tagIds
    ) {
        NoteEntity existing = noteService.lambdaQuery()
                .eq(NoteEntity::getCreatedBy, userId)
                .eq(NoteEntity::getWorkspaceId, workspaceId)
                .eq(NoteEntity::getTitle, title)
                .one();
        if (existing != null) {
            if (tagIds != null && !tagIds.isEmpty()) {
                noteTagService.syncNoteTags(existing.getId(), workspaceId, tagIds);
            }
            return noteService.getNoteDetail(existing.getId(), userId);
        }
        NoteCreateRequest request = new NoteCreateRequest();
        request.setWorkspaceId(workspaceId);
        request.setFolderId(folderId);
        request.setParentId(parentId);
        request.setTitle(title);
        request.setContent(content);
        request.setContentType("markdown");
        request.setTagIds(tagIds);
        return noteService.createNote(request, userId);
    }

    private void touchNoteMeta(Long noteId, Long userId, int daysAgo, boolean favorite) {
        int hour = 9 + (daysAgo % 8);
        int minute = (daysAgo * 11) % 60;
        LocalDateTime edited = LocalDateTime.now()
                .minusDays(daysAgo)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
        noteService.lambdaUpdate()
                .eq(NoteEntity::getId, noteId)
                .eq(NoteEntity::getCreatedBy, userId)
                .set(NoteEntity::getLastEditedAt, edited)
                .set(NoteEntity::getIsFavorite, favorite)
                .update();
    }

    private TodoVO upsertTodo(
            Long userId,
            Long workspaceId,
            Long noteId,
            String title,
            String horizon,
            LocalDateTime dueAt,
            int status
    ) {
        TodoItemEntity existing = todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(TodoItemEntity::getTitle, title)
                .one();
        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() <= 1) {
                TodoUpdateRequest update = new TodoUpdateRequest();
                update.setTitle(title);
                update.setHorizon(horizon);
                update.setDueAt(dueAt);
                todoService.updateTodo(existing.getId(), update, userId);
            }
            if (status != 0 && existing.getStatus() != null && existing.getStatus() != status) {
                return todoService.updateStatus(existing.getId(), status, userId);
            }
            return todoService.getTodoDetail(existing.getId(), userId);
        }
        TodoCreateRequest request = new TodoCreateRequest();
        request.setWorkspaceId(workspaceId);
        request.setNoteId(noteId);
        request.setTitle(title);
        request.setHorizon(TodoHorizon.normalize(horizon));
        request.setDueAt(dueAt);
        request.setPriority(1);
        TodoVO created = todoService.createTodo(request, userId);
        if (status != 0) {
            return todoService.updateStatus(created.getId(), status, userId);
        }
        return created;
    }

    private void createReminderIfAbsent(
            Long userId,
            Long workspaceId,
            Long todoId,
            LocalDateTime triggerAt,
            String message
    ) {
        Long existing = reminderService.lambdaQuery()
                .eq(ReminderEntity::getCreatedBy, userId)
                .eq(ReminderEntity::getTodoId, todoId)
                .count();
        if (existing != null && existing > 0) {
            return;
        }
        ReminderCreateRequest reminder = new ReminderCreateRequest();
        reminder.setWorkspaceId(workspaceId);
        reminder.setTodoId(todoId);
        reminder.setReminderType("due");
        reminder.setTriggerAt(triggerAt);
        reminder.setMessage(message);
        reminderService.createReminder(reminder, userId);
    }
}
