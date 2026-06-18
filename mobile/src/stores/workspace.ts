import { reactive } from 'vue';
import { listWorkspaces, type Workspace } from '../api/workspaces';

export const workspaceState = reactive({
  items: [] as Workspace[],
  loaded: false,
});

export async function refreshWorkspaces() {
  workspaceState.items = await listWorkspaces();
  workspaceState.loaded = true;
}

export function getWorkspaceName(id?: string | null) {
  if (!id) return '';
  return workspaceState.items.find((w) => String(w.id) === String(id))?.name || '';
}
