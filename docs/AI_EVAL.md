# Noto AI Evaluation Protocol

> 目标：让 Noto 的 AI 能力不只“能用”，还可被面试官验证其检索质量、可执行性和工程可信度。

---

## 1. 评测范围

| 范围 | 说明 |
|------|------|
| RAG 问答 | 用户问题是否能召回正确文档，并给出带引用的回答 |
| Agent 办事 | 自然语言是否能被拆成可确认工具调用 |
| 知识到行动闭环 | AI 输出是否能落回笔记、待办、提醒、复盘 |
| 生产可用性 | 是否有失败兜底、禁用 AI 提示、审计和延迟记录 |

评测用例文件：[`ai-eval-cases.json`](./ai-eval-cases.json)

---

## 2. 核心指标

| Metric | Definition | Target |
|--------|------------|--------|
| Citation hit rate | 回答至少引用一个期望证据来源 | >= 80% |
| Key point coverage | 回答覆盖期望要点比例 | >= 75% |
| Actionability | 办事类问题产出可确认步骤或可执行结果 | >= 80% |
| Hallucination risk | 编造项目不存在能力或不存在文件 | 0 critical cases |
| User confirmation safety | 写操作必须进入确认或预览链路 | 100% |

---

## 3. 手动评测流程

1. 启动后端、前端和 pgvector 数据库。
2. 登录演示账号，确认 AI 已启用。
3. 打开 `AI 展示` 页面，记录 RAG chunks、indexed notes、最近 Agent runs。
4. 打开 `docs/ai-eval-cases.json`。
5. 对每个 case，在 AI 助手中输入 `question`。
6. 记录回答是否覆盖 `expectedAnswerPoints`。
7. 检查引用是否命中 `expectedEvidence`。
8. 如果是办事类问题，检查是否出现 Agent 步骤和用户确认。
9. 记录失败原因：召回失败、回答遗漏、无引用、编造、不进入确认链。

---

## 4. 评分模板

| Case ID | Citation Hit | Key Points | Actionable | Hallucination | Notes |
|---------|--------------|------------|------------|---------------|-------|
| rag-project-context-001 | pass/fail | 0-100% | n/a | none/minor/critical | |
| rag-citation-002 | pass/fail | 0-100% | n/a | none/minor/critical | |
| rag-retrieval-003 | pass/fail | 0-100% | n/a | none/minor/critical | |
| agent-action-004 | pass/fail | 0-100% | pass/fail | none/minor/critical | |
| workflow-demo-005 | pass/fail | 0-100% | pass/fail | none/minor/critical | |

---

## 5. 面试讲法

可以这样解释：

> 我没有只做“把 prompt 发给模型”。Noto 做了私有知识库 RAG、引用回链、可确认 Agent、AI 调用审计和业务闭环。为了避免只凭主观感受判断效果，我准备了固定评测集，按引用命中率、要点覆盖率、可执行性和幻觉风险来检查。

---

## 6. 后续自动化方向

当前文档先作为手动评测协议。后续可补一个脚本：

```bash
node scripts/ai-eval.mjs --base-url http://localhost:9086/api/v1 --token <token>
```

脚本读取 `docs/ai-eval-cases.json`，调用 `/ai/ask`，输出 Markdown/JSON 报告。自动化版本不应把用户私有笔记内容写入仓库。
