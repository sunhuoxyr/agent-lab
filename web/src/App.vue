<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  fetchConversations,
  fetchMessages,
  fetchPresets,
  streamChat,
  type Conversation,
  type Message,
  type Preset,
} from './api'

const md = new MarkdownIt({ breaks: true, linkify: true })

const conversations = ref<Conversation[]>([])
const presets = ref<Preset[]>([])
const currentId = ref<number | null>(null)
const messages = ref<Message[]>([])
const input = ref('')
const streaming = ref(false)
const selectedPresetId = ref<number | null>(null)
const errorMsg = ref('')
const messageListEl = ref<HTMLElement | null>(null)

const currentTitle = computed(
  () => conversations.value.find((c) => c.id === currentId.value)?.title ?? '新对话',
)

function renderMd(content: string): string {
  return md.render(content)
}

async function scrollToBottom() {
  await nextTick()
  messageListEl.value?.scrollTo({ top: messageListEl.value.scrollHeight })
}

async function loadConversations() {
  conversations.value = await fetchConversations()
}

async function openConversation(id: number) {
  currentId.value = id
  errorMsg.value = ''
  messages.value = await fetchMessages(id)
  await scrollToBottom()
}

function newChat() {
  currentId.value = null
  messages.value = []
  errorMsg.value = ''
}

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return
  input.value = ''
  errorMsg.value = ''
  streaming.value = true

  messages.value.push({ id: Date.now(), conversationId: currentId.value ?? 0, role: 'user', content: text })
  const assistant: Message = {
    id: Date.now() + 1,
    conversationId: currentId.value ?? 0,
    role: 'assistant',
    content: '',
  }
  messages.value.push(assistant)
  await scrollToBottom()

  await streamChat(
    { conversationId: currentId.value ?? undefined, presetId: selectedPresetId.value ?? undefined, message: text },
    {
      onMeta: (id) => {
        currentId.value = id
        assistant.conversationId = id
      },
      onToken: (token) => {
        assistant.content += token
        scrollToBottom()
      },
      onError: (msg) => {
        errorMsg.value = msg
      },
      onDone: async () => {
        streaming.value = false
        await loadConversations()
      },
    },
  )
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadConversations(), fetchPresets().then((p) => (presets.value = p))])
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <button class="new-chat" @click="newChat">+ 新对话</button>
      <div
        v-for="c in conversations"
        :key="c.id"
        class="conv-item"
        :class="{ active: c.id === currentId }"
        @click="openConversation(c.id)"
      >
        {{ c.title }}
      </div>
    </aside>

    <main class="main">
      <header class="header">
        <span class="title">{{ currentTitle }}</span>
        <select v-model="selectedPresetId" class="preset-select">
          <option :value="null">无预设</option>
          <option v-for="p in presets" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
      </header>

      <div ref="messageListEl" class="messages">
        <div v-if="messages.length === 0" class="empty">说一句，开个头。</div>
        <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
          <template v-if="m.role === 'assistant'">
            <div class="bubble md" v-html="renderMd(m.content)"></div>
          </template>
          <template v-else>
            <div class="bubble">{{ m.content }}</div>
          </template>
        </div>
      </div>

      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>

      <footer class="composer">
        <textarea
          v-model="input"
          rows="2"
          placeholder="Enter 发送，Shift+Enter 换行"
          @keydown="onKeydown"
        ></textarea>
        <button :disabled="streaming || !input.trim()" @click="send">
          {{ streaming ? '…' : '发送' }}
        </button>
      </footer>
    </main>
  </div>
</template>
