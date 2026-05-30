# Noto · 知微 API 请求与响应设计

## 1. 设计目标
本文档定义 Noto · 知微 MVP 阶段的后端 API 请求与响应规范，目标是：
- 为 Web 端提供稳定统一的接口
- 为未来 App、小程序、桌面端复用同一套接口
- 统一请求格式、响应格式、错误格式
- 支持笔记、搜索、AI、待办、提醒等核心能力

---

## 2. 接口设计原则
1. REST 风格为主
2. 统一前缀 `/api/v1`
3. 统一响应结构
4. 统一错误码与错误信息
5. 统一鉴权方式
6. AI 流式输出单独支持 SSE 或 WebSocket
7. 所有接口都应支持 traceId 追踪

---

## 3. 通用请求规范

### 3.1 请求头
- `Authorization: Bearer <token>`
- `Content-Type: application/json`
- `X-Trace-Id: <traceId>`

### 3.2 通用请求参数约定
- 分页参数统一使用 `page` 和 `pageSize`
- 排序参数统一使用 `sortBy` 和 `sortOrder`
- 时间字段统一使用 ISO 8601 格式或标准时间戳，项目内保持一致

---

## 4. 通用响应结构

### 4.1 成功响应
```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "abc123"
}
```

### 4.2 失败响应
```json
{
  "code": 40001,
  "message": "参数错误",
  "data": null,
  "traceId": "abc123"
}
```

### 4.3 响应字段说明
- `code`：业务状态码
- `message`：提示信息
- `data`：业务数据
- `traceId`：链路追踪 ID

---

## 5. 统一错误码建议
- `0`：成功
- `40001`：参数错误
- `40002`：未登录
- `40003`：无权限
- `40004`：资源不存在
- `40005`：请求频率过高
- `50001`：服务器内部错误
- `50002`：AI 服务调用失败
- `50003`：外部依赖失败

---

## 6. 认证接口

### 6.1 用户注册
**POST** `/api/v1/auth/register`

#### 请求体
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "12345678"
}
```

#### 响应体
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1
  },
  "traceId": "abc123"
}
```

### 6.2 用户登录
**POST** `/api/v1/auth/login`

#### 请求体
```json
{
  "account": "test@example.com",
  "password": "12345678"
}
```

#### 响应体
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "jwt-token",
    "refreshToken": "refresh-token",
    "expiresIn": 7200,
    "user": {
      "id": 1,
      "nickname": "Noto User",
      "avatarUrl": ""
    }
  },
  "traceId": "abc123"
}
```

### 6.3 获取当前用户信息
**GET** `/api/v1/auth/me`

### 6.4 退出登录
**POST** `/api/v1/auth/logout`

---

## 7. 用户与工作区接口

### 7.1 获取用户资料
**GET** `/api/v1/users/profile`

### 7.2 更新用户资料
**PUT** `/api/v1/users/profile`

#### 请求体
```json
{
  "nickname": "New Name",
  "avatarUrl": "https://xxx.com/avatar.png"
}
```

### 7.3 获取工作区列表
**GET** `/api/v1/workspaces`

### 7.4 创建工作区
**POST** `/api/v1/workspaces`

#### 请求体
```json
{
  "name": "My Space",
  "description": "Personal workspace"
}
```

---

## 8. 笔记接口

### 8.1 创建笔记
**POST** `/api/v1/notes`

#### 请求体
```json
{
  "workspaceId": 1,
  "folderId": 2,
  "title": "Spring Boot 笔记",
  "content": "# 标题\n内容...",
  "contentType": "markdown",
  "tagIds": [1, 2]
}
```

### 8.2 更新笔记
**PUT** `/api/v1/notes/{id}`

#### 请求体
```json
{
  "folderId": 2,
  "title": "更新后的标题",
  "content": "更新后的内容",
  "contentType": "markdown",
  "isFavorite": true,
  "tagIds": [1, 3]
}
```

### 8.3 获取笔记详情
**GET** `/api/v1/notes/{id}`

### 8.4 删除笔记
**DELETE** `/api/v1/notes/{id}`

### 8.5 笔记列表
**GET** `/api/v1/notes`

#### 查询参数示例
- `workspaceId`
- `folderId`
- `tagId`
- `keyword`
- `page`
- `pageSize`

### 8.6 收藏/取消收藏
**PATCH** `/api/v1/notes/{id}/favorite`

#### 请求体
```json
{
  "isFavorite": true
}
```

---

## 9. 文件夹与标签接口

### 9.1 创建文件夹
**POST** `/api/v1/folders`

### 9.2 文件夹列表
**GET** `/api/v1/folders?workspaceId=1`

### 9.3 更新文件夹
**PUT** `/api/v1/folders/{id}`

### 9.4 删除文件夹
**DELETE** `/api/v1/folders/{id}`

### 9.5 创建标签
**POST** `/api/v1/tags`

### 9.6 标签列表
**GET** `/api/v1/tags?workspaceId=1`

### 9.7 删除标签
**DELETE** `/api/v1/tags/{id}`

---

## 10. 附件接口

### 10.1 上传附件
**POST** `/api/v1/attachments/upload`

#### 请求体
- `multipart/form-data`
- 文件字段：`file`

#### 响应体
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileName": "demo.png",
    "fileUrl": "https://...",
    "storageKey": "note/1/demo.png"
  },
  "traceId": "abc123"
}
```

### 10.2 删除附件
**DELETE** `/api/v1/attachments/{id}`

---

## 11. 搜索接口

### 11.1 全文搜索
**GET** `/api/v1/search`

#### 查询参数
- `workspaceId`
- `keyword`
- `folderId`
- `tagId`
- `page`
- `pageSize`

#### 响应数据结构建议
```json
{
  "items": [
    {
      "noteId": 1,
      "title": "Spring Boot 笔记",
      "snippet": "...命中的片段...",
      "score": 0.98,
      "highlight": "<em>Spring Boot</em>..."
    }
  ],
  "total": 1
}
```

---

## 12. AI 接口

### 12.1 笔记总结
**POST** `/api/v1/ai/notes/{id}/summarize`

#### 响应体
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "summary": "这里是总结",
    "keyPoints": ["重点1", "重点2"],
    "riskPoints": ["风险1"]
  },
  "traceId": "abc123"
}
```

### 12.2 AI 问答
**POST** `/api/v1/ai/ask`

#### 请求体
```json
{
  "workspaceId": 1,
  "question": "这篇文档的核心结论是什么？",
  "scope": "note",
  "targetId": 1001
}
```

#### 响应体
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "核心结论是...",
    "references": [
      {
        "noteId": 1001,
        "noteTitle": "Spring Boot 笔记",
        "snippet": "...引用片段..."
      }
    ]
  },
  "traceId": "abc123"
}
```

### 12.3 待办提取
**POST** `/api/v1/ai/notes/{id}/extract-todos`

#### 响应体
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "todos": [
      {
        "title": "补充接口文档",
        "priority": 2,
        "dueAt": "2026-06-01T18:00:00"
      }
    ]
  },
  "traceId": "abc123"
}
```

### 12.4 相关文档推荐
**GET** `/api/v1/ai/notes/{id}/related`

---

## 13. AI 流式接口

### 13.1 问答流式输出
**GET** `/api/v1/ai/ask/stream`

### 说明
- 可使用 SSE
- 前端实时接收 token 流
- 适用于长回答和更好的交互体验

### 13.2 使用建议
- 非流式接口用于普通查询
- 流式接口用于聊天式问答与长文本总结

---

## 14. 待办接口

### 14.1 创建待办
**POST** `/api/v1/todos`

#### 请求体
```json
{
  "workspaceId": 1,
  "noteId": 1001,
  "title": "补充接口文档",
  "description": "为项目补充完整接口说明",
  "priority": 2,
  "dueAt": "2026-06-01T18:00:00"
}
```

### 14.2 更新待办
**PUT** `/api/v1/todos/{id}`

### 14.3 删除待办
**DELETE** `/api/v1/todos/{id}`

### 14.4 待办列表
**GET** `/api/v1/todos`

### 14.5 更新状态
**PATCH** `/api/v1/todos/{id}/status`

#### 请求体
```json
{
  "status": 2
}
```

---

## 15. 提醒接口

### 15.1 创建提醒
**POST** `/api/v1/reminders`

### 15.2 提醒列表
**GET** `/api/v1/reminders`

### 15.3 更新提醒
**PUT** `/api/v1/reminders/{id}`

### 15.4 删除提醒
**DELETE** `/api/v1/reminders/{id}`

### 15.5 触发提醒记录
**POST** `/api/v1/reminders/{id}/trigger`

---

## 16. 审计日志接口

### 16.1 查询日志列表
**GET** `/api/v1/audit-logs`

#### 查询参数
- `workspaceId`
- `userId`
- `actionType`
- `page`
- `pageSize`

---

## 17. 异步任务接口

### 17.1 查询 AI 任务状态
**GET** `/api/v1/ai-tasks/{id}`

### 17.2 查询索引任务状态
**GET** `/api/v1/search-index-tasks/{id}`

---

## 18. 分页响应建议
```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

---

## 19. 前端对接建议
1. 所有列表接口优先支持分页
2. 所有详情接口返回完整对象
3. AI 接口尽量支持流式和非流式两种形式
4. 所有返回值都应带引用或来源字段
5. 所有异步任务都应返回任务 ID 供轮询

---

## 20. MVP 最小接口集合
MVP 第一版最少需要以下接口：
- 注册/登录/当前用户
- 笔记增删改查
- 文件夹/标签增删查
- 附件上传
- 全文搜索
- AI 总结
- AI 问答
- 待办增删改查
- 提醒增删改查
- 任务状态查询
- 审计日志查询

---

## 21. 结论
本 API 设计以统一、简单、可扩展为原则，满足 Web 端 MVP 快速上线，同时为未来多端复用、AI 流式交互和任务编排预留空间。
