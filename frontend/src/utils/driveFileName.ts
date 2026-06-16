import type { DriveFile } from '../api/drive';

type FileNameSource = {
  fileName?: string | null;
  file_name?: string | null;
  storageKey?: string | null;
  fileUrl?: string | null;
};

/** 从 storageKey 末段解析「uuid-原名」中的原名 */
function nameFromStorageKey(storageKey?: string | null): string {
  if (!storageKey?.trim()) return '';
  const segment = storageKey.split('/').filter(Boolean).pop() || '';
  const match = segment.match(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-(.+)$/i,
  );
  if (match?.[1]) return decodeURIComponent(match[1]);
  return segment;
}

/** 网盘/附件列表展示名（兼容 snake_case、空 fileName） */
export function resolveDriveFileDisplayName(source: FileNameSource): string {
  const direct =
    (typeof source.fileName === 'string' && source.fileName.trim()) ||
    (typeof source.file_name === 'string' && source.file_name.trim()) ||
    '';
  if (direct) return direct;

  const fromKey = nameFromStorageKey(source.storageKey);
  if (fromKey) return fromKey;

  return '未命名文件';
}

/** 列表接口归一化，保证 fileName 必有值 */
export function normalizeDriveFile(raw: DriveFile & { file_name?: string }): DriveFile {
  const fileName = resolveDriveFileDisplayName(raw);
  return { ...raw, fileName };
}