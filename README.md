# Noto · 知微

> 面向知识管理与智能辅助的个人/团队笔记平台。

Noto · 知微帮助用户记录、整理、检索知识，并结合 **LangChain4j + 通义千问** 的 AI 能力与待办提醒，构建轻量化的知识管理与工作协同工具。

**当前阶段：MVP 可用 · 阶段八已收尾** — 核心闭环已打通，具备演示与迭代基础。

---

## 项目进度总览

| 维度 | 进度 | 说明 |
|------|------|------|
| MVP 核心闭环 | ~95% | 阶段八收尾：pgvector、搜索定位、提醒闭环、开发脚本 |
| AI 能力深度 | ~55% | 阶段一～四已完成；**下一阶段：体验深化（见路线图 E/F/G）** |
| 工程化 / 上线 | ~80% | Docker 演示栈、CI、pgvector、集成测试 + 演示数据种子 |

### 模块完成度

| 模块 | 状态 | 已实现 | 待完善 |
|------|------|--------|--------|
| 用户 | ✅ | 注册、登录、JWT、个人资料 | 生产环境邮件重置密码（可选） |
| 笔记 | ✅ | 知识库、文档树、子文档、拖拽排序、Markdown、自动保存、收藏/归档、**图片上传（MinIO）**、**附件管理面板** | — |
| 搜索 | ✅ | PostgreSQL 关键字检索、高亮、分组/标签筛选、跳转 | ES 异步索引（可选） |
| 待办 | ✅ | 行动看板、状态流转、近期行动/长期目标、笔记联动、**关联文档选择器** | — |
| 提醒 | ✅ | 到期轮询 + 系统通知、逾期每日提醒、周期总结就绪通知、复盘闭环 | 邮件/短信推送 |
| AI | ✅ | 摘要、统一 AI 助手（问答+办事）、RAG、智能体、每日 digest | **阶段五～七**：Cmd+K、主流程嵌入、跨文档合成等 |
| 首页 | ✅ | 四步工作流看板、**拖拽改状态**、AI 拖文档提取待办 | — |

> 说明：`任务清单.csv`、`总索引文档.md` 部分条目已滞后，**以本 README 与代码为准**。

---

## 核心功能

| 模块 | 能力 |
|------|------|
| 用户 | 注册、登录、个人资料 |
| 笔记 | 知识库首页、分组目录、文档树（含子文档、拖拽移动）、收藏、归档、自动保存 |
| 搜索 | 关键字搜索、分组/标签筛选、高亮片段、跳转定位 |
| 待办 | **行动看板**（待办队列 / 进行中）、状态流转、与笔记联动、AI/规则提取 |
| 提醒 | 到期提醒、前端轮询通知 |
| AI | 文档摘要、知识库/单篇问答（SSE 流式）、智能待办提取（审查后确认） |
| 首页 | **今日行动看板**：文档 → 待办 → 进行中 → 完成，支持跨列拖拽 |

---

## 技术栈

### 后端

- Java 17、Spring Boot 3
- PostgreSQL、MyBatis-Plus
- Spring Security + JWT
- **LangChain4j** + `langchain4j-community-dashscope`（通义千问）
- Spring Boot DevTools 热重载

### 前端

- Vue 3 + TypeScript
- Ant Design Vue、md-editor-v3
- Vite

### 规划中（表/文档已设计，代码未接入）

- Elasticsearch / 向量检索
- Redis（会话缓存）、RabbitMQ（异步任务）
- ~~MinIO（附件）~~ → **MinIO 图片上传已接入**（`noto.minio.enabled=true`）

---

## 仓库结构

```text
Noto_知微/
├── backend/          # Spring Boot 后端
├── frontend/         # Vue 3 Web 桌面 + 移动 Web
├── mobile/           # uni-app 移动客户端
├── README.md         # 项目说明 + 进度 + AI 路线图（本文档）
└── 各类设计文档（PRD、API、数据库等）
```

---

## 快速启动

> **命令速查（开发 / 演示 / 生产 / APK）→ [docs/COMMANDS.md](docs/COMMANDS.md)**  
> 部署细节 → [docs/DEV_DEPLOY.md](docs/DEV_DEPLOY.md)

### 日常开发（三步）

```powershell
.\scripts\dev-up.ps1              # Docker：PostgreSQL + MinIO
cd backend; .\dev.ps1 -WatchCompile   # 后端 → :9086
cd ..\frontend; npm run dev       # 前端 → :5173
```

浏览器：**http://localhost:5173** · 默认账号 `admin` / `admin123`

### 其他场景

| 场景 | 命令 |
|------|------|
| Docker 演示 | `.\scripts\demo-up.ps1` → http://localhost:8080 |
| 生产上线 | Linux：`./deploy/prod-up.sh`（见 [deploy/README.md](deploy/README.md)） |
| Android App | `cd mobile && npm run dev:h5` · 打包见 [docs/UNIAPP.md](docs/UNIAPP.md) |

### 环境要求

- **后端**：JDK 17、Maven、Node.js 18+
- **移动 App**：Node.js 18+、uni-app（`mobile/`）→ 见 [docs/UNIAPP.md](docs/UNIAPP.md)

### 启用 AI（可选）

需配置通义千问 DashScope API Key：

**方式 A：环境变量（推荐）**

```powershell
$env:NOTO_AI_ENABLED = "true"
$env:AI_DASHSCOPE_API_KEY = "你的百炼/通义 API Key"
```

**方式 B：配置文件**

在 `application-dev.yml` 中：

```yaml
noto:
  ai:
    enabled: true
    model: qwen-turbo   # 或 qwen-plus
    embedding-model: text-embedding-v3
    rag-enabled: true
    rag-chunk-size: 600
    api-key: 你的密钥
```

**向量检索（B1）**：文档保存后自动分块并写入 embedding 索引；历史文档可调用 `POST /api/v1/ai/rag/reindex` 批量重建，或在编辑页 AI 侧栏点「重建本篇索引」。

重启后端后，访问 `GET /api/v1/ai/status` 确认 `enabled: true`。

---

## Docker 演示 / 生产

> 命令速查 → **[docs/COMMANDS.md](docs/COMMANDS.md)** ② 演示、③ 生产

```powershell
copy .env.example .env    # 首次：编辑 JWT、AI Key 等
.\scripts\demo-up.ps1     # 演示 → http://localhost:8080
.\scripts\demo-down.ps1   # 停止
```

云服务器上线 → [deploy/README.md](deploy/README.md)

<details>
<summary>展开：手动 docker compose 与端口说明</summary>

### 准备环境

- 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（含 Docker Compose）

### 配置环境变量

```powershell
cd e:\Noto
copy .env.example .env
# 编辑 .env：至少修改 NOTO_JWT_SECRET；启用 AI 时设置 NOTO_AI_ENABLED 与 AI_DASHSCOPE_API_KEY
```

### 启动

```powershell
# 或手动
docker compose up -d --build
```

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8080 |
| 后端 API | http://localhost:9086/api/v1/health |
| PostgreSQL | localhost:5432 |
| MinIO API | http://localhost:9000 |
| MinIO 控制台 | http://localhost:9001（默认 minioadmin / minioadmin） |

Docker 环境默认启用 MinIO（`NOTO_MINIO_ENABLED=true`），笔记编辑器可直接上传图片。

### 演示账号与预置数据

首次启动（`NOTO_DEMO_ENABLED=true`，Docker 默认开启）会自动写入 **v3 演示数据**（仅首次）；待办截止日期会在**每次后端启动**时按当前时间刷新。

| 项 | 内容 |
|----|------|
| 账号 | `admin` / `admin123` |
| 分组 | 6 个（工作 / 项目 / 学习 / 会议 / 灵感 / 参考） |
| 文档 | **74 篇**（周会、日志、PRD、客户、剪藏等） |
| 待办 | 20 条 |
| 提醒 | 2 条 |

关闭演示数据：在 `.env` 中设置 `NOTO_DEMO_ENABLED=false` 后重建 backend 容器。

本地 IDE 开发若需预置数据：`$env:NOTO_DEMO_ENABLED="true"` 后启动后端（需已有 admin 账号）。

**端口冲突**：若 9086 已被本地后端占用，运行 `.\scripts\demo-up.ps1` 会自动停止本地进程；保留本地后端时用 `.\scripts\demo-up.ps1 -InfraOnly` 或 `.\scripts\dev-up.ps1`（仅启 db/minio）。

