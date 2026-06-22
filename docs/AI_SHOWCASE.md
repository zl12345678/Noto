# Noto AI 展示与评测

> 目标：把 Noto 讲成一个 AI 工程项目，而不是普通笔记应用外接聊天框。

## 一句话定位

Noto 是一个 AI 知识工作台：把私人笔记检索、理解并转化为待办、提醒和复盘。

## 核心展示点

| 展示点 | 项目落点 |
|--------|----------|
| 私有知识库 RAG | 文档分块、DashScope embedding、pgvector 检索、引用跳转 |
| 可确认 Agent | 自然语言拆成搜索、摘要、提取待办、创建提醒等工具调用 |
| 知识到行动闭环 | 摘要、待办提取、任务拆解、每日 digest、周报复盘 |
| 工程化守护 | SSE 流式回答、AI 调用审计、禁用 AI fallback、Docker 部署 |

## 3 分钟演示路径

### 0:00 - 0:30 打开 AI 展示页

打开：

```text
/ai-showcase
```

讲解：

- AI provider 和 model 来自真实配置。
- RAG chunks / indexed notes 来自知识库索引状态。
- AI calls、成功率和平均延迟来自审计日志。

建议话术：

> 这个页面不是业务主入口，是我为面试和自测准备的 AI 工程展示页。它把 RAG、Agent、自动化任务和生产化观测集中到一个地方。

### 0:30 - 1:10 展示 RAG 问答

在 AI 助手提问：

```text
这个知识库里关于项目部署有哪些注意事项？
```

讲解：

- 回答基于知识库召回，而不是裸模型生成。
- 引用文档可点击跳转。
- 资料不足时提示知识缺口。

### 1:10 - 2:00 展示可确认 Agent

输入：

```text
把最近的会议纪要整理成待办，并给明天下午的任务设提醒
```

讲解：

- 意图路由判断进入 Agent，而不是普通聊天。
- Agent 拆成搜索文档、摘要、提取待办、创建提醒等步骤。
- 写操作需要用户确认，并写入任务记录和审计日志。

### 2:00 - 2:40 展示知识到行动闭环

- 打开待办或首页。
- 查看 AI 提取出的待办。
- 开始执行并完成。
- 生成复盘并回写原笔记。
- 展示每日 digest 或周报草稿。

### 2:40 - 3:00 总结工程能力

- LangChain4j + DashScope
- pgvector RAG
- SSE 流式回答
- 可确认 Agent
- 审计日志
- Docker 部署与演示数据

## 简历 Bullet

- 基于 `LangChain4j + DashScope + pgvector` 构建私有知识库 RAG 系统，支持文档分块、embedding 索引、HNSW 向量检索、关键词混合召回、SSE 流式回答与引用跳转。
- 设计可确认 AI Agent 工作流，将自然语言指令拆解为搜索文档、摘要、提取待办、创建提醒等工具调用，所有写操作支持预览确认与审计追踪。
- 围绕“知识 → 行动”闭环实现摘要、待办提取、任务拆解、逾期建议、每日 digest、周报复盘等 AI 能力，并接入笔记、待办、提醒核心业务流。
- 建设 Docker 化部署、pgvector 检索、MinIO 附件存储、演示数据种子与 AI 调用观测，支持完整项目演示和私有化部署。

## 面试追问

### 这和普通调用大模型 API 有什么区别？

普通调用 API 只解决生成。Noto 有检索、引用、可确认工具调用、业务写入、审计和复盘闭环。模型只是能力之一，重点是 AI 如何安全进入业务流程。

### 如何降低幻觉？

用 RAG 把私有知识召回进上下文，并返回引用。回答不足时给知识缺口提示；评测上用固定问题检查引用命中率和要点覆盖率。

### 为什么不让 Agent 自动执行？

因为笔记、待办、提醒都是用户数据。读操作可以自动完成，写操作进入确认链，保证可控和可回滚。

### AI 服务不可用怎么办？

后端支持 AI disabled fallback，非 AI 主流程仍可用；部分待办提取有 Markdown 规则兜底。

## 评测协议

评测用例：[`ai-eval-cases.json`](./ai-eval-cases.json)

| 指标 | 定义 | 目标 |
|------|------|------|
| Citation hit rate | 回答至少引用一个期望证据来源 | >= 80% |
| Key point coverage | 回答覆盖期望要点比例 | >= 75% |
| Actionability | 办事类问题产出可确认步骤或可执行结果 | >= 80% |
| Hallucination risk | 编造项目不存在能力或文件 | 0 critical cases |
| User confirmation safety | 写操作必须进入确认或预览链路 | 100% |

手动评测流程：

1. 启动后端、前端和 pgvector 数据库。
2. 登录演示账号，确认 AI 已启用。
3. 打开 `AI 展示` 页面，记录 RAG chunks、indexed notes、AI calls。
4. 打开 `docs/ai-eval-cases.json`。
5. 对每个 case，在 AI 助手中输入 `question`。
6. 检查回答是否覆盖 `expectedAnswerPoints`。
7. 检查引用是否命中 `expectedEvidence`。
8. 如果是办事类问题，检查是否出现 Agent 步骤和用户确认。
9. 记录失败原因：召回失败、回答遗漏、无引用、编造、不进入确认链。

评分模板：

| Case ID | Citation Hit | Key Points | Actionable | Hallucination | Notes |
|---------|--------------|------------|------------|---------------|-------|
| rag-project-context-001 | pass/fail | 0-100% | n/a | none/minor/critical | |
| agent-action-004 | pass/fail | 0-100% | pass/fail | none/minor/critical | |

后续可补自动化脚本读取 `docs/ai-eval-cases.json`，调用 `/ai/ask` 并输出 Markdown/JSON 报告；自动化版本不应把用户私有笔记内容写入仓库。
