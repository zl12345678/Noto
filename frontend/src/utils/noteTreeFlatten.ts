import type { NoteTreeNode } from '../components/note/NoteDocTree.vue';

export const VIRTUAL_TREE_THRESHOLD = 80;
export const VIRTUAL_TREE_ROW_HEIGHT = 32;

export type FlatTreeRow = {
  node: NoteTreeNode;
  depth: number;
};

export function flattenNoteTree(
  nodes: NoteTreeNode[],
  expandedKeys: string[],
  depth = 0,
): FlatTreeRow[] {
  const rows: FlatTreeRow[] = [];
  for (const node of nodes) {
    rows.push({ node, depth });
    const children = node.children;
    if (children?.length && expandedKeys.includes(node.key)) {
      rows.push(...flattenNoteTree(children, expandedKeys, depth + 1));
    }
  }
  return rows;
}

export function countFlatTreeNodes(nodes: NoteTreeNode[], expandedKeys: string[]) {
  return flattenNoteTree(nodes, expandedKeys).length;
}