</details>

MinIO 与数据库由 `dev-up.ps1` / `demo-up.ps1` 一并启动，无需单独脚本。更多命令 → [docs/COMMANDS.md](docs/COMMANDS.md)。

---

## 附件接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/attachments/upload` | 上传附件（`multipart`: `file`, `noteId`） |
| GET | `/api/v1/notes/{noteId}/attachments` | 笔记附件列表 |
| DELETE | `/api/v1/attachments/{id}` | 删除附件 |
| GET | `/api/v1/attachments/{id}/file?access=...` | 读取文件（签名 URL，Markdown 预览可用） |

笔记编辑器工具栏 **图片** 按钮：需先保存文档（有 `noteId`），上传后自动插入 Markdown 图片链接。

---

## AI 接口（当前）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/ai/status` | AI 状态 |
| POST | `/api/v1/ai/notes/{id}/summarize` | 生成摘要（可选 body 传草稿；`persist=false` 仅预览） |
| POST | `/api/v1/ai/notes/{id}/transform` | 润色/条文化/结构化/模板（见 mode） |
| POST | `/api/v1/ai/notes/{id}/transform-selection/stream` | 选中段落流式改写（`concise`/`expand`/`formal`） |
| POST | `/api/v1/ai/notes/{id}/extract-todos` | AI 提取待办预览（不入库） |
| POST | `/api/v1/ai/notes/{id}/extract-todos/confirm` | 确认审查后的待办并入库 |
| GET | `/api/v1/ai/digest/today` | 获取今日 AI digest |
| POST | `/api/v1/ai/digest/generate` | 手动生成今日 digest（可选 `workspaceId`） |
| POST | `/api/v1/ai/route` | 意图路由：判断走问答（chat）还是办事（agent） |
| POST | `/api/v1/ai/ask` | 非流式问答 |
| GET | `/api/v1/ai/ask/stream` | SSE 流式问答（`question`, `workspaceId`, `scope`, `targetId`, `sessionId`） |
| GET | `/api/v1/ai/sessions` | 会话列表（`workspaceId`） |
| POST | `/api/v1/ai/sessions` | 创建会话 |
| GET | `/api/v1/ai/sessions/{id}/messages` | 会话消息 |
| DELETE | `/api/v1/ai/sessions/{id}` | 删除会话 |
| GET | `/api/v1/ai/todos/daily-suggestions` | 今日行动建议（可选 `workspaceId`） |
| GET | `/api/v1/ai/todos/daily-review` | 今日完成复盘（可选 `workspaceId`） |
| POST | `/api/v1/ai/todos/breakdown` | 待办拆解为子任务建议 |
| GET | `/api/v1/ai/todos/overdue-advice` | 逾期待办 AI 处理建议 |
| GET | `/api/v1/ai/rag/status` | 向量索引状态（可选 `workspaceId`） |
| POST | `/api/v1/ai/rag/reindex` | 重建知识库向量索引 |
| POST | `/api/v1/ai/rag/notes/{id}/reindex` | 重建单篇文档向量索引 |
| GET | `/api/v1/settings/ai` | 获取用户 AI 偏好 |
| PATCH | `/api/v1/settings/ai` | 更新用户 AI 偏好 |
| POST | `/api/v1/ai/agent/plan` | 智能体规划工作流（预览，写操作需确认） |
| GET | `/api/v1/ai/agent/tasks` | 智能体任务列表 |
| GET | `/api/v1/ai/agent/tasks/{id}` | 智能体任务详情 |
| POST | `/api/v1/ai/agent/tasks/{id}/confirm` | 确认并执行待确认步骤 |
| POST | `/api/v1/notes/{id}/extract-todos` | Markdown 规则提取预览（不入库） |
| POST | `/api/v1/notes/{id}/extract-todos/confirm` | 确认规则提取的待办并入库 |
| GET | `/api/v1/notes/{id}/related` | 相关文档推荐（规则：同标签/分组/标题） |

**前端入口**

- 侧栏 **AI 助手**（`/ai`）— 统一对话框，后端 LLM 自动识别问答或办事（失败时规则兜底）；问答流式输出、引用可跳转；办事在对话内展示方案并确认执行
- 旧链接 `/ai/agent` 自动跳转到 `/ai?tab=agent`
- 笔记编辑页 **AI 助手**（悬浮抽屉）— 摘要、润色、条文化、提取待办、单篇问答、相关文档
- 笔记右键 — **AI 摘要**、**AI/规则提取待办**
- 首页看板 — 文档拖到「待办队列」默认 **AI 提取**（无结果时创建跟进任务）

**当前 AI 实现特点与限制**

| 能力 | 实现方式 | 限制 |
|------|----------|------|
| 单篇问答 | RAG 向量召回 + 全文上下文 | 超长文档仍截断 8000 字 |
| 知识库问答 | 向量检索 Top-K + keyword 混合召回 | 大库性能依赖内存余弦相似度 |
| 提取待办 | Prompt → JSON → 审查确认 | 与看板已打通 |
| 摘要 | Prompt → JSON；面板预览后写入；可选保存后自动生成 | 长文仍截断 8000 字 |
| 写作助手 | 结构化/模板/选中改写（流式） | 选中替换依赖原文精确匹配 |

---

## AI 能力路线图

> 定位：**知识 + 行动** 的 AI 助手，而非通用聊天机器人。  
> 原则：**所有写操作预览 + 用户确认**；AI 与业务解耦；结果可回链原文。

### 阶段一：增强现有能力（短期，1–2 周）

目标：在不改架构的前提下，让已有 AI 更好用。

| 编号 | 能力 | 说明 | 状态 |
|------|------|------|------|
| A1 | 编辑页 AI 面板 | 摘要 / 润色 / 条文化 / 提取待办 / 单篇问答 / 相关文档，统一侧栏入口 | ✅ 已完成 |
| A2 | 摘要自动化 | 保存后可选自动摘要（摘要为空时）；面板开关 | ✅ 已完成 |
| A3 | 引用跳转增强 | AI 问答引用点击带 `q` 参数跳转并高亮 | ✅ 已完成 |
| A4 | 会话持久化 | 接入 `ai_chat_session` / `ai_chat_message` 表 | ✅ 已完成 |
| A5 | 相似文档（规则版） | 同知识库 + 标签/分组/标题推荐相关笔记 | ✅ 已完成 |

**交付标准**：笔记编辑页可一站式调用 AI；AI 问答页可查看历史会话。✅ 阶段一已完成。

---

### 阶段二：检索增强与行动智能（中期，2–4 周）

目标：问答「懂库」；AI 从「提取待办」延伸到「规划行动」。

| 编号 | 能力 | 说明 | 依赖 |
|------|------|------|------|
| B1 | **RAG 向量检索** | 笔记分块 + DashScope embedding；问答向量召回 + keyword 混合 | ✅ 已完成 |
| B2 | 段落级引用 | 返回答案 + `noteId` + 偏移，编辑器高亮定位 | ✅ 已完成（offset + 滚动） |
| B3 | 待办拆解 | 「完成 XX 项目」→ 子任务 + 优先级/截止建议 | ✅ 已完成 |
| B4 | 今日行动建议 | 结合队列/逾期/进行中，推荐「今天先做哪 3 件」 | ✅ 已完成 |
| B5 | 完成复盘 | 基于今日完成生成日复盘 / 周报草稿 | ✅ 已完成 |
| B6 | AI 调用审计 | 记录 Prompt、模型、耗时、结果至 `audit_log` | ✅ 已完成 |

**交付标准**：知识库问答能准确引用相关文档片段；看板侧有一键「今日建议」。✅ 阶段二已完成。

---

### 阶段三：写作助手与模板（中期，可与阶段二并行）

目标：降低「写文档」成本，为提取待办提供更规范输入。

| 编号 | 能力 | 场景 | 状态 |
|------|------|------|------|
| C1 | 结构化整理 | 会议纪要 → 结论 / 决策 / 待办 / 风险 | ✅ 已完成 |
| C2 | 模板生成 | 周报、项目复盘、方案初稿（按模板填空） | ✅ 已完成 |
| C3 | 选中润色 | 选中段落：更简洁、扩写、改语气（流式写回） | ✅ 已完成 |
| C4 | 知识缺口提示 | 问答资料不足时，建议补充哪类笔记 | ✅ 已完成 |

