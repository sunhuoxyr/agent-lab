<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  fetchConversations,
  fetchMessages,
  fetchPresets,
  fetchStats,
  streamChat,
  type Conversation,
  type Message,
  type Preset,
  type Stats,
} from './api'

const md = new MarkdownIt({ breaks: true, linkify: true })

const conversations = ref<Conversation[]>([])
const presets = ref<Preset[]>([])
const stats = ref<Stats | null>(null)
const currentId = ref<number | null>(null)
const messages = ref<Message[]>([])
const input = ref('')
const streaming = ref(false)
const selectedPresetId = ref<number | null>(null)
const ragEnabled = ref(false)
const errorMsg = ref('')
const messageListEl = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null

const currentTitle = computed(
  () => conversations.value.find((c) => c.id === currentId.value)?.title ?? '新对话',
)

function renderMd(content: string): string {
  return md.render(content)
}

function metricsOf(m: Message): string {
  const parts: string[] = []
  if (m.tokensPrompt || m.tokensCompletion) {
    parts.push(`${m.tokensPrompt ?? 0}+${m.tokensCompletion ?? 0} tok`)
  }
  if (m.ttftMs != null) {
    parts.push(`TTFT ${(m.ttftMs / 1000).toFixed(1)}s`)
  }
  return parts.join(' · ')
}

async function scrollToBottom() {
  await nextTick()
  messageListEl.value?.scrollTo({ top: messageListEl.value.scrollHeight })
}

async function loadConversations() {
  conversations.value = await fetchConversations()
}

async function loadStats() {
  try {
    stats.value = await fetchStats()
  } catch { /* 统计挂了不影响聊天 */ }
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

function stop() {
  abortController?.abort()
}

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return
  input.value = ''
  errorMsg.value = ''
  streaming.value = true
  abortController = new AbortController()

  messages.value.push({ id: Date.now(), conversationId: currentId.value ?? 0, role: 'user', content: text })
  const assistant: Message = {
    id: Date.now() + 1,
    conversationId: currentId.value ?? 0,
    role: 'assistant',
    content: '',
    sources: [],
  }
  messages.value.push(assistant)
  await scrollToBottom()

  await streamChat(
    {
      conversationId: currentId.value ?? undefined,
      presetId: selectedPresetId.value ?? undefined,
      rag: ragEnabled.value || undefined,
      message: text,
    },
    {
      onMeta: (id) => {
        currentId.value = id
        assistant.conversationId = id
      },
      onSources: (sources) => {
        assistant.sources = sources
      },
      onToken: (token) => {
        assistant.content += token
        scrollToBottom()
      },
      onError: (msg) => {
        errorMsg.value = msg
      },
      onDone: async (stopped) => {
        if (stopped && !assistant.content) {
          assistant.content = '（已停止）'
        }
        streaming.value = false
        abortController = null
        await Promise.all([loadConversations(), loadStats()])
      },
    },
    abortController.signal,
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
    await Promise.all([
      loadConversations(),
      fetchPresets().then((p) => (presets.value = p)),
      loadStats(),
    ])
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
      <div v-if="stats" class="stats-footer">
        助手 {{ stats.messageCount }} 条 · token {{ stats.promptTokens }}+{{ stats.completionTokens }}
        <template v-if="stats.messageCount"> · 平均 TTFT {{ (stats.avgTtftMs / 1000).toFixed(1) }}s</template>
      </div>
    </aside>

    <main class="main">
      <header class="header">
        <span class="title">{{ currentTitle }}</span>
        <label class="rag-toggle" title="先检索知识库片段再回答（需已 reindex）">
          <input v-model="ragEnabled" type="checkbox" />
          知识库
        </label>
        <select v-model="selectedPresetId" class="preset-select">
          <option :value="null">无预设</option>
          <option v-for="p in presets" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
      </header>

      <div ref="messageListEl" class="messages">
        <div v-if="messages.length === 0" class="empty">说一句，开个头。</div>
        <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
          <template v-if="m.role === 'assistant'">
            <div class="assistant-col">
              <div class="bubble md" v-html="renderMd(m.content)"></div>
              <div v-if="m.sources && m.sources.length" class="sources">
                来源：<span v-for="s in m.sources" :key="s" class="source-item">{{ s }}</span>
              </div>
              <div v-if="metricsOf(m)" class="metrics">{{ metricsOf(m) }}</div>
            </div>
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
        <button v-if="streaming" class="stop-btn" @click="stop">停止</button>
        <button v-else :disabled="!input.trim()" @click="send">发送</button>
      </footer>
    </main>
  </div>
</template>
