<script setup>
import { session } from '../session'
defineProps({ item: Object, busy: Boolean, threadDeleted: Boolean })
defineEmits(['like', 'reply', 'remove'])
</script>
<template>
  <article class="comment-item">
    <div class="comment-byline"><strong>{{ item.nickname }}</strong><small>{{ item.createdAt.replace('T', ' ') }} UTC</small></div>
    <p v-if="item.deleted" class="muted">评论已删除</p>
    <template v-else>
      <small v-if="item.replyToId" class="muted">回复 {{ item.replyToName }}</small>
      <p class="comment-body">{{ item.body }}</p>
      <div class="comment-actions">
        <el-button text :disabled="busy || !session.user" :aria-pressed="item.liked" @click="$emit('like', item)">{{ item.liked ? '取消赞' : '赞' }} · {{ item.likeCount }}</el-button>
        <el-button v-if="session.user && !threadDeleted" text :disabled="busy" @click="$emit('reply', item)">回复</el-button>
        <el-button v-if="item.mine && session.user" text type="danger" :disabled="busy" @click="$emit('remove', item)">删除</el-button>
      </div>
    </template>
  </article>
</template>
