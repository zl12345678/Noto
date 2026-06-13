import type { DriveFolder } from '../api/drive';

export interface DriveFolderTreeNode {
  key: string;
  title: string;
  folder: DriveFolder;
  children?: DriveFolderTreeNode[];
}

export function buildDriveFolderTree(folders: DriveFolder[]): DriveFolderTreeNode[] {
  const nodes = new Map<string, DriveFolderTreeNode>();
  folders.forEach((folder) => {
    nodes.set(folder.id, {
      key: `folder:${folder.id}`,
      title: folder.name,
      folder,
      children: [],
    });
  });

  const roots: DriveFolderTreeNode[] = [];
  folders.forEach((folder) => {
    const node = nodes.get(folder.id);
    if (!node) return;
    const parentId = folder.parentId ?? null;
    if (parentId && nodes.has(parentId)) {
      nodes.get(parentId)!.children!.push(node);
    } else {
      roots.push(node);
    }
  });

  const sortNodes = (items: DriveFolderTreeNode[]) => {
    items.sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'));
    items.forEach((item) => {
      if (item.children?.length) {
        sortNodes(item.children);
      } else {
        delete item.children;
      }
    });
  };
  sortNodes(roots);
  return roots;
}

export function driveFolderTreeToAntData(nodes: DriveFolderTreeNode[]) {
  return nodes.map((node) => ({
    key: node.key,
    title: `${node.title} (${node.folder.fileCount ?? 0})`,
    children: node.children?.length ? driveFolderTreeToAntData(node.children) : undefined,
  }));
}

export function flattenDriveFolderTree(
  nodes: DriveFolderTreeNode[],
  depth = 0,
): Array<{ folder: DriveFolder; depth: number }> {
  const result: Array<{ folder: DriveFolder; depth: number }> = [];
  nodes.forEach((node) => {
    result.push({ folder: node.folder, depth });
    if (node.children?.length) {
      result.push(...flattenDriveFolderTree(node.children, depth + 1));
    }
  });
  return result;
}

export function collectDriveFolderTreeKeys(nodes: DriveFolderTreeNode[]): string[] {
  const keys: string[] = [];
  nodes.forEach((node) => {
    keys.push(node.key);
    if (node.children?.length) {
      keys.push(...collectDriveFolderTreeKeys(node.children));
    }
  });
  return keys;
}

export function getDriveFolderPath(folderId: string, folders: DriveFolder[]): string {
  const byId = new Map(folders.map((folder) => [folder.id, folder]));
  const parts: string[] = [];
  let current = byId.get(folderId);
  const guard = new Set<string>();
  while (current && !guard.has(current.id)) {
    guard.add(current.id);
    parts.unshift(current.name);
    current = current.parentId ? byId.get(current.parentId) : undefined;
  }
  return parts.join(' / ');
}
