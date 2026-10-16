export interface Conversation {
  id: number
  title: string
  model: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  tokensPrompt?: number | null
  tokensCompletion?: number | null
  ttftMs?: number | null
  sources?: string[]
}

export interface Preset {
  id: number
  name: string
  systemPrompt: string
}

export interface Stats {
  messageCount: number
  promptTokens: number
  completionTokens: number
  avgTtftMs: number
}

export async function fetchConversations(): Promise<Conversation[]> {
  const resp = await fetch('/api/conversations')
  if (!resp.ok) throw new Error(`会话列表失败 HTTP ${resp.status}`)
  return resp.json()
}

export async function fetchMessages(conversationId: number): Promise<Message[]> {
  const resp = await fetch(`/api/conversations/${conversationId}/messages`)
  if (!resp.ok) throw new Error(`消息列表失败 HTTP ${resp.status}`)
  return resp.json()
}

export async function fetchPresets(): Promise<Preset[]> {
  const resp = await fetch('/api/presets')
  if (!resp.ok) throw new Error(`预设列表失败 HTTP ${resp.status}`)
  return resp.json()
}

export async function fetchStats(): Promise<Stats> {
  const resp = await fetch('/api/stats')
  if (!resp.ok) throw new Error(`统计失败 HTTP ${resp.status}`)
  return resp.json()
}

export interface ChatHandlers {
  onMeta?: (conversationId: number) => void
  onSources?: (sources: string[]) => void
  onToken?: (token: string) => void
  onError?: (message: string) => void
  onDone?: (stopped: boolean) => void
}

/** POST 的 SSE 不能用 EventSource，用 fetch + ReadableStream 自己解；signal 用于 Abort 停止 */
export async function streamChat(
  body: { conversationId?: number; presetId?: number; rag?: boolean; message: string },
  handlers: ChatHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let stopped = false
  let resp: Response
  try {
    resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      handlers.onDone?.(true)
      return
    }
    handlers.onError?.(String(e))
    handlers.onDone?.(false)
    return
  }
  if (!resp.ok || !resp.body) {
    handlers.onError?.(`HTTP ${resp.status}`)
    handlers.onDone?.(false)
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let event = 'message'

  if (signal) {
    signal.addEventListener('abort', () => {
      stopped = true
      reader.cancel().catch(() => {})
    })
  }

  const dispatch = (data: string) => {
    try {
      if (event === 'meta') handlers.onMeta?.(JSON.parse(data).conversationId)
      else if (event === 'sources') handlers.onSources?.(JSON.parse(data))
      else if (event === 'token') handlers.onToken?.(JSON.parse(data))
      else if (event === 'error') handlers.onError?.(JSON.parse(data))
    } catch { /* 忽略坏包 */ }
  }

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx: number
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dispatch(line.slice(5))
        }
        event = 'message'
      }
    }
  } catch (e) {
    if (!(e instanceof DOMException && e.name === 'AbortError')) {
      handlers.onError?.(String(e))
    }
  }
  handlers.onDone?.(stopped)
}
