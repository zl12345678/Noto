import type { TodoItem } from '../api/todos';

/** 看板展示：按截止时间升序，无截止时间排后 */
export function sortTodosByDueAt(items: TodoItem[]): TodoItem[] {
  return [...items].sort((left, right) => {
    if (!left.dueAt && !right.dueAt) return 0;
    if (!left.dueAt) return 1;
    if (!right.dueAt) return -1;
    return new Date(left.dueAt).getTime() - new Date(right.dueAt).getTime();
  });
}
