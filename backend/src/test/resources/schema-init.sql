-- Noto · 知微 建表 SQL
-- 数据库：PostgreSQL

CREATE TABLE IF NOT EXISTS sys_user (
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

COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.id IS '主键ID';
COMMENT ON COLUMN sys_user.username IS '用户名，用于登录和唯一识别';
COMMENT ON COLUMN sys_user.email IS '邮箱，用于联系和账号找回';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希值，禁止存储明文密码';
COMMENT ON COLUMN sys_user.nickname IS '用户昵称，页面展示名称';
COMMENT ON COLUMN sys_user.avatar_url IS '用户头像地址';
COMMENT ON COLUMN sys_user.status IS '用户状态：0正常，1禁用';
COMMENT ON COLUMN sys_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间';
COMMENT ON COLUMN sys_user.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS workspace (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    type SMALLINT NOT NULL DEFAULT 0,
    description VARCHAR(512),
    status SMALLINT NOT NULL DEFAULT 0,
    home_note_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE workspace IS '工作区表';
COMMENT ON COLUMN workspace.id IS '主键ID';
COMMENT ON COLUMN workspace.owner_user_id IS '工作区所属用户ID';
COMMENT ON COLUMN workspace.name IS '工作区名称';
COMMENT ON COLUMN workspace.type IS '工作区类型：0个人，1团队';
COMMENT ON COLUMN workspace.description IS '工作区描述';
COMMENT ON COLUMN workspace.status IS '状态：0正常，1停用';
COMMENT ON COLUMN workspace.home_note_id IS '知识库首页文档ID';
COMMENT ON COLUMN workspace.created_at IS '创建时间';
COMMENT ON COLUMN workspace.updated_at IS '更新时间';
COMMENT ON COLUMN workspace.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE note_folder IS '笔记文件夹表';
COMMENT ON COLUMN note_folder.id IS '主键ID';
COMMENT ON COLUMN note_folder.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN note_folder.parent_id IS '父文件夹ID，支持层级结构';
COMMENT ON COLUMN note_folder.name IS '文件夹名称';
COMMENT ON COLUMN note_folder.sort_order IS '排序值，越大越靠前';
COMMENT ON COLUMN note_folder.created_at IS '创建时间';
COMMENT ON COLUMN note_folder.updated_at IS '更新时间';
COMMENT ON COLUMN note_folder.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS note (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    folder_id BIGINT,
    parent_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
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

COMMENT ON TABLE note IS '笔记表';
COMMENT ON COLUMN note.id IS '主键ID';
COMMENT ON COLUMN note.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN note.folder_id IS '所属文件夹ID';
COMMENT ON COLUMN note.parent_id IS '父文档ID，支持子文档层级';
COMMENT ON COLUMN note.sort_order IS '树内排序，越大越靠前';
COMMENT ON COLUMN note.title IS '笔记标题';
COMMENT ON COLUMN note.content IS '笔记正文内容';
COMMENT ON COLUMN note.content_type IS '内容类型：markdown、richtext等';
COMMENT ON COLUMN note.summary IS '笔记摘要';
COMMENT ON COLUMN note.status IS '状态：0正常，1归档';
COMMENT ON COLUMN note.is_favorite IS '是否收藏';
COMMENT ON COLUMN note.last_edited_at IS '最后编辑时间';
COMMENT ON COLUMN note.created_by IS '创建人ID';
COMMENT ON COLUMN note.updated_by IS '最后更新人ID';
COMMENT ON COLUMN note.created_at IS '创建时间';
COMMENT ON COLUMN note.updated_at IS '更新时间';
COMMENT ON COLUMN note.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE note_block IS '笔记分块表';
COMMENT ON COLUMN note_block.id IS '主键ID';
COMMENT ON COLUMN note_block.note_id IS '所属笔记ID';
COMMENT ON COLUMN note_block.block_index IS '分块顺序号';
COMMENT ON COLUMN note_block.block_type IS '分块类型';
COMMENT ON COLUMN note_block.content IS '分块内容';
COMMENT ON COLUMN note_block.token_count IS '分块 token 数';
COMMENT ON COLUMN note_block.embedding_id IS '向量索引ID';
COMMENT ON COLUMN note_block.created_at IS '创建时间';
COMMENT ON COLUMN note_block.updated_at IS '更新时间';
COMMENT ON COLUMN note_block.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE tag IS '标签表';
COMMENT ON COLUMN tag.id IS '主键ID';
COMMENT ON COLUMN tag.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN tag.name IS '标签名称';
COMMENT ON COLUMN tag.color IS '标签颜色';
COMMENT ON COLUMN tag.created_at IS '创建时间';
COMMENT ON COLUMN tag.updated_at IS '更新时间';
COMMENT ON COLUMN tag.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS note_tag (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_note_tag UNIQUE (note_id, tag_id)
);

COMMENT ON TABLE note_tag IS '笔记标签关联表';
COMMENT ON COLUMN note_tag.id IS '主键ID';
COMMENT ON COLUMN note_tag.note_id IS '笔记ID';
COMMENT ON COLUMN note_tag.tag_id IS '标签ID';
COMMENT ON COLUMN note_tag.created_at IS '创建时间';
COMMENT ON COLUMN note_tag.deleted IS '逻辑删除标记';

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
);

COMMENT ON TABLE note_attachment IS '笔记附件表';
COMMENT ON COLUMN note_attachment.id IS '主键ID';
COMMENT ON COLUMN note_attachment.note_id IS '所属笔记ID';
COMMENT ON COLUMN note_attachment.file_name IS '文件名';
COMMENT ON COLUMN note_attachment.file_type IS '文件类型';
COMMENT ON COLUMN note_attachment.file_size IS '文件大小（字节）';
COMMENT ON COLUMN note_attachment.file_url IS '文件访问地址';
COMMENT ON COLUMN note_attachment.storage_key IS '存储服务对象键';
COMMENT ON COLUMN note_attachment.created_at IS '创建时间';
COMMENT ON COLUMN note_attachment.updated_at IS '更新时间';
COMMENT ON COLUMN note_attachment.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE ai_chat_session IS 'AI对话会话表';
COMMENT ON COLUMN ai_chat_session.id IS '主键ID';
COMMENT ON COLUMN ai_chat_session.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN ai_chat_session.user_id IS '会话所属用户ID';
COMMENT ON COLUMN ai_chat_session.session_type IS '会话类型：问答、总结、改写等';
COMMENT ON COLUMN ai_chat_session.title IS '会话标题';
COMMENT ON COLUMN ai_chat_session.latest_message_at IS '最近消息时间';
COMMENT ON COLUMN ai_chat_session.created_at IS '创建时间';
COMMENT ON COLUMN ai_chat_session.updated_at IS '更新时间';
COMMENT ON COLUMN ai_chat_session.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE ai_chat_message IS 'AI对话消息表';
COMMENT ON COLUMN ai_chat_message.id IS '主键ID';
COMMENT ON COLUMN ai_chat_message.session_id IS '所属会话ID';
COMMENT ON COLUMN ai_chat_message.role IS '消息角色：user、assistant、system';
COMMENT ON COLUMN ai_chat_message.content IS '消息内容';
COMMENT ON COLUMN ai_chat_message.token_usage IS '消耗的token数量';
COMMENT ON COLUMN ai_chat_message.model_name IS '模型名称';
COMMENT ON COLUMN ai_chat_message.referenced_note_ids IS '引用的笔记ID列表，逗号分隔';
COMMENT ON COLUMN ai_chat_message.created_at IS '创建时间';
COMMENT ON COLUMN ai_chat_message.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE todo_item IS '待办事项表';
COMMENT ON COLUMN todo_item.id IS '主键ID';
COMMENT ON COLUMN todo_item.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN todo_item.note_id IS '关联笔记ID';
COMMENT ON COLUMN todo_item.title IS '待办标题';
COMMENT ON COLUMN todo_item.description IS '待办描述';
COMMENT ON COLUMN todo_item.priority IS '优先级：1低，2中，3高';
COMMENT ON COLUMN todo_item.status IS '状态：0未开始，1进行中，2已完成，3已取消';
COMMENT ON COLUMN todo_item.due_at IS '截止时间';
COMMENT ON COLUMN todo_item.completed_at IS '完成时间';
COMMENT ON COLUMN todo_item.created_by IS '创建人ID';
COMMENT ON COLUMN todo_item.created_at IS '创建时间';
COMMENT ON COLUMN todo_item.updated_at IS '更新时间';
COMMENT ON COLUMN todo_item.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE reminder IS '提醒表';
COMMENT ON COLUMN reminder.id IS '主键ID';
COMMENT ON COLUMN reminder.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN reminder.todo_id IS '关联待办ID';
COMMENT ON COLUMN reminder.reminder_type IS '提醒类型：一次性、重复、到期前提醒等';
COMMENT ON COLUMN reminder.trigger_at IS '触发时间';
COMMENT ON COLUMN reminder.message IS '提醒消息内容';
COMMENT ON COLUMN reminder.status IS '状态：0待发送，1已发送，2失败';
COMMENT ON COLUMN reminder.sent_at IS '实际发送时间';
COMMENT ON COLUMN reminder.created_at IS '创建时间';
COMMENT ON COLUMN reminder.updated_at IS '更新时间';
COMMENT ON COLUMN reminder.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE search_index_task IS '搜索索引任务表';
COMMENT ON COLUMN search_index_task.id IS '主键ID';
COMMENT ON COLUMN search_index_task.note_id IS '所属笔记ID';
COMMENT ON COLUMN search_index_task.task_type IS '任务类型';
COMMENT ON COLUMN search_index_task.status IS '状态：0待处理，1处理中，2成功，3失败';
COMMENT ON COLUMN search_index_task.retry_count IS '重试次数';
COMMENT ON COLUMN search_index_task.error_message IS '错误信息';
COMMENT ON COLUMN search_index_task.created_at IS '创建时间';
COMMENT ON COLUMN search_index_task.updated_at IS '更新时间';
COMMENT ON COLUMN search_index_task.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE ai_task IS 'AI任务表';
COMMENT ON COLUMN ai_task.id IS '主键ID';
COMMENT ON COLUMN ai_task.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN ai_task.user_id IS '发起任务的用户ID';
COMMENT ON COLUMN ai_task.task_type IS 'AI任务类型';
COMMENT ON COLUMN ai_task.target_id IS '任务目标ID';
COMMENT ON COLUMN ai_task.status IS '状态：0待处理，1处理中，2成功，3失败';
COMMENT ON COLUMN ai_task.input_content IS '输入内容';
COMMENT ON COLUMN ai_task.output_content IS '输出内容';
COMMENT ON COLUMN ai_task.retry_count IS '重试次数';
COMMENT ON COLUMN ai_task.error_message IS '错误信息';
COMMENT ON COLUMN ai_task.created_at IS '创建时间';
COMMENT ON COLUMN ai_task.updated_at IS '更新时间';
COMMENT ON COLUMN ai_task.deleted IS '逻辑删除标记';

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

COMMENT ON TABLE audit_log IS '审计日志表';
COMMENT ON COLUMN audit_log.id IS '主键ID';
COMMENT ON COLUMN audit_log.workspace_id IS '所属工作区ID';
COMMENT ON COLUMN audit_log.user_id IS '操作用户ID';
COMMENT ON COLUMN audit_log.action_type IS '操作类型';
COMMENT ON COLUMN audit_log.resource_type IS '资源类型';
COMMENT ON COLUMN audit_log.resource_id IS '资源ID';
COMMENT ON COLUMN audit_log.detail IS '操作详情';
COMMENT ON COLUMN audit_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN audit_log.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS user_setting (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_user_setting UNIQUE (user_id, setting_key)
);

COMMENT ON TABLE user_setting IS '用户设置表';
COMMENT ON COLUMN user_setting.id IS '主键ID';
COMMENT ON COLUMN user_setting.user_id IS '用户ID';
COMMENT ON COLUMN user_setting.setting_key IS '设置键';
COMMENT ON COLUMN user_setting.setting_value IS '设置值';
COMMENT ON COLUMN user_setting.updated_at IS '更新时间';
COMMENT ON COLUMN user_setting.deleted IS '逻辑删除标记';

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
