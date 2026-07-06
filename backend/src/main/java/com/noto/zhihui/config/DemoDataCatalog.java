package com.noto.zhihui.config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 演示数据目录：生成 50+ 篇看起来像真实使用的文档。
 */
public final class DemoDataCatalog {

    public static final String FOLDER_WORK = "工作笔记";
    public static final String FOLDER_PROJECT = "项目资料";
    public static final String FOLDER_LEARN = "个人学习";
    public static final String FOLDER_MEETING = "会议纪要";
    public static final String FOLDER_INBOX = "灵感碎片";
    public static final String FOLDER_REF = "参考资料";

    public record NoteSeed(
            String title,
            String folder,
            String content,
            String parentTitle,
            List<String> tags,
            int daysAgo,
            boolean favorite
    ) {
    }

    public record TodoSeed(
            String title,
            String noteTitle,
            String horizon,
            int dueDaysOffset,
            int dueHour,
            int status
    ) {
    }

    private DemoDataCatalog() {
    }

    public static List<String> folderNames() {
        return List.of(
                FOLDER_WORK,
                FOLDER_PROJECT,
                FOLDER_LEARN,
                FOLDER_MEETING,
                FOLDER_INBOX,
                FOLDER_REF
        );
    }

    public static List<String> tagDefinitions() {
        return List.of("演示", "工作", "学习", "会议", "产品", "客户", "待整理", "复盘");
    }

    public static List<NoteSeed> allNotes(LocalDate today) {
        return allNotes(today, "demo", "123456");
    }

    public static List<NoteSeed> allNotes(LocalDate today, String demoUsername, String demoPassword) {
        List<NoteSeed> seeds = new ArrayList<>();
        seeds.addAll(coreNotes(today, demoUsername, demoPassword));
        seeds.addAll(weeklyMeetings(today, 14));
        seeds.addAll(sprintRetros(10));
        seeds.addAll(workJournals(today, 10, demoUsername, demoPassword));
        seeds.addAll(projectDocs());
        seeds.addAll(learningNotes());
        seeds.addAll(clientNotes());
        seeds.addAll(referenceClips());
        seeds.addAll(childNotes(demoUsername));
        seeds.addAll(inboxFragments());
        return seeds;
    }

    public static List<TodoSeed> extraTodos() {
        LocalDate recentJournalDay = LocalDate.now().minusDays(1);
        while (recentJournalDay.getDayOfWeek().getValue() > 5) {
            recentJournalDay = recentJournalDay.minusDays(1);
        }
        String recentJournalTitle = "工作日志 · " + recentJournalDay;
        return List.of(
                new TodoSeed("整理 Q2 目标", "周会纪要 · MVP 演示", "action", 1, 18, 0),
                new TodoSeed("回复客户邮件", "周会纪要 · MVP 演示", "action", 2, 12, 1),
                new TodoSeed("周五前提交周报", "周会纪要 · MVP 演示", "action", 5, 17, 0),
                new TodoSeed("评审 PR #128", "周会纪要 · MVP 演示", "action", 0, 17, 0),
                new TodoSeed("准备周会演示脚本", "产品 Roadmap · Q2-Q3", "action", 3, 10, 0),
                new TodoSeed("更新 DEV_DEPLOY 文档", "产品 Roadmap · Q2-Q3", "action", 7, 18, 0),
                new TodoSeed("联调 MinIO 附件上传", "周会纪要 · MVP 演示", "action", 1, 15, 0),
                new TodoSeed("梳理待办中心交互", "周会纪要 · MVP 演示", "action", -2, 17, 2),
                new TodoSeed("完成首页看板联调", "周会纪要 · MVP 演示", "action", -1, 12, 2),
                new TodoSeed("2026 技术沉淀计划", "产品 Roadmap · Q2-Q3", "long_term", 30, 9, 0),
                new TodoSeed("学习 RAG 索引优化", "产品 Roadmap · Q2-Q3", "long_term", 14, 20, 0),
                new TodoSeed("团队知识库规范草案", "产品 Roadmap · Q2-Q3", "long_term", 45, 9, 0),
                new TodoSeed("补充集成测试用例", "迭代复盘 · Sprint 18", "action", 2, 16, 0),
                new TodoSeed("优化笔记树加载性能", "知微架构笔记", "action", 4, 11, 0),
                new TodoSeed("整理 Acme 演示反馈", "客户沟通记录 · Acme", "action", 1, 10, 0),
                new TodoSeed("输出 Vue 组件规范 v0.2", "Vue 3 复习清单", "action", 6, 18, 0),
                new TodoSeed("归档三月会议材料", "会议纪要索引", "action", 3, 14, 0),
                new TodoSeed("复盘上周逾期待办", recentJournalTitle, "action", 0, 20, 0),
                new TodoSeed("阅读 pgvector 官方文档", "PostgreSQL 笔记", "long_term", 21, 21, 0),
                new TodoSeed("设计移动端 IA 草案", "产品 Roadmap · Q2-Q3", "long_term", 60, 9, 0)
        );
    }

    private static List<NoteSeed> coreNotes(LocalDate today, String demoUsername, String demoPassword) {
        return List.of(
                new NoteSeed(
                        "周会纪要 · MVP 演示",
                        FOLDER_MEETING,
                        meetingContent(today),
                        null,
                        List.of("演示", "会议", "工作"),
                        0,
                        true
                ),
                new NoteSeed(
                        "Noto 功能导览",
                        FOLDER_WORK,
                        guideContent(demoUsername, demoPassword),
                        null,
                        List.of("演示"),
                        2,
                        true
                ),
                new NoteSeed(
                        "产品 Roadmap · Q2-Q3",
                        FOLDER_PROJECT,
                        roadmapContent(),
                        null,
                        List.of("产品", "工作"),
                        5,
                        true
                ),
                new NoteSeed(
                        "知微架构笔记",
                        FOLDER_PROJECT,
                        architectureContent(),
                        null,
                        List.of("产品", "学习"),
                        8,
                        false
                ),
                new NoteSeed(
                        "Vue 3 复习清单",
                        FOLDER_LEARN,
                        vueContent(),
                        null,
                        List.of("学习"),
                        4,
                        false
                ),
                new NoteSeed(
                        "客户沟通记录 · Acme",
                        FOLDER_WORK,
                        clientContent(),
                        null,
                        List.of("客户", "工作"),
                        3,
                        false
                ),
                new NoteSeed(
                        "会议纪要索引",
                        FOLDER_MEETING,
                        indexContent(),
                        null,
                        List.of("会议", "工作"),
                        12,
                        false
                ),
                new NoteSeed(
                        "PostgreSQL 笔记",
                        FOLDER_LEARN,
                        postgresContent(),
                        null,
                        List.of("学习"),
                        15,
                        false
                )
        );
    }

    private static List<NoteSeed> weeklyMeetings(LocalDate today, int weeks) {
        List<NoteSeed> list = new ArrayList<>();
        for (int i = 1; i <= weeks; i++) {
            LocalDate monday = today.minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
            String title = "周会纪要 · " + monday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            list.add(new NoteSeed(
                    title,
                    FOLDER_MEETING,
                    weeklyMeetingBody(monday),
                    null,
                    List.of("会议", "工作"),
                    i * 7,
                    i <= 2
            ));
        }
        return list;
    }

    private static List<NoteSeed> sprintRetros(int count) {
        List<NoteSeed> list = new ArrayList<>();
        for (int sprint = 18 - count + 1; sprint <= 18; sprint++) {
            list.add(new NoteSeed(
                    "迭代复盘 · Sprint " + sprint,
                    FOLDER_PROJECT,
                    sprintRetroBody(sprint),
                    null,
                    List.of("产品", "复盘"),
                    (18 - sprint) * 4 + 2,
                    sprint >= 17
            ));
        }
        return list;
    }

    private static List<NoteSeed> workJournals(LocalDate today, int days, String demoUsername, String demoPassword) {
        List<NoteSeed> list = new ArrayList<>();
        LocalDate cursor = today;
        int added = 0;
        while (added < days) {
            if (cursor.getDayOfWeek().getValue() <= 5) {
                String title = "工作日志 · " + cursor;
                list.add(new NoteSeed(
                        title,
                        FOLDER_WORK,
                        journalBody(cursor, demoUsername, demoPassword),
                        null,
                        List.of("工作"),
                        added + 1,
                        false
                ));
                added++;
            }
            cursor = cursor.minusDays(1);
        }
        return list;
    }

    private static List<NoteSeed> projectDocs() {
        return List.of(
                new NoteSeed("PRD · 文档树拖拽", FOLDER_PROJECT, prdBody("文档树拖拽", "支持分组内自由排序、子文档提升层级"), null, List.of("产品"), 20, false),
                new NoteSeed("PRD · AI 提取待办", FOLDER_PROJECT, prdBody("AI 提取待办", "规则提取 + AI 审查确认"), null, List.of("产品"), 18, false),
                new NoteSeed("PRD · 首页行动看板", FOLDER_PROJECT, prdBody("首页行动看板", "队列 / 进行中 / 完成三列拖拽"), null, List.of("产品"), 16, false),
                new NoteSeed("技术方案 · RAG 检索", FOLDER_PROJECT, techSpecBody("RAG", "pgvector + 混合检索"), null, List.of("产品", "学习"), 22, false),
                new NoteSeed("技术方案 · 附件存储", FOLDER_PROJECT, techSpecBody("MinIO", "预签名上传与下载"), null, List.of("产品"), 25, false),
                new NoteSeed("发布说明 · v0.8.0", FOLDER_PROJECT, releaseBody("0.8.0"), null, List.of("产品", "工作"), 9, true),
                new NoteSeed("发布说明 · v0.7.2", FOLDER_PROJECT, releaseBody("0.7.2"), null, List.of("产品"), 28, false),
                new NoteSeed("竞品调研 · 语雀 / Notion", FOLDER_PROJECT, competitorBody(), null, List.of("产品", "学习"), 35, false)
        );
    }

    private static List<NoteSeed> learningNotes() {
        return List.of(
                new NoteSeed("Spring Boot 3 备忘", FOLDER_LEARN, cheatSheet("Spring Boot 3", "虚拟线程、AOT、Observability"), null, List.of("学习"), 11, false),
                new NoteSeed("MyBatis-Plus 实践", FOLDER_LEARN, cheatSheet("MyBatis-Plus", "分页、逻辑删除、自动填充"), null, List.of("学习"), 13, false),
                new NoteSeed("Pinia 状态设计", FOLDER_LEARN, cheatSheet("Pinia", "store 拆分与持久化"), null, List.of("学习"), 7, false),
                new NoteSeed("Markdown 写作规范", FOLDER_LEARN, writingGuideBody(), null, List.of("学习", "工作"), 30, false),
                new NoteSeed("Docker Compose 速查", FOLDER_LEARN, cheatSheet("Docker", "db / minio / backend / frontend"), null, List.of("学习"), 17, false),
                new NoteSeed("Prompt 工程笔记", FOLDER_LEARN, promptBody(), null, List.of("学习", "产品"), 6, false)
        );
    }

    private static List<NoteSeed> clientNotes() {
        return List.of(
                new NoteSeed("客户沟通 · 北辰科技", FOLDER_WORK, clientThread("北辰科技", "私有化部署清单"), null, List.of("客户"), 14, false),
                new NoteSeed("客户沟通 · 云图教育", FOLDER_WORK, clientThread("云图教育", "教师协作场景"), null, List.of("客户"), 21, false),
                new NoteSeed("客户沟通 · 澄观医疗", FOLDER_WORK, clientThread("澄观医疗", "病历摘要合规"), null, List.of("客户"), 27, false),
                new NoteSeed("投标材料 · 政务知识库", FOLDER_WORK, bidBody(), null, List.of("客户", "工作"), 32, false)
        );
    }

    private static List<NoteSeed> referenceClips() {
        return List.of(
                new NoteSeed("剪藏 · 待办 GTD 方法", FOLDER_REF, clipBody("Getting Things Done", "收集、厘清、组织、回顾、执行"), null, List.of("学习"), 40, false),
                new NoteSeed("剪藏 · 知识库信息架构", FOLDER_REF, clipBody("IA Patterns", "文件夹 vs 标签 vs 图谱"), null, List.of("学习", "产品"), 38, false),
                new NoteSeed("剪藏 · PostgreSQL 性能", FOLDER_REF, clipBody("PG Performance", "索引、VACUUM、连接池"), null, List.of("学习"), 45, false),
                new NoteSeed("剪藏 · Vue 性能优化", FOLDER_REF, clipBody("Vue Perf", "异步组件、虚拟列表"), null, List.of("学习"), 19, false)
        );
    }

