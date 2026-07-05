import { routeAiIntent, type AiRouteResult } from '../api/ai';

export type AiIntent = 'chat' | 'agent' | 'clarify';

export type { AiRouteResult };

export interface ResolveAiIntentOptions {
  sessionId?: string;
  recentContext?: string;
}

const CATALOG_QUESTION =
  /(?:有哪些|有什么|都有什么|列出|列表|清单|多少篇|几篇|几个|哪些).{0,24}(?:文档|笔记|文章|资料|待办|提醒)|(?:文档|笔记|知识库|资料库|分组|文件夹|待办|提醒).{0,24}(?:有哪些|有什么|列表|清单|多少)/;

const ACTION_SIGNALS: RegExp[] = [
  /(?:创建|新建|添加|写入|生成|更新|修改|删除|完成|标记).{0,10}(?:文档|笔记|待办|提醒)/,
  /提取.{0,8}待办/,
  /(?:设置|创建|安排|取消).{0,8}提醒/,
  /提醒我/,
  /帮我.{0,12}(?:做|完成|处理|整理|创建|写|安排|查|找|删|改)/,
  /整理.{0,10}(?:待办|文档|会议|笔记)/,
  /搜索.{0,24}(?:并|然后).{0,24}(?:摘要|提取|创建|提醒)/,
  /(?:今晚|今天|明天|后天).{0,16}(?:洗澡|开会|提交|完成|提醒)/,
  /(?:就|那|好|行|可以).{0,6}(?:创建|新建|提取|提醒|安排|帮我|完成|删除)/,
  /(?:有哪些|有什么|列出|查询|查看).{0,16}(?:文档|笔记|待办|提醒|文件|分享|标签|分组)/,
  /(?:分享|网盘|标签|分组|文件夹)/,
];

const CHAT_SIGNALS: RegExp[] = [
  /^(?:什么|为什么|怎么|如何|是否|有没有|哪些|谁|哪|请问|能否解释|帮我解释)/,
  /(?:是什么|什么意思|有什么区别|怎么理解|总结一下|概括一下|详细说说|展开说说)/,
  /这个知识库|主要讲|关于什么|讲了什么/,
  /^(?:那|然后|接着|继续|还有|再说|详细|展开|为什么|什么意思)/,
];

const HOW_TO_QUESTION = /^(?:怎么|如何|怎样).{0,12}(?:创建|新建|设置|提取|使用|操作|做)/;

const FOLLOW_UP = /^(?:那|然后|接着|继续|还有|再说|详细|展开|为什么|什么意思)/;
const PENDING_PLAN_EDIT = /(?:改|修改|换|调整|推迟|提前|延迟|延后|挪|改成|换成).{0,16}(?:时间|日期|明天|后天|今天|今晚|下周|周[一二三四五六日天]|点|上午|下午|晚上|标题|内容|提醒|待办|一周|[一二三四五六七八九十\d]+天)|(?:提醒|待办).{0,8}(?:推迟|提前|延迟|延后|往后|往前|挪).{0,8}(?:一周|[一二三四五六七八九十\d]+天)/;
const PENDING_PLAN_TARGETED_SHIFT = /(?:提醒|待办).{0,8}(?:延迟|延后|推迟|往后|提前|往前|挪).{0,8}(?:一周|[一二三四五六七八九十\d]+天)/;
const PENDING_PLAN_AMBIGUOUS_SHIFT = /^(?:延迟|延后|推迟|往后|提前|往前|挪).{0,8}(?:一周|[一二三四五六七八九十\d]+天)$/;
const VAGUE_CONTEXT_REPLY = /^(?:改一下|修改一下|换一下|调整一下|改改|不对|不是|重新|算了|那个|这个|就这个|就这样|可以|好|行|嗯|对)$/;

function detectWithContextFallback(text: string, recentContext?: string): AiIntent {
  const input = text.trim();
  if (!input) return 'chat';

  if (HOW_TO_QUESTION.test(input)) return 'chat';
  if (CATALOG_QUESTION.test(input)) return 'agent';
  const hasPendingPlan = Boolean(recentContext?.includes('待确认方案') || recentContext?.includes('PENDING_PLAN_JSON:'));
  if (hasPendingPlan && PENDING_PLAN_TARGETED_SHIFT.test(input)) return 'agent';
  if (hasPendingPlan && PENDING_PLAN_AMBIGUOUS_SHIFT.test(input)) return 'clarify';
  if (hasPendingPlan && PENDING_PLAN_EDIT.test(input)) return 'agent';

  const actionScore = ACTION_SIGNALS.reduce((n, re) => n + (re.test(input) ? 1 : 0), 0);
  const chatScore = CHAT_SIGNALS.reduce((n, re) => n + (re.test(input) ? 1 : 0), 0);
  const endsWithQuestion = /[?？]$/.test(input);

  if (FOLLOW_UP.test(input) && actionScore === 0 && recentContext?.trim()) {
    if (chatScore > 0 || endsWithQuestion) return 'chat';
  }

  if (actionScore > 0 && actionScore >= chatScore && chatScore === 0) return 'agent';
  if (chatScore > 0 || (endsWithQuestion && actionScore === 0)) return 'chat';

  if (recentContext?.trim() && input.length <= 18) {
    const lastUser = [...recentContext.split('\n')]
      .reverse()
      .find((line) => line.startsWith('用户：') || line.startsWith('用户:'));
    if (lastUser && ACTION_SIGNALS.some((re) => re.test(lastUser)) && /(?:好|行|可以|那就|帮我|创建|提取|提醒)/.test(input)) {
      return 'agent';
    }
  }

  if (recentContext?.trim() && input.length <= 12 && VAGUE_CONTEXT_REPLY.test(input)) {
    return 'clarify';
  }

  if (/(?:创建|新建|提取|提醒|待办|整理|写入|安排)/.test(input)) return 'agent';

  return 'chat';
}

/** 本地规则兜底（AI 未启用或路由接口失败时使用） */
export function detectAiIntentFallback(text: string, recentContext?: string): AiIntent {
  return detectWithContextFallback(text, recentContext);
}

/** @deprecated 使用 resolveAiIntent */
export function detectAiIntent(text: string): AiIntent {
  return detectAiIntentFallback(text);
}

/** 优先走后端 LLM 路由，失败时本地规则兜底 */
export async function resolveAiIntent(
  text: string,
  options?: ResolveAiIntentOptions,
): Promise<AiRouteResult> {
  const message = text.trim();
  const recentContext = options?.recentContext?.trim();
  try {
    const result = await routeAiIntent({
      message,
      sessionId: options?.sessionId,
      recentContext,
    });
    if (result?.intent === 'chat' || result?.intent === 'agent' || result?.intent === 'clarify') {
      return result;
    }
  } catch {
    // 接口不可用时走本地规则
  }
  return {
    intent: detectAiIntentFallback(message, recentContext),
    source: 'rule',
    reason: '本地规则判断',
  };
}

export function intentLabel(intent: AiIntent): string {
  if (intent === 'clarify') return '澄清';
  return intent === 'agent' ? '操控' : '阅读';
}
