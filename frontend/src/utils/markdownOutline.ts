export interface MarkdownHeadingItem {
  level: number;
  title: string;
  /** URL 锚点（与预览 heading id 对齐） */
  slug: string;
  /** 标题行在正文中的起始字符偏移（用于编辑区定位） */
  start: number;
  /** 文档内第几个标题（0-based） */
  index: number;
}

const HEADING_LINE = /^(#{1,6})\s+(.+?)\s*$/;

export function slugifyHeadingTitle(title: string): string {
  const base = title
    .trim()
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fff\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  return base || 'section';
}

/**
 * 从 Markdown 源码提取标题（按文档顺序）。
 */
export function parseMarkdownHeadings(content: string): MarkdownHeadingItem[] {
  if (!content) return [];
  const items: MarkdownHeadingItem[] = [];
  const slugCount = new Map<string, number>();
  let offset = 0;
  let index = 0;
  const lines = content.split('\n');
  for (const line of lines) {
    const m = line.match(HEADING_LINE);
    if (m) {
      const level = m[1].length;
      const title = m[2].replace(/\s+#+\s*$/, '').trim();
      let slug = slugifyHeadingTitle(title);
      const n = (slugCount.get(slug) ?? 0) + 1;
      slugCount.set(slug, n);
      if (n > 1) slug = `${slug}-${n}`;
      items.push({ level, title, slug, start: offset, index });
      index += 1;
    }
    offset += line.length + 1;
  }
  return items;
}

export function filterHeadingsByMaxLevel(
  items: MarkdownHeadingItem[],
  maxLevel: number,
): MarkdownHeadingItem[] {
  const cap = Math.min(6, Math.max(1, maxLevel));
  return items.filter((item) => item.level <= cap);
}

export function filterHeadingsByKeyword(
  items: MarkdownHeadingItem[],
  keyword: string,
): MarkdownHeadingItem[] {
  const q = keyword.trim().toLowerCase();
  if (!q) return items;
  return items.filter((item) => item.title.toLowerCase().includes(q));
}

/** 按字符偏移找当前应高亮的大纲项（最后一个 start <= offset） */
export function activeHeadingIndexAtOffset(
  items: MarkdownHeadingItem[],
  offset: number,
): number {
  if (!items.length || offset < 0) return -1;
  let lo = 0;
  let hi = items.length - 1;
  let ans = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (items[mid].start <= offset) {
      ans = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return ans >= 0 ? items[ans].index : -1;
}

export function findHeadingBySlug(
  items: MarkdownHeadingItem[],
  slug: string,
): MarkdownHeadingItem | undefined {
  if (!slug) return undefined;
  const decoded = decodeURIComponent(slug);
  const norm = (s: string) => s.toLowerCase();
  const target = norm(decoded);
  return items.find(
    (h) =>
      h.slug === decoded ||
      h.slug === slug ||
      norm(h.slug) === target ||
      slugifyHeadingTitle(h.title) === target,
  );
}

export const OUTLINE_HEADING_TEMPLATE = `# 一级标题

## 二级标题

正文从这里开始…

`;

export interface MarkdownSectionChunk {
  key: string;
  markdown: string;
  /** 本节第一个标题在全文中的 index（若无标题则为 -1） */
  firstHeadingIndex: number;
}

/** 大文档按一级标题分块，便于懒渲染 */
export function splitMarkdownByTopHeadings(content: string): MarkdownSectionChunk[] {
  if (!content) {
    return [{ key: '0', markdown: '', firstHeadingIndex: -1 }];
  }
  const lines = content.split('\n');
  const chunks: MarkdownSectionChunk[] = [];
  let buf: string[] = [];
  let chunkStart = 0;
  let headingIndex = 0;
  let firstInChunk = -1;

  const flush = (key: string) => {
    const md = buf.join('\n');
    if (md.trim() || chunks.length === 0) {
      chunks.push({ key, markdown: md, firstHeadingIndex: firstInChunk });
    }
    buf = [];
    firstInChunk = -1;
  };

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const isH1 = /^#\s+/.test(line);
    if (isH1 && buf.length > 0) {
      flush(String(chunkStart));
      chunkStart += 1;
    }
    if (/^(#{1,6})\s+/.test(line)) {
      if (firstInChunk < 0) firstInChunk = headingIndex;
      headingIndex += 1;
    }
    buf.push(line);
  }
  flush(String(chunkStart));
  return chunks.length ? chunks : [{ key: '0', markdown: content, firstHeadingIndex: -1 }];
}