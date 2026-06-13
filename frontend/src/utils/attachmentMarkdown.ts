export function isImageAttachment(fileType?: string, fileName?: string): boolean {
  if (fileType?.startsWith('image/')) return true;
  const ext = fileName?.split('.').pop()?.toLowerCase() ?? '';
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext);
}

export function buildAttachmentMarkdown(file: {
  fileName: string;
  fileUrl: string;
  fileType?: string;
}): string {
  const label = file.fileName.replace(/\[/g, '\\[').replace(/\]/g, '\\]');
  if (isImageAttachment(file.fileType, file.fileName)) {
    return `\n![${label}](${file.fileUrl})\n`;
  }
  return `\n[${label}](${file.fileUrl})\n`;
}
