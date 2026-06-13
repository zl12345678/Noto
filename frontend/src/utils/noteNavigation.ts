export function buildNoteRouteQuery(options: {
  from?: string;
  workspaceId?: string | null;
  keyword?: string;
  offsetStart?: number | null;
  offsetEnd?: number | null;
}): Record<string, string> {
  const query: Record<string, string> = {};
  if (options.from) query.from = options.from;
  if (options.workspaceId) query.workspace = String(options.workspaceId);
  const keyword = options.keyword?.trim();
  if (keyword) query.q = keyword;
  if (options.offsetStart != null) query.start = String(options.offsetStart);
  if (options.offsetEnd != null) query.end = String(options.offsetEnd);
  return query;
}