**交付标准**：编辑页可一键结构化/套模板；选中段落流式改写；问答不足时给出补充建议。✅ 阶段三已完成。

---

### 阶段四：可确认智能体（长期，4+ 周）

目标：自然语言驱动多步工作流，**每步可预览、可取消**。

```
用户：「把上周会议纪要整理成待办并设提醒」
  → 搜索文档 → 摘要 → 提取待办 → 创建提醒
  → 逐步预览，用户确认后执行
  → 写入 ai_task / audit_log
```

| 编号 | 能力 | 说明 |
|------|------|------|
| D1 | Tool Calling 编排 | `searchNotes`、`summarize`、`extractTodos`、`createTodo`、`createReminder` 工具链 + 预览确认 | ✅ 已完成 |
| D2 | 智能体任务页 | 展示拆解步骤、执行状态、逐步/批量确认 | ✅ 已完成 |
| D3 | 定时 AI 任务 | 每日行动建议 digest（Spring 定时 + 用户偏好）；18 点后手动/定时生成含复盘 | ✅ MVP 已完成 |
| D4 | 用户 AI 偏好 | `user_setting`：回答风格（已接入问答 Prompt）、默认 scope、是否自动摘要 | ✅ 已完成 |

**交付标准**：用户用一句话完成「找文档 → 提取 → 入库 → 设提醒」闭环，全程可审查。

---

### 路线图总览

```text
阶段一（增强现有）     阶段二（RAG + 行动）     阶段三（写作）     阶段四（智能体）     阶段五～七（体验深化）
─────────────────     ───────────────────     ─────────────     ───────────────     ─────────────────────
编辑页 AI 面板    →    向量检索问答       →    模板/润色      →    Tool 编排      →    Cmd+K / 主流程嵌入
会话持久化        →    段落引用跳转       →    结构化整理     →    自然语言工作流  →    跨文档合成 / 自动化
相似文档推荐      →    今日建议/复盘      →                    →    审计与偏好      →    轻量记忆 / 提醒闭环
```

### 明确不做（当前阶段）

- 复杂多 Agent 自主协作（维护成本高，与单人 MVP 不匹配）
- 无确认的自动写库（违背「结果可信」原则）
- 通用闲聊机器人（偏离产品定位）
- 图片/PPT 生成等与核心闭环无关的能力

---

## AI 体验深化路线图（后续推进）

> **背景**：阶段一～四能力已齐备，但 AI 仍容易显得「鸡肋」——能力多、入口散，与通用聊天工具同质化，且未充分钉进「记录 → 行动」主路径。  
> **目标**：让 AI 从「侧边栏功能模块」变为 **行动看板的发动机**，默认产出可执行结果（待办、提醒、结构化笔记、复盘草稿）。

### 优化原则

| 原则 | 说明 |
|------|------|
| 场景优先 | 先问用户在哪个界面、要完成什么，再调 AI，而非先打开 AI 页 |
| 默认可执行 | 待办、提醒、结构化笔记、周报草稿，比长段解释更有价值 |
| 上下文即壁垒 | 个人笔记、待办、逾期、知识库 RAG 是通用大模型没有的 |
| 渐进信任 | 简单写操作可一键执行；复杂多步链路仍预览 + 确认 |

### 阶段五 · 短期（1–2 周）— 长在主流程上

| 编号 | 能力 | 说明 | 状态 |
|------|------|------|------|
| E1 | 全局指令栏 `Cmd+K` | 顶栏/快捷键：搜文档、问知识库、一句话办事（待办/提醒）、跳转 | ✅ 已完成 |
| E2 | 编辑页「写完推一步」 | 保存或停笔后轻提示：提取待办 / 生成摘要（可关闭） | ✅ 已完成 |
| E3 | 选中浮动 AI | 选中段落 → 润色 / 扩写 / 转成待办，替代整页抽屉操作 | ✅ 已完成 |
| E4 | 办事信任模式 | 用户设置：单步简单指令（仅 1 个待办或提醒）自动执行，跳二次确认 | ✅ 已完成 |
| E5 | Digest 可点就做 | 首页今日建议每条带「开始 / 完成 / 推迟」，与看板联动 | ✅ 已完成 |

**交付标准**：用户无需专门打开 AI 页，在写作与看板主路径上即可完成常见 AI 操作。

### 阶段六 · 中期（2–4 周）— 知识 → 行动差异化

| 编号 | 能力 | 说明 | 状态 |
|------|------|------|------|
| F1 | 跨文档合成 | 按主题/时间范围聚合多篇笔记 → 决策记录、项目现状、周报草稿（输出为新笔记） | ✅ 已完成 |
| F2 | 待办智能补全 | 模糊待办自动拆解子任务（复用 breakdown）；逾期待办建议改期/拆小/归档 | ✅ 已完成 |
| F3 | 提醒 + AI 闭环 | 提醒触发附带笔记上下文；完成后可选「写一句复盘进原笔记」 | ✅ 已完成 |
| F4 | RAG 体验升级 | 长文分块减少 8000 字截断；引用排序优化；缺口提示引导补笔记 | ✅ 已完成 |

**交付标准**：用户能感知「只有在我的知识库里才做得到」的 AI 能力，而非可复制到 ChatGPT 的问答。

### 阶段七 · 长期 — 从工具到协作者

| 编号 | 能力 | 说明 | 状态 |
|------|------|------|------|
| G1 | 轻量记忆 | 记住主攻项目、常用知识库、办事偏好（非无限闲聊记忆） | ✅ 已完成 |
| G2 | 自动化规则 | 每周五生成复盘笔记；会议笔记保存后自动提取待办预览（仍须确认） | ✅ 已完成 |
| G3 | 对外扩展（可选） | 剪藏/导入 → 结构化进库；待办导出 ICS、复盘邮件等 | ✅ 已落地 |

**交付标准**：AI 能按用户节奏主动参与，而不是被动等待提问。

---

### 阶段八 · 稳定与体验 ✅

> **目标**：从「功能齐备」到「敢演示、敢迭代」——测试保障、 onboarding、性能与体验收尾。

| 编号 | 能力 | 说明 | 状态 |
|------|------|------|------|
| H1 | E2E 自测清单 | 15 分钟演示/回归脚本 | ✅ 已完成 |
| H2 | 关键集成测试 | 健康检查、登录、笔记→提取待办→看板（Testcontainers） | ✅ 已完成 |
| H3 | 新用户引导 | 注册后欢迎引导、待办空状态、引导可关闭 | ✅ 已完成 |
| H4 | RAG 规模化 | pgvector HNSW 向量检索 + 历史 JSON 向量回填；无 pgvector 时内存 fallback | ✅ 已完成 |
| H5 | 提醒深化 | 待办到期 + 逾期每日提醒 + 周期总结就绪通知 + 复盘闭环 | ✅ 已完成 |
| H6 | 搜索体验 | 搜索结果 / Cmd+K 跳转带 offset 正文定位（CodeMirror 滚动） | ✅ 已完成 |
| H7 | 文档同步 | README / 任务清单与代码对齐 | ✅ 已完成 |

**交付标准**：CI 跑通核心集成测试；新用户 5 分钟内完成第一篇文档 + 第一个待办。

| 优先级 | 事项 | 状态 |
|--------|------|------|
| **P0** | H1 核心流程 E2E 自测清单 | ✅ `docs/E2E_CHECKLIST.md` |
| **P0** | H2 关键 API 集成测试 | ✅ Testcontainers + PostgreSQL |
| **P0** | H3 新用户 Onboarding | ✅ 注册引导 + 空状态 + 可关闭 |
| **P1** | H4 RAG 性能（pgvector） | ✅ pgvector + HNSW；plain Postgres 自动 fallback |
| **P1** | H5 提醒推送与闭环深化 | ✅ 已完成 |
| **P2** | H6 搜索体验收尾 | ✅ 已完成 |

### 产品表述调整（对外）

- 少强调：「AI 问答、摘要、润色…」功能清单
- 多强调：**「写完就能变待办，到期就会推你，周末能自动复盘」**

---

## 知识库与文档树

每个知识库（workspace）具备：

| 能力 | 说明 |
|------|------|
| **首页** | 自动创建「首页」文档；进入知识库默认打开首页 |
| **分组目录** | 点击树中分组 → 右侧浏览子分组与文档列表 |
| **子文档** | 右键文档 →「新建子文档」；树中嵌套展示 |
| **树拖拽** | 文档/分组可在树内拖拽移动排序 |
| **按选中新建** | 选中分组/文档后点「+ 新建」，在该层级下创建 |

数据模型：`workspace.home_note_id` 指向首页；`note.parent_id` 表示子文档；`note_folder.parent_id` 表示嵌套分组。

---

## 待办与行动看板

待办分为两类时间视野（`horizon`）：

| 值 | 含义 |
|----|------|
| `action` | **近期行动**：默认进入「待办队列」 |
| `long_term` | **长期目标**：按状态归入队列/进行中 |

状态与看板分区：

| 状态 | 看板位置 |
|------|----------|
| 待开始 (`0`) | **待办队列** |
| 进行中 (`1`) | **进行中**（可多线并行） |
| 已完成 (`2`) | 计入今日完成 |

**闭环**：文档 → 提取（审查）→ 排队 → 开做 → 完成。

**首页拖拽规则**

| 从 | 拖到 | 效果 |
|----|------|------|
| 知识输入（文档） | 待办队列 | AI 提取待办（审查）；无结果则创建「跟进：标题」 |
| 待办队列 | 进行中 | 状态 → 进行中 |
| 进行中 | 待办队列 | 状态 → 待开始 |
| 进行中 | 今日完成 | 状态 → 已完成 |

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/todos/board` | 行动看板数据 |
| GET | `/api/v1/todos` | 分页列表，可选 `horizon` 筛选 |

**前端入口**

- **待办**（`/todos?view=board`）
- **首页** — 四步工作流 + 拖拽
- 笔记关联条 — 快捷跳转看板

---

## 开发说明

- 后端热重载：修改 Java 后自动编译到 `target/classes` 即可触发 DevTools 重启
- AI 未启用时，后端仍可正常启动；调用 AI 接口会返回友好提示
- 无 AI 时使用 **规则提取**（`- [ ]` 复选框与「待办：」行），同样需审查确认
- 开发环境首次启动会自动执行 schema 迁移（如 `horizon` 列）

---

## 文档索引

建议阅读顺序：

1. `README.md`（本文 — 进度与 AI 路线图）
2. `docs/DEV_DEPLOY.md`（**开发与 Docker 部署步骤**）
3. `docs/MULTI_PLATFORM.md`（**多端路线图与移动 Web MVP**）
4. `docs/WEB_DESKTOP_POLISH.md`（**Web 桌面完善路线图**）
5. `docs/E2E_CHECKLIST.md`（核心流程自测清单）
6. `总索引文档.md`
7. `PRD_正式版.txt`
8. `API请求响应设计.md`
9. `数据库表设计.md`
10. `技术架构设计.txt`

---

## 后续计划（非 AI）

| 优先级 | 事项 | 状态 |
|--------|------|------|
| 高 | 检索体验优化（高亮、定位） | ✅ 已完成 |
| 高 | 附件 / 图片上传（MinIO） | ✅ 编辑器图片上传已接入 |
| 中 | 日志与审计（`audit_log`） | ✅ AI 调用已接入 |
| 中 | Docker 部署与演示数据 | ✅ Compose 已提供 |
| 中 | 多端路线图 | ✅ `docs/MULTI_PLATFORM.md` |
| 中 | Web 桌面完善 | ✅ `docs/WEB_DESKTOP_POLISH.md` 阶段九 A 已完成 |
| 高 | 核心集成测试 | ✅ Testcontainers（见 `backend/src/test`） |
| 高 | E2E 自测清单 | ✅ `docs/E2E_CHECKLIST.md` |
| 低 | 每日/周总结类提醒 | ✅ 页内 + 系统通知（见 H5） |

AI 相关规划见 [AI 能力路线图](#ai-能力路线图) 与 [AI 体验深化路线图（后续推进）](#ai-体验深化路线图后续推进)。阶段八见 [阶段八 · 稳定与体验](#阶段八--稳定与体验当前推进)。
