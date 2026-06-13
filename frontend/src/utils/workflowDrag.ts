import type { Note } from '../api/notes';
import type { TodoItem } from '../api/todos';

export type WorkflowStageKey = 'notes' | 'action' | 'parallel' | 'done';

export type WorkflowDragType = 'note' | 'todo';

export const WORKFLOW_DRAG_MIME = 'application/x-noto-workflow';

export interface WorkflowDragPayload {
  type: WorkflowDragType;
  fromStage: WorkflowStageKey;
  note?: Pick<Note, 'id' | 'title' | 'workspaceId'>;
  todo?: TodoItem;
}

export interface WorkflowDropEvent extends WorkflowDragPayload {
  toStage: WorkflowStageKey;
}

/** 允许的跨列拖拽：文档→待办队列，待办↔进行中，进行中→完成 */
export function canDropOnStage(
  fromStage: WorkflowStageKey,
  toStage: WorkflowStageKey,
  type: WorkflowDragType,
): boolean {
  if (fromStage === toStage) return false;

  if (type === 'note') {
    return fromStage === 'notes' && toStage === 'action';
  }

  if (fromStage === 'action' && toStage === 'parallel') return true;
  if (fromStage === 'parallel' && toStage === 'action') return true;
  if (fromStage === 'parallel' && toStage === 'done') return true;
  return false;
}

export function isStageDropTarget(stageKey: WorkflowStageKey): boolean {
  return stageKey === 'action' || stageKey === 'parallel' || stageKey === 'done';
}

export function parseWorkflowDragPayload(raw: string): WorkflowDragPayload | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as WorkflowDragPayload;
    if (!parsed?.type || !parsed?.fromStage) return null;
    return parsed;
  } catch {
    return null;
  }
}
