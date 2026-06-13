import { completionRetro, getAiStatus } from '../api/ai';
import type { ReminderItem } from '../api/reminders';

export async function quickWriteCompletionRetro(item: ReminderItem): Promise<boolean> {
  if (!item.noteId) return false;

  try {
    const status = await getAiStatus();
    if (!status.enabled) {
      const { message } = await import('ant-design-vue');
      message.warning('AI 未启用，无法生成复盘');
      return false;
    }
  } catch {
    return false;
  }

  const { message } = await import('ant-design-vue');
  try {
    const result = await completionRetro(item.todoId, {
      append: true,
      scenario: 'reminder_due',
    });
    message.success(`复盘已写入「${result.noteTitle || '关联笔记'}」`);
    return true;
  } catch (error: any) {
    message.error(error?.message || '写入复盘失败');
    return false;
  }
}

export async function offerCompletionRetro(item: ReminderItem): Promise<void> {
  if (!item.noteId) return;

  try {
    const status = await getAiStatus();
    if (!status.enabled) return;
  } catch {
    return;
  }

  const { Modal, Input, message } = await import('ant-design-vue');
  const { h, ref } = await import('vue');

  let generatedLine = '';
  try {
    const result = await completionRetro(item.todoId, {
      append: false,
      scenario: 'completed',
    });
    generatedLine = result.line;
  } catch {
    generatedLine = `已完成「${item.todoTitle || '待办'}」。`;
  }

  const editable = ref(generatedLine);

  return new Promise<void>((resolve) => {
    Modal.confirm({
      title: '写一句复盘进原笔记？',
      width: 480,
      content: h('div', { style: 'margin-top:8px' }, [
        h(
          'p',
          { style: 'color:#667085;font-size:13px;margin-bottom:8px' },
          `将追加到「${item.noteTitle || '关联笔记'}」末尾，可编辑后再写入。`,
        ),
        h(Input.TextArea, {
          value: editable.value,
          rows: 3,
          maxlength: 120,
          showCount: true,
          'onUpdate:value': (value: string) => {
            editable.value = value;
          },
        }),
      ]),
      okText: '写入笔记',
      cancelText: '跳过',
      async onOk() {
        const text = editable.value.trim();
        if (!text) {
          message.warning('请输入复盘内容');
          return Promise.reject(new Error('empty'));
        }
        try {
          await completionRetro(item.todoId, { line: text, append: true, scenario: 'completed' });
          message.success('复盘已写入笔记');
        } catch (error: any) {
          message.error(error?.message || '写入失败');
          return Promise.reject(error);
        }
        resolve();
      },
      onCancel() {
        resolve();
      },
    });
  });
}
