package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.util.AiConversationContext;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.service.AiChatSessionService;
import com.noto.zhihui.vo.ai.AiRouteVO;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class AiIntentRouter {

    private static final Pattern ACTION_SIGNAL = Pattern.compile(
            "(?:创建|新建|添加|写入|生成|更新|修改|删除|完成|标记).{0,10}(?:文档|笔记|待办|提醒)"
                    + "|提取.{0,8}待办"
                    + "|(?:设置|创建|安排|取消).{0,8}提醒"
                    + "|提醒我"
                    + "|帮我.{0,12}(?:做|完成|处理|整理|创建|写|安排|查|找|删|改)"
                    + "|整理.{0,10}(?:待办|文档|会议|笔记)"
                    + "|搜索.{0,24}(?:并|然后).{0,24}(?:摘要|提取|创建|提醒)"
                    + "|(?:就|那|好|行|可以).{0,6}(?:创建|新建|提取|提醒|安排|帮我|完成|删除)"
                    + "|(?:有哪些|有什么|列出|查询|查看).{0,16}(?:文档|笔记|待办|提醒)"
    );

    private static final Pattern CATALOG_QUESTION = Pattern.compile(
            "(?:有哪些|有什么|都有什么|列出|列表|清单|多少篇|几篇|几个|哪些).{0,24}(?:文档|笔记|文章|资料|待办|提醒)"
                    + "|(?:文档|笔记|知识库|资料库|分组|文件夹|待办|提醒).{0,24}(?:有哪些|有什么|列表|清单|多少)"
    );

    private static final Pattern CHAT_SIGNAL = Pattern.compile(
            "^(?:什么|为什么|怎么|如何|是否|有没有|谁|哪|请问|能否解释|帮我解释)"
                    + "|(?:是什么|什么意思|有什么区别|怎么理解|总结一下|概括一下|详细说说|展开说说)"
                    + "|这个知识库|主要讲|关于什么|讲了什么"
                    + "|文档.{0,12}(?:讲|说|内容|意思)"
    );

    private static final Pattern HOW_TO_QUESTION = Pattern.compile(
            "^(?:怎么|如何|怎样).{0,12}(?:创建|新建|设置|提取|使用|操作|做)"
    );

    private static final Pattern FOLLOW_UP = Pattern.compile(
            "^(?:那|然后|接着|继续|还有|再说|详细|展开|为什么|什么意思)"
    );

    private final ObjectMapper objectMapper;
    private final NotoAiProperties aiProperties;
    private final ChatModel chatModel;
    private final AiChatSessionService aiChatSessionService;

    public AiIntentRouter(
            ObjectMapper objectMapper,
            NotoAiProperties aiProperties,
            @Autowired(required = false) ChatModel chatModel,
            @Autowired(required = false) AiChatSessionService aiChatSessionService
    ) {
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
        this.chatModel = chatModel;
        this.aiChatSessionService = aiChatSessionService;
    }

    public AiRouteVO route(String message) {
        return route(message, null, null, null);
    }

    public AiRouteVO route(String message, Long sessionId, Long userId, String recentContext) {
        String input = message == null ? "" : message.trim();
        if (!StringUtils.hasText(input)) {
            return new AiRouteVO("chat", "空输入默认问答", "rule");
        }
        String contextBlock = resolveContextBlock(sessionId, userId, recentContext);
        if (aiProperties.isEnabled() && chatModel != null) {
            try {
                AiRouteVO llm = routeWithLlm(input, contextBlock);
                if (llm != null) {
                    return llm;
                }
            } catch (Exception ignored) {
                // fallback to rules
            }
        }
        return routeWithRules(input, contextBlock);
    }

    private String resolveContextBlock(Long sessionId, Long userId, String recentContext) {
        String sessionBlock = "";
        if (sessionId != null && userId != null && aiChatSessionService != null) {
            sessionBlock = aiChatSessionService.buildRecentDialogBlock(sessionId, userId, 8);
        }
        return AiConversationContext.resolveContextBlock(recentContext, sessionBlock);
    }

    private AiRouteVO routeWithLlm(String input, String contextBlock) {
        String contextSection = StringUtils.hasText(contextBlock)
                ? """
                近期对话（用于理解指代，如「这个」「上面」「那就帮我创建」）：
                %s

                """.formatted(contextBlock)
                : "";
        String prompt = """
                你是 Noto 知微 AI 助手的意图路由器。AI 助手定位是系统操控：查列表、建文档、管待办、设提醒。
                根据用户最新输入（结合对话历史）判断应走哪种模式：
                - agent：查目录/列表、创建/修改/删除/完成 文档·待办·提醒，或任何需要操作系统数据的请求
                - chat：仅当用户要阅读解释文档内容（总结、对比、为什么），且不需要改系统数据

                关键规则：
                1. 「灵感有哪些文档」「列出待办」「完成 XX 待办」→ agent
                2. 「这篇文档讲了什么」「总结一下」→ chat
                3. 「怎么创建待办？」→ chat（问方法）
                4. 「创建今晚8点的待办」→ agent
                5. 对话中「那就帮我创建/完成/删除」→ agent
                6. 不确定时：涉及列表或写操作 → agent；纯阅读理解 → chat

                %s仅输出 JSON，不要 markdown：
                {"intent":"chat或agent","reason":"一句中文说明"}

                用户最新输入：
                %s
                """.formatted(contextSection, input);

        String raw = chatModel.chat(prompt);
        return parseLlmResult(raw, input, contextBlock);
    }

    private AiRouteVO parseLlmResult(String raw, String input, String contextBlock) {
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            String intent = node.has("intent") ? node.get("intent").asText() : "";
            String reason = node.has("reason") ? node.get("reason").asText() : "";
            if ("chat".equalsIgnoreCase(intent)) {
                return new AiRouteVO("chat", reasonOrDefault(reason, "问答查资料"), "llm");
            }
            if ("agent".equalsIgnoreCase(intent)) {
                return new AiRouteVO("agent", reasonOrDefault(reason, "执行办事流程"), "llm");
            }
        } catch (Exception ignored) {
            // fallback below
        }
        AiRouteVO rules = routeWithRules(input, contextBlock);
        rules.setSource("rule");
        return rules;
    }

    private AiRouteVO routeWithRules(String input, String contextBlock) {
        if (HOW_TO_QUESTION.matcher(input).find()) {
            return new AiRouteVO("chat", "询问操作方法", "rule");
        }

        if (CATALOG_QUESTION.matcher(input).find()) {
            return new AiRouteVO("agent", "查询知识库目录或列表", "rule");
        }

        boolean action = ACTION_SIGNAL.matcher(input).find();
        boolean chat = CHAT_SIGNAL.matcher(input).find();
        boolean followUp = FOLLOW_UP.matcher(input).find();
        boolean endsWithQuestion = input.endsWith("?") || input.endsWith("？");

        if (followUp && !action && StringUtils.hasText(contextBlock)) {
            if (CHAT_SIGNAL.matcher(input).find() || endsWithQuestion) {
                return new AiRouteVO("chat", "结合上下文的追问", "rule");
            }
        }

        if (action && !chat) {
            return new AiRouteVO("agent", "包含执行类指令", "rule");
        }
        if (chat || (endsWithQuestion && !action)) {
            return new AiRouteVO("chat", "包含提问或解释需求", "rule");
        }
        if (AiConversationContext.needsContextBoost(input) && StringUtils.hasText(contextBlock)) {
            String priorTopic = AiConversationContext.extractLastUserTopic(contextBlock);
            if (StringUtils.hasText(priorTopic)
                    && ACTION_SIGNAL.matcher(priorTopic).find()
                    && Pattern.compile("(?:好|行|可以|那就|帮我|创建|提取|提醒)").matcher(input).find()) {
                return new AiRouteVO("agent", "承接上文执行意图", "rule");
            }
        }
        if (Pattern.compile("(?:创建|新建|提取|提醒|待办|整理|写入|安排)").matcher(input).find()) {
            return new AiRouteVO("agent", "包含办事关键词", "rule");
        }
        return new AiRouteVO("chat", "默认问答", "rule");
    }

    private String reasonOrDefault(String reason, String fallback) {
        return StringUtils.hasText(reason) ? reason.trim() : fallback;
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}
