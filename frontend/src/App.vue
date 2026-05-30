<template>
  <div class="app-shell">
    <header class="hero-card">
      <p class="eyebrow">Noto · 知微</p>
      <h1>记录知识，理解知识，形成行动闭环</h1>
      <p class="subtitle">
        这是一个面向知识管理与智能辅助场景的前端启动页，后续将承载笔记、搜索、AI、待办与提醒等核心能力。
      </p>
      <div class="actions">
        <a-button type="primary">开始使用</a-button>
        <a-button>查看文档</a-button>
      </div>
    </header>

    <section class="grid">
      <div class="feature-card">
        <h2>统一布局</h2>
        <p>左侧导航、中间内容、右侧 AI/待办辅助面板的三栏结构。</p>
      </div>
      <div class="feature-card">
        <h2>统一请求</h2>
        <p>后续会接入 Axios 封装、Token 注入与 TraceId 透传。</p>
      </div>
      <div class="feature-card">
        <h2>统一状态</h2>
        <p>使用 Pinia 管理用户、笔记、搜索、AI 会话与待办状态。</p>
      </div>
    </section>
  </div>
</template>
