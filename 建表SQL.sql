-- Noto · 知微 建表 SQL
-- 数据库：PostgreSQL

CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(512),
    status SMALLINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS workspace (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    type SMALLINT NOT NULL DEFAULT 0,
    description VARCHAR(512),
    status SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS note_folder (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    parent_id BIGINT,
    name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS note (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    folder_id BIGINT,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(32) NOT NULL DEFAULT 'markdown',
    summary TEXT,
    status SMALLINT NOT NULL DEFAULT 0,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    last_edited_at TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS note_block (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    block_index INT NOT NULL,
    block_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    embedding_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS tag (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    color VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_tag_workspace_name UNIQUE (workspace_id, name)
);

CREATE TABLE IF NOT EXISTS note_tag (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_note_tag UNIQUE (note_id, tag_id)
);

CREATE TABLE IF NOT EXISTS note_attachment (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    file_type VARCHAR(64),
    file_size BIGINT NOT NULL DEFAULT 0,
    file_url VARCHAR(512) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

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
);

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
);

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
);

CREATE TABLE IF NOT EXISTS reminder (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    todo_id BIGINT NOT NULL,
    reminder_type VARCHAR(32) NOT NULL,
    trigger_at TIMESTAMP NOT NULL,
    message VARCHAR(512),
    status SMALLINT NOT NULL DEFAULT 0,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS search_index_task (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

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
);

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
);

CREATE TABLE IF NOT EXISTS user_setting (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_user_setting UNIQUE (user_id, setting_key)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_workspace_owner_user_id ON workspace(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_note_workspace_id ON note(workspace_id);
CREATE INDEX IF NOT EXISTS idx_note_folder_id ON note(folder_id);
CREATE INDEX IF NOT EXISTS idx_note_last_edited_at ON note(last_edited_at);
CREATE INDEX IF NOT EXISTS idx_note_block_note_id ON note_block(note_id);
CREATE INDEX IF NOT EXISTS idx_note_tag_note_id ON note_tag(note_id);
CREATE INDEX IF NOT EXISTS idx_note_tag_tag_id ON note_tag(tag_id);
CREATE INDEX IF NOT EXISTS idx_tag_workspace_id ON tag(workspace_id);
CREATE INDEX IF NOT EXISTS idx_todo_workspace_status ON todo_item(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_todo_due_at ON todo_item(due_at);
CREATE INDEX IF NOT EXISTS idx_reminder_status_trigger_at ON reminder(status, trigger_at);
CREATE INDEX IF NOT EXISTS idx_ai_task_status_type ON ai_task(status, task_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_created_at ON audit_log(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_search_index_task_status ON search_index_task(status);
CREATE INDEX IF NOT EXISTS idx_ai_chat_session_user_id ON ai_chat_session(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_chat_message_session_id ON ai_chat_message(session_id);
