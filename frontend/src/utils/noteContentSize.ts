/** 超过该字符数关闭编辑器分栏预览（显著减轻卡顿） */
export const LARGE_NOTE_PREVIEW_OFF_CHARS = 80_000;

/** 历史阈值：曾用于关闭阅读标题折叠；现阅读模式始终保留折叠，仅作参考 */
export const LARGE_NOTE_READ_LIGHT_CHARS = 120_000;

/** 超过该字符数显示性能提示条 */
export const HUGE_NOTE_WARN_CHARS = 200_000;

export function noteContentCharCount(content?: string | null): number {
  return content?.length ?? 0;
}

export function shouldDisableEditorPreview(content?: string | null): boolean {
  return noteContentCharCount(content) >= LARGE_NOTE_PREVIEW_OFF_CHARS;
}

export function shouldUseLightReadPreview(content?: string | null): boolean {
  return noteContentCharCount(content) >= LARGE_NOTE_READ_LIGHT_CHARS;
}

export function isHugeNoteContent(content?: string | null): boolean {
  return noteContentCharCount(content) >= HUGE_NOTE_WARN_CHARS;
}

export function formatContentSizeHint(chars: number): string {
  if (chars >= 1_000_000) return `${(chars / 1_000_000).toFixed(1)}M 字`;
  if (chars >= 10_000) return `${Math.round(chars / 1000)}k 字`;
  return `${chars} 字`;
}