const MEETING_TITLE_PATTERN = /会议|纪要|meet(?:ing)?|讨论|sync|站会|评审|复盘会|周会|standup/i;
const MEETING_SECTION_PATTERN = /^##\s*(待办|决策|结论|行动|action|todo|next steps)/im;

export function isMeetingLikeNote(title: string, content: string) {
  const trimmedTitle = (title || '').trim();
  const trimmedContent = (content || '').trim();
  if (trimmedContent.length < 120) return false;
  if (MEETING_TITLE_PATTERN.test(trimmedTitle)) return true;
  if (MEETING_SECTION_PATTERN.test(trimmedContent)) return true;
  return false;
}

export function autoExtractSessionKey(noteId: string) {
  return `noto-auto-extract-done-${noteId}`;
}
