import { message } from 'ant-design-vue';
import type { Router } from 'vue-router';
import { createNote } from '../api/notes';

export function normalizeGapTitle(gap: string) {
  const trimmed = gap.trim();
  const stripped = trimmed
    .replace(/^建议新建[：:]\s*/u, '')
    .replace(/^关于[「「]/u, '')
    .replace(/[」」]的背景笔记$/u, '')
    .replace(/^现有资料匹配度较低，建议补充[「「]/u, '')
    .replace(/[」」]相关记录$/u, '')
    .trim();
  if (!stripped) return trimmed.slice(0, 40);
  return stripped.length > 48 ? `${stripped.slice(0, 48)}…` : stripped;
}

export async function createNoteFromGap(gap: string, workspaceId: string, router: Router) {
  if (!workspaceId) {
    message.warning('请先选择知识库');
    return;
  }
  const title = normalizeGapTitle(gap);
  try {
    const created = await createNote({
      workspaceId,
      title,
      content: `> 知识库缺口：${gap.trim()}\n\n## 待补充\n\n- \n`,
    });
    message.success('已创建笔记草稿');
    router.push({
      path: `/notes/${created.id}`,
      query: { workspace: workspaceId, from: 'ai-gap' },
    });
  } catch (error: any) {
    message.error(error?.message || '创建笔记失败');
  }
}

export function sortReferencesByRelevance<T extends { relevanceScore?: number | null }>(refs: T[]) {
  return [...refs].sort((a, b) => (b.relevanceScore ?? 0) - (a.relevanceScore ?? 0));
}