    private static List<NoteSeed> childNotes(String demoUsername) {
        return List.of(
                new NoteSeed("行动项 · Q2 目标拆解", FOLDER_MEETING, childActionBody("Q2 目标", demoUsername), "周会纪要 · MVP 演示", List.of("工作"), 1, false),
                new NoteSeed("行动项 · 客户邮件草稿", FOLDER_WORK, childActionBody("客户邮件", demoUsername), "客户沟通记录 · Acme", List.of("客户"), 2, false),
                new NoteSeed("子文档 · API 契约导出", FOLDER_PROJECT, childActionBody("OpenAPI 导出脚本", demoUsername), "知微架构笔记", List.of("产品"), 6, false),
                new NoteSeed("子文档 · 看板拖拽规则", FOLDER_PROJECT, childActionBody("看板状态机", demoUsername), "PRD · 首页行动看板", List.of("产品"), 10, false),
                new NoteSeed("子文档 · RAG 分块策略", FOLDER_PROJECT, childActionBody("分块 512 tokens", demoUsername), "技术方案 · RAG 检索", List.of("学习", "产品"), 12, false)
        );
    }

    private static List<NoteSeed> inboxFragments() {
        return List.of(
                new NoteSeed("灵感 · 命令面板快捷操作", FOLDER_INBOX, fragmentBody("Cmd+K 支持最近文档、待办、AI"), null, List.of("待整理"), 1, false),
                new NoteSeed("灵感 · 笔记双向链接", FOLDER_INBOX, fragmentBody("引用块 hover 预览"), null, List.of("待整理"), 2, false),
                new NoteSeed("灵感 · 周报自动生成", FOLDER_INBOX, fragmentBody("从本周完成待办 + 编辑过的文档汇总"), null, List.of("待整理", "产品"), 3, false),
                new NoteSeed("待读 · LangChain4j 工具调用", FOLDER_INBOX, fragmentBody("Agent 工具链与确认流"), null, List.of("待整理", "学习"), 5, false),
                new NoteSeed("待读 · 多端同步策略", FOLDER_INBOX, fragmentBody("Web / 移动 / 离线"), null, List.of("待整理", "产品"), 8, false)
        );
    }

    private static String meetingContent(LocalDate today) {
        return """
                # 周会纪要 · 知微 MVP 演示

                日期：%s

                ## 讨论要点

                - **Q2 目标**需要对齐产品路线图与阶段交付
                - 客户邮件需在本周内回复
                - 首页看板拖拽与搜索定位已可用，适合对外演示

                ## 行动项

                - [ ] 整理 Q2 目标
                - [ ] 回复客户邮件
                - [ ] 周五前提交周报
                - [ ] 评审 PR #128
                - [ ] 联调 MinIO 附件上传
                """.formatted(today);
    }

    private static String guideContent(String demoUsername, String demoPassword) {
        return """
                # Noto · 知微 功能导览

                > 演示账号：`%s` / `%s`

                ## 15 分钟演示路径

                1. **写文档** — 打开「周会纪要」，展示 Markdown 与自动保存
                2. **提取待办** — 右键或 AI 面板 → 规则提取待办 → 审查确认
                3. **行动看板** — 首页拖拽：队列 → 进行中 → 完成
                4. **搜索定位** — 全局搜索或 `Ctrl+K` 搜「Q2」，跳转正文高亮
                5. **提醒** — 待办中心查看提醒；到期后页内/系统通知
                """.formatted(demoUsername, demoPassword);
    }

    private static String roadmapContent() {
        return """
                # 产品 Roadmap · Q2-Q3

                ## Q2 重点

                - 文档树拖拽与分组目录
                - 待办中心行动看板
                - AI 提取待办与摘要

                ## Q3 方向

                - 多知识库协作
                - 移动端适配
                - RAG 检索质量优化
                """;
    }

    private static String architectureContent() {
        return """
                # 知微架构笔记

                - 前端：Vue 3 + Vite + Ant Design Vue
                - 后端：Spring Boot 3 + MyBatis-Plus
                - 存储：PostgreSQL + MinIO
                - AI：LangChain4j + DashScope
                """;
    }

    private static String vueContent() {
        return """
                # Vue 3 复习清单

                - Composition API 与 `<script setup>`
                - Pinia 状态管理
                - Vue Router 懒加载
                - 性能：异步组件与代码分割
                """;
    }

    private static String clientContent() {
        return """
                # 客户沟通记录 · Acme

                **联系人**：张经理

                ## 诉求

                - 待办与文档联动演示
                - AI 提取待办准确率
                - 私有化部署评估
                """;
    }

    private static String indexContent() {
        return """
                # 会议纪要索引

                近三个月周会、复盘与客户会议归档在「会议纪要」分组。

                - 按日期命名的周会纪要
                - Sprint 复盘系列
                - 客户沟通线程
                """;
    }

    private static String postgresContent() {
        return """
                # PostgreSQL 笔记

                - 本地 dev：`noto_zhihui_dev`
                - 扩展：`vector`（RAG）
                - 连接池：HikariCP 默认配置
                """;
    }

    private static String weeklyMeetingBody(LocalDate monday) {
        return """
                # 周会纪要 · %s

                ## 本周进展

                - 文档与待办联动优化
                - 修复若干 UI 细节
                - 补充演示数据与部署文档

                ## 下周计划

                - 继续阶段九体验收尾
                - 准备对外演示脚本

                ## 风险

                - 暂无阻塞项
                """.formatted(monday);
    }

    private static String sprintRetroBody(int sprint) {
        return """
                # 迭代复盘 · Sprint %d

                ## 做得好

                - 交付节奏稳定
                - 关键路径有集成测试

                ## 待改进

                - 大列表加载性能
                - 空状态与错误提示统一

                ## 下个 Sprint 重点

                - 看板体验与提醒链路
                """.formatted(sprint);
    }

    private static String journalBody(LocalDate day, String demoUsername, String demoPassword) {
        return """
                # 工作日志 · %s

                ## 今日完成

                - 文档编辑与待办提取联调
                - 首页看板拖拽验收
                - 回复 2 封客户邮件

                ## 明日计划

                - 继续性能优化
                - 整理本周会议材料

                ## 备注

                - 演示环境：`%s` / `%s`
                """.formatted(day, demoUsername, demoPassword);
    }

    private static String prdBody(String feature, String summary) {
        return """
                # PRD · %s

                ## 背景

                %s

                ## 用户故事

                - 作为知识工作者，我希望在侧栏快速定位文档
                - 作为项目经理，我希望从会议纪要提取行动项

                ## 验收标准

                - [ ] 主路径 15 分钟内可演示
                - [ ] 有集成测试覆盖
                """.formatted(feature, summary);
    }

    private static String techSpecBody(String topic, String detail) {
        return """
                # 技术方案 · %s

                %s

                ## 接口

                - REST `/api/v1/...`
                - 统一 `ApiResponse` 包装

                ## 风险

                - 需评估数据迁移与回滚
                """.formatted(topic, detail);
    }

    private static String releaseBody(String version) {
        return """
                # 发布说明 · v%s

                ## 新功能

                - 文档树拖拽
                - 演示数据种子 v3

                ## 修复

                - 分组目录展示完整子文档
                - 刷新性能优化
                """.formatted(version);
    }

    private static String competitorBody() {
        return """
                # 竞品调研 · 语雀 / Notion

                | 维度 | 语雀 | Notion | 知微 |
                |------|------|--------|------|
                | 文档树 | 强 | 中 | 中 |
                | 待办联动 | 弱 | 中 | 强 |
                | AI | 中 | 强 | 中 |
                """;
    }

    private static String cheatSheet(String topic, String bullets) {
        return """
                # %s 备忘

                %s

                ## 链接

                - 项目内 `docs/DEV_DEPLOY.md`
                """.formatted(topic, bullets);
    }

    private static String writingGuideBody() {
        return """
                # Markdown 写作规范

                - 标题层级不跳级
                - 会议纪要必有「行动项」列表
                - 代码块标注语言
                """;
    }

    private static String promptBody() {
        return """
                # Prompt 工程笔记

                - 结构化输出：JSON / Markdown
                - 引用知识库片段时标注来源
                - 提取待办需可审查确认
                """;
    }

    private static String clientThread(String name, String topic) {
        return """
                # 客户沟通 · %s

                **主题**：%s

                ## 纪要

                - 需求澄清完成
                - 约定两周后 POC 演示

                ## 后续

                - [ ] 发送环境账号
                - [ ] 准备功能对照表
                """.formatted(name, topic);
    }

    private static String bidBody() {
        return """
                # 投标材料 · 政务知识库

                ## 亮点

                - 本地部署
                - 待办与文档闭环
                - 审计日志（规划中）
                """;
    }

    private static String clipBody(String source, String summary) {
        return """
                # 剪藏 · %s

                > %s

                ## 我的笔记

                - 可复用到知微 IA 设计
                """.formatted(source, summary);
    }

    private static String childActionBody(String topic, String demoUsername) {
        return """
                ## %s

                - 负责人：%s
                - 状态：进行中
                - 关联父文档中的行动项列表
                """.formatted(topic, demoUsername);
    }

    private static String fragmentBody(String idea) {
        return """
                # 灵感碎片

                %s

                ---

                _Captured at %s_
                """.formatted(idea, LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)));
    }
}
