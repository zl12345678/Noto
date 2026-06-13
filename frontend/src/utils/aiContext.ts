import type { AiChatMessage } from '../store/aiChat';

/** 构建传给后端的近期对话块（不含当前这条用户输入） */
export function buildRecentContextBlock(
  messages: AiChatMessage[],
  maxTurns = 6,
  maxChars = 1200,
): string {
  const recent = messages.slice(-maxTurns);
  const lines: string[] = [];
  for (const item of recent) {
    const text = item.content?.trim().replace(/\s+/g, ' ');
    if (!text) continue;
    if (item.kind === 'agent' && item.agentTask?.assistantReply) {
      lines.push(`助手：${item.agentTask.assistantReply.trim()}`);
      continue;
    }
    lines.push(`${item.role === 'user' ? '用户' : '助手'}：${text}`);
  }
  let block = lines.join('\n');
  if (block.length > maxChars) {
    block = block.slice(block.length - maxChars);
  }
  return block;
}

/** 当前轮次之前的历史（排除刚 push 的用户消息） */
export function priorMessagesForContext(messages: AiChatMessage[]): AiChatMessage[] {
  if (messages.length <= 1) return [];
  return messages.slice(0, -1);
}

export type AiInputMode = 'auto' | 'chat' | 'agent';

export const AI_INPUT_MODE_OPTIONS = [
  { label: '自动', value: 'auto' as const },
  { label: '阅读', value: 'chat' as const },
  { label: '操控', value: 'agent' as const },
];
