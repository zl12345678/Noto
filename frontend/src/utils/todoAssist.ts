import dayjs, { type Dayjs } from 'dayjs';
import type { TodoItem } from '../api/todos';

const VAGUE_PATTERNS =
  /^(跟进|处理|完善|优化|整理|考虑|推进|研究|了解|看看|想想|安排|准备)|跟进[:：]|待补充|^todo$/i;

/** 标题是否偏模糊、适合 AI 拆解 */
export function isVagueTodoTitle(title: string): boolean {
  const text = title.trim();
  if (!text) return true;
  if (text.length < 6) return true;
  if (VAGUE_PATTERNS.test(text)) return true;
  if (text.length <= 14 && !/\d/.test(text) && !/[：:（(]/.test(text)) {
    return true;
  }
  return false;
}

export const OVERDUE_ACTION_LABEL: Record<string, string> = {
  reschedule: '改期',
  breakdown: '拆小',
  archive: '归档',
  complete: '完成',
};

/** 是否今日到期（含已逾期） */
export function isDueToday(item: Pick<TodoItem, 'dueAt'>): boolean {
  if (!item.dueAt) return false;
  const due = dayjs(item.dueAt);
  const today = dayjs();
  return due.isSame(today, 'day') || due.isBefore(today, 'day');
}

/** 推迟到明天上午 9 点 */
export function buildPostponedDueAt(item: Pick<TodoItem, 'dueAt'>): string {
  const base = item.dueAt ? dayjs(item.dueAt) : dayjs().endOf('day');
  return base.add(1, 'day').hour(9).minute(0).second(0).millisecond(0).toISOString();
}

export type ReminderPreset = '1h' | 'tonight' | 'tomorrow' | 'before_due';

/** 根据待办智能推荐提醒触发时间 */
export function suggestReminderTriggerAt(item: Pick<TodoItem, 'dueAt' | 'title'>): Dayjs {
  const now = dayjs();
  if (item.dueAt) {
    const due = dayjs(item.dueAt);
    if (due.isAfter(now)) {
      const beforeDue = due.subtract(1, 'hour');
      if (beforeDue.isAfter(now)) return beforeDue;
      return due;
    }
    return now.add(1, 'hour');
  }
  const tonight = now.hour(20).minute(0).second(0).millisecond(0);
  if (tonight.isAfter(now)) return tonight;
  return now.add(1, 'hour');
}

export function buildReminderPresetTime(
  preset: ReminderPreset,
  item: Pick<TodoItem, 'dueAt'>,
): Dayjs | null {
  const now = dayjs();
  if (preset === '1h') return now.add(1, 'hour');
  if (preset === 'tonight') {
    const tonight = now.hour(20).minute(0).second(0).millisecond(0);
    return tonight.isAfter(now) ? tonight : now.add(1, 'hour');
  }
  if (preset === 'tomorrow') {
    return now.add(1, 'day').hour(9).minute(0).second(0).millisecond(0);
  }
  if (preset === 'before_due' && item.dueAt) {
    const due = dayjs(item.dueAt);
    const beforeDue = due.subtract(1, 'hour');
    return beforeDue.isAfter(now) ? beforeDue : due.isAfter(now) ? due : null;
  }
  return null;
}
