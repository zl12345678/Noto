<template>
  <a-modal
    v-model:open="open"
    title="键盘快捷键"
    :footer="null"
    width="520px"
    class="shortcuts-modal"
  >
    <p class="shortcuts-intro">在输入框外按下 <kbd>?</kbd> 可再次打开本面板。</p>
    <table class="shortcuts-table">
      <tbody>
        <tr v-for="row in rows" :key="row.label">
          <th>{{ row.label }}</th>
          <td><kbd v-for="key in row.keys" :key="key">{{ key }}</kbd></td>
        </tr>
      </tbody>
    </table>
  </a-modal>
</template>

<script setup lang="ts">
const open = defineModel<boolean>('open', { default: false });

const rows = [
  { label: '命令面板（搜文档 / 问 AI / 办事）', keys: ['Ctrl', 'K'] },
  { label: '返回上一个模块标签', keys: ['Alt', '←'] },
  { label: '标签右键菜单（固定 / 关闭其他）', keys: ['右键'] },
  { label: '保存当前文档（笔记编辑页）', keys: ['Ctrl', 'S'] },
  { label: '快捷键帮助', keys: ['?'] },
  { label: '关闭弹窗 / 命令面板 / 侧栏抽屉', keys: ['Esc'] },
  { label: '命令面板内移动选项', keys: ['↑', '↓'] },
  { label: '命令面板内确认', keys: ['Enter'] },
];
</script>

<style scoped>
.shortcuts-intro {
  margin: 0 0 16px;
  color: rgba(0, 0, 0, 0.55);
  font-size: 13px;
}

.shortcuts-table {
  width: 100%;
  border-collapse: collapse;
}

.shortcuts-table th,
.shortcuts-table td {
  padding: 10px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  text-align: left;
  vertical-align: middle;
}

.shortcuts-table th {
  width: 62%;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
}

.shortcuts-table td {
  white-space: nowrap;
}

kbd {
  display: inline-block;
  margin-right: 4px;
  padding: 2px 8px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.04);
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
