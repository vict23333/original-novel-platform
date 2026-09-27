<script setup>
import BookCover from './BookCover.vue'
import { countText, dateText } from '../discovery'
defineProps({ book: { type: Object, required: true }, rank: Number })
</script>
<template>
  <router-link class="library-book discovery-book" :to="`/books/${book.id}`">
    <span v-if="rank" class="rank-number" :class="{ leading: rank <= 3 }">{{ String(rank).padStart(2, '0') }}</span>
    <BookCover :title="book.title" :category-id="book.categoryId" />
    <div class="discovery-copy">
      <span class="book-category">{{ book.categoryName }} · {{ book.serialStatus === 'COMPLETED' ? '完结' : '连载' }}</span>
      <h3 :title="book.title">{{ book.title }}</h3><p class="muted" :title="`${book.authorName} 著`">{{ book.authorName }} 著</p>
      <p class="book-blurb">{{ book.introduction }}</p>
      <p class="book-metrics">本周 {{ countText(book.weekReads) }} 阅读 · {{ countText(book.collections) }} 收藏</p>
      <p class="book-update">{{ book.chapterCount }} 章 · {{ countText(book.wordCount) }} 字</p>
      <p v-if="book.latestChapterTitle" class="book-update book-latest" :title="`最新：${book.latestChapterTitle} · ${dateText(book.lastUpdatedAt)}`">最新：{{ book.latestChapterTitle }}</p>
      <time v-if="book.lastUpdatedAt" class="book-date">{{ dateText(book.lastUpdatedAt) }}</time>
    </div>
  </router-link>
</template>
