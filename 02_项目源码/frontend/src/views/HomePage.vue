<script setup>
import { session } from '../session'
</script>
<template>
  <section class="home-hero">
    <div class="hero-copy"><p class="eyebrow">故事的下一页，等你开启</p><h1>在文字之间，<br>遇见新的世界。</h1><p class="hero-description">为热爱故事的读者，也为正在写下故事的你。<br>在墨间，开启属于自己的阅读与创作旅程。</p><div class="hero-actions"><router-link class="solid-link large" :to="session.user ? '/profile' : '/register'">{{ session.user ? '进入个人中心' : '创建我的账号' }} <span aria-hidden="true">↗</span></router-link><router-link v-if="!session.user" class="quiet-link" to="/login">已有账号，去登录 →</router-link></div><p class="release-note">首个开发版本已开放注册、登录和个人资料维护。</p></div>
    <div class="book-scene" aria-hidden="true"><div class="scene-circle"></div><div class="book book-back"><span>故事<br>始于<br>一念</span></div><div class="book book-front"><span class="book-label">墨 间 · 创 作 手 记</span><strong>每一个故事<br>都有自己的<br>回响。</strong><div class="book-rule"></div><small>写下第一行，让想象生长。</small></div><span class="scene-caption">A PLACE FOR YOUR STORIES</span></div>
  </section>
  <section class="feature-section"><div class="section-heading"><div><p class="eyebrow">从一个账号开始</p><h2>选择你的故事身份</h2></div><span class="outline-label">当前可体验</span></div><div class="feature-grid"><article><span class="feature-number">01 / 读者</span><h3>建立你的阅读身份</h3><p>注册读者账号，维护昵称和个人简介。阅读、书架与评论将在后续版本开放。</p></article><article><span class="feature-number">02 / 作者</span><h3>准备你的创作名片</h3><p>作者同时拥有读者能力，可进入作者工作台。作品发布与 AI 辅助创作正在规划中。</p></article><article><span class="feature-number">03 / 平台管理</span><h3>让内容有序生长</h3><p>管理账号由平台单独初始化。审核、用户管理及分类维护将在后续版本实现。</p></article></div></section>
</template>
