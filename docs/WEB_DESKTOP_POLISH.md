# Noto · 知微 — Web 桌面完善路线图

> 在 [阶段八](../README.md#阶段八--稳定与体验) 与 [多端路线图](./MULTI_PLATFORM.md) 之间：**先把 Web 桌面体验做顺、做稳**，再启动移动 Web。  
> 部署见 [DEV_DEPLOY.md](./DEV_DEPLOY.md)，回归见 [E2E_CHECKLIST.md](./E2E_CHECKLIST.md)。

---

## 1. 目标

| 维度 | 说明 |
|------|------|
| **产品** | 桌面浏览器下「记录 → 提取 → 看板 → 提醒 → AI」闭环顺畅、可演示、可日常用 |
| **工程** | 契约稳定、关键路径有测试、交互一致、无明显 rough edge |
| **边界** | 本阶段 **不做** 移动 IA / PWA / 原生 App（见 [MULTI_PLATFORM.md](./MULTI_PLATFORM.md)） |

---

## 2. 阶段划分

```text
阶段九 A（体验收尾）  ← 当前，接近完成
  账号安全 · 布局记忆 · 快捷键 · OpenAPI · 表单校验

阶段九 B（深度打磨）
  空状态组件 · 加载/错误态 · RAG 进度 UI

阶段九 C（可选增强）
  邮件重置密码 · ES 检索 · 协作能力
```

---

## 3. 阶段九 A — 体验收尾

### 3.1 已完成

| 项 | 说明 |
|----|------|
| 侧栏宽度持久化 | `sidebarLayout` 写入 `localStorage`（nav / tree 宽度） |
| 忘记密码加强 | 确认密码、8–64 位校验、统一提示文案 |
| 防用户枚举 | `/auth/forgot-password` 无论邮箱是否存在均返回成功 |
| 注册密码校验 | 与后端 `@Size(min=8)` 对齐 |
| 快捷键帮助 | `?` 打开面板；用户菜单「键盘快捷键」 |
| 集成测试 | `AuthIntegrationTest.forgotPasswordShouldAlwaysReturnSuccess` |
| Profile 改密 | 可选改密 + 确认密码；仅改昵称无需当前密码 |
| OpenAPI | SpringDoc + Swagger UI（dev）+ `scripts/export-openapi.ps1` |
| 编辑器 Ctrl+S | 笔记编辑页手动保存 |
| API 错误提示 | `http.ts` 统一提取后端 `message` |
| **EmptyState 组件** | 搜索 / 待办 / 笔记 / 首页 / 上下文面板统一空状态 |
| **笔记筛选** | 全部 / 收藏 / 最近 / 归档（API + 侧栏 Segmented） |

### 3.2 待办（阶段九 B）

| 优先级 | 项 | 说明 |
|--------|-----|------|
| P1 | 提交 openapi.yaml | 发版前运行 `export-openapi.ps1` |
| P2 | 归档/收藏快捷入口 | 首页统计卡片跳转 `scope=favorite` |
| P2 | 忘记密码邮件流 | 生产环境接 SMTP + 一次性 token |

---

## 4. 阶段九 B — 深度打磨

### 4.1 交互与视觉

- 侧栏折叠状态是否持久化（当前仅宽度）
- 看板列宽、卡片信息密度可调
- AI 助手 / 编辑页抽屉宽度记忆
- 深色模式（低优先级，Ant Design Vue token）

### 4.2 性能

- 大知识库笔记树虚拟滚动
- 搜索 debounce + 结果缓存
- RAG 重建进度指示（已有 API，补 UI）

### 4.3 可访问性

- Cmd+K / 命令面板 ARIA（部分已有）
- 焦点陷阱与 Esc 关闭一致性
- 快捷键帮助与 Cmd+K footer 同步

---

## 5. 桌面验收清单（阶段九 A）

在 **Chrome / Edge 桌面**、窗口宽度 **≥ 1280px** 下：

### 账号

- [ ] 注册：密码少于 8 位时前端拦截
- [ ] 忘记密码：两次密码不一致时拦截
- [ ] 忘记密码：未知邮箱也提示「若邮箱已注册…」（不暴露是否存在）
- [ ] 已知邮箱重置后，新密码可登录

### 布局

- [ ] 拖拽调整左侧导航 / 笔记树宽度 → 刷新后宽度保持
- [ ] 打开文档后折叠侧栏 → 行为与改前一致

### 笔记筛选

- [ ] 侧栏切换「全部 / 收藏 / 最近 / 归档」→ 树列表与 API 一致
- [ ] 归档视图下打开文档 → 可取消归档；切换回「全部」后归档项不再显示
- [ ] URL `?scope=favorite` / `?scope=archived` 可分享/刷新保持筛选

### 快捷键

- [ ] `Ctrl+K` 打开/关闭命令面板
- [ ] 笔记编辑页 `Ctrl+S` 保存（有改动时）
- [ ] 非输入状态下按 `?` 打开快捷键帮助
- [ ] 用户菜单 →「键盘快捷键」同面板

### 核心闭环（沿用 E2E）

- [ ] 见 [E2E_CHECKLIST.md](./E2E_CHECKLIST.md) 路径 A + C

---

## 6. 与多端路线关系

```text
Web 桌面完善（本文 §3–5）
        ↓  闭环稳定、OpenAPI 就绪
移动 Web / PWA（MULTI_PLATFORM §6）
        ↓
原生 / 桌面壳
```

**原则**：桌面未验收项（E2E 路径 A/C）不启动移动 IA 大改；OpenAPI 可在桌面完善阶段并行。

---

## 7. 相关文档

| 文档 | 内容 |
|------|------|
| [README.md](../README.md) | 功能与 AI 路线图 |
| [MULTI_PLATFORM.md](./MULTI_PLATFORM.md) | 多端优先级（移动 Web 在桌面之后） |
| [DEV_DEPLOY.md](./DEV_DEPLOY.md) | 开发 / 部署 |
| [E2E_CHECKLIST.md](./E2E_CHECKLIST.md) | 15 分钟回归 |
| [openapi.yaml](./openapi.yaml) | OpenAPI 规范（`scripts/export-openapi.ps1` 生成） |

---

## 8. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-06-05 | 初版 + 阶段九 A 首批实现（侧栏记忆、忘记密码、快捷键） |
| 2026-06-11 | OpenAPI、Profile 改密、Ctrl+S、API 错误提示 |
| 2026-06-11 | EmptyState 组件、笔记收藏/归档筛选 |
