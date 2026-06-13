package com.noto.zhihui.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 在业务 Bean（如 AuthController）初始化前执行 schema 补丁，避免新字段导致启动失败。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseSchemaMigrator {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS todo_item (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL,
                    note_id BIGINT,
                    title VARCHAR(256) NOT NULL,
                    description TEXT,
                    priority SMALLINT NOT NULL DEFAULT 1,
                    status SMALLINT NOT NULL DEFAULT 0,
                    due_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    created_by BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_todo_workspace_status ON todo_item(workspace_id, status)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_todo_due_at ON todo_item(due_at)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE todo_item ADD COLUMN IF NOT EXISTS horizon VARCHAR(16) NOT NULL DEFAULT 'action'
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_todo_horizon_status ON todo_item(horizon, status)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reminder (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL,
                    todo_id BIGINT NOT NULL,
                    reminder_type VARCHAR(32) NOT NULL,
                    trigger_at TIMESTAMP NOT NULL,
                    message VARCHAR(512),
                    status SMALLINT NOT NULL DEFAULT 0,
                    sent_at TIMESTAMP,
                    created_by BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_reminder_status_trigger_at ON reminder(status, trigger_at)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE reminder ADD COLUMN IF NOT EXISTS created_by BIGINT NOT NULL DEFAULT 0
                """);
        jdbcTemplate.execute("""
                ALTER TABLE note ADD COLUMN IF NOT EXISTS parent_id BIGINT
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_parent_id ON note(parent_id)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE workspace ADD COLUMN IF NOT EXISTS home_note_id BIGINT
                """);
        jdbcTemplate.execute("""
                ALTER TABLE note ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_chat_session (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    session_type VARCHAR(32) NOT NULL,
                    title VARCHAR(256),
                    latest_message_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_ai_chat_session_user_workspace
                ON ai_chat_session(user_id, workspace_id, latest_message_at DESC)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_chat_message (
                    id BIGSERIAL PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    role VARCHAR(32) NOT NULL,
                    content TEXT NOT NULL,
                    token_usage INT NOT NULL DEFAULT 0,
                    model_name VARCHAR(128),
                    referenced_note_ids VARCHAR(1024),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_ai_chat_message_session
                ON ai_chat_message(session_id, created_at)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT,
                    user_id BIGINT,
                    action_type VARCHAR(64) NOT NULL,
                    resource_type VARCHAR(64),
                    resource_id BIGINT,
                    detail TEXT,
                    trace_id VARCHAR(128),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_audit_log_user_created_at ON audit_log(user_id, created_at)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_rag_chunk (
                    id BIGSERIAL PRIMARY KEY,
                    note_id BIGINT NOT NULL,
                    workspace_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    chunk_index INT NOT NULL,
                    content TEXT NOT NULL,
                    offset_start INT NOT NULL DEFAULT 0,
                    offset_end INT NOT NULL DEFAULT 0,
                    embedding TEXT NOT NULL,
                    embedding_model VARCHAR(64),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_rag_chunk_note ON note_rag_chunk(note_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_rag_chunk_workspace_user
                ON note_rag_chunk(workspace_id, user_id)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_task (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    task_type VARCHAR(32) NOT NULL,
                    target_id BIGINT,
                    status SMALLINT NOT NULL DEFAULT 0,
                    input_content TEXT,
                    output_content TEXT,
                    retry_count INT NOT NULL DEFAULT 0,
                    error_message VARCHAR(1024),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_ai_task_status_type ON ai_task(status, task_type)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_setting (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    setting_key VARCHAR(128) NOT NULL,
                    setting_value TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    CONSTRAINT uk_user_setting UNIQUE (user_id, setting_key)
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE user_setting ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_attachment (
                    id BIGSERIAL PRIMARY KEY,
                    note_id BIGINT NOT NULL,
                    file_name VARCHAR(256) NOT NULL,
                    file_type VARCHAR(64),
                    file_size BIGINT NOT NULL DEFAULT 0,
                    file_url VARCHAR(512) NOT NULL,
                    storage_key VARCHAR(512) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE note_attachment ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_attachment_note_id ON note_attachment(note_id)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE note_attachment ADD COLUMN IF NOT EXISTS workspace_id BIGINT
                """);
        jdbcTemplate.execute("""
                ALTER TABLE note_attachment ADD COLUMN IF NOT EXISTS uploaded_by BIGINT
                """);
        jdbcTemplate.execute("""
                ALTER TABLE note_attachment ALTER COLUMN note_id DROP NOT NULL
                """);
        jdbcTemplate.execute("""
                UPDATE note_attachment na
                SET workspace_id = n.workspace_id
                FROM note n
                WHERE na.note_id = n.id AND na.workspace_id IS NULL
                """);
        jdbcTemplate.execute("""
                UPDATE note_attachment na
                SET uploaded_by = n.created_by
                FROM note n
                WHERE na.note_id = n.id AND na.uploaded_by IS NULL
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_attachment_link (
                    id BIGSERIAL PRIMARY KEY,
                    attachment_id BIGINT NOT NULL,
                    note_id BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_attachment_link_attachment
                ON note_attachment_link(attachment_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_attachment_link_note
                ON note_attachment_link(note_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_attachment_workspace
                ON note_attachment(workspace_id, created_at DESC)
                """);
        jdbcTemplate.execute("""
                INSERT INTO note_attachment_link (attachment_id, note_id, created_at, updated_at, deleted)
                SELECT na.id, na.note_id, na.created_at, na.updated_at, FALSE
                FROM note_attachment na
                WHERE na.note_id IS NOT NULL
                  AND na.deleted = FALSE
                  AND NOT EXISTS (
                    SELECT 1 FROM note_attachment_link l
                    WHERE l.attachment_id = na.id AND l.note_id = na.note_id AND l.deleted = FALSE
                  )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS drive_folder (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_drive_folder_workspace ON drive_folder(workspace_id)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE drive_folder ADD COLUMN IF NOT EXISTS parent_id BIGINT
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_drive_folder_parent ON drive_folder(parent_id)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE note_attachment ADD COLUMN IF NOT EXISTS folder_id BIGINT
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_note_attachment_folder ON note_attachment(folder_id)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS share_link (
                    id BIGSERIAL PRIMARY KEY,
                    token VARCHAR(64) NOT NULL,
                    resource_type VARCHAR(32) NOT NULL,
                    resource_id BIGINT NOT NULL,
                    created_by BIGINT NOT NULL,
                    expires_at TIMESTAMP,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_share_link_token ON share_link(token)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_share_link_resource
                ON share_link(resource_type, resource_id, enabled)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE share_link ADD COLUMN IF NOT EXISTS password_hash VARCHAR(128)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE share_link ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0
                """);
        jdbcTemplate.execute("""
                ALTER TABLE share_link ADD COLUMN IF NOT EXISTS last_viewed_at TIMESTAMP
                """);
        jdbcTemplate.execute("""
                ALTER TABLE share_link ALTER COLUMN resource_id DROP NOT NULL
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS share_link_item (
                    id BIGSERIAL PRIMARY KEY,
                    share_link_id BIGINT NOT NULL,
                    resource_type VARCHAR(32) NOT NULL,
                    resource_id BIGINT NOT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_share_link_item_link
                ON share_link_item(share_link_id, sort_order)
                """);
        jdbcTemplate.execute("""
                DELETE FROM note_attachment_link
                WHERE attachment_id IS NULL OR note_id IS NULL
                """);
        jdbcTemplate.execute("""
                UPDATE note_attachment na
                SET workspace_id = n.workspace_id,
                    uploaded_by = COALESCE(na.uploaded_by, n.created_by)
                FROM note n
                WHERE na.note_id = n.id
                  AND (na.workspace_id IS NULL OR na.uploaded_by IS NULL)
                """);
    }
}
