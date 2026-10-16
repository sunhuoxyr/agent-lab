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
}

export interface Preset {
  id: number
  name: string
  systemPrompt: string
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

export interface ChatHandlers {
  onMeta?: (conversationId: number) => void
  onToken?: (token: string) => void
  onError?: (message: string) => void
  onDone?: () => void
}

/** POST 的 SSE 不能用 EventSource，用 fetch + ReadableStream 自己解 */
export async function streamChat(
  body: { conversationId?: number; presetId?: number; message: string },
  handlers: ChatHandlers,
): Promise<void> {
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!resp.ok || !resp.body) {
    handlers.onError?.(`HTTP ${resp.status}`)
    handlers.onDone?.()
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let event = 'message'

  const dispatch = (data: string) => {
    if (event === 'meta') {
      try {
        handlers.onMeta?.(JSON.parse(data).conversationId)
      } catch { /* 忽略坏包 */ }
    } else if (event === 'token') {
      try {
        handlers.onToken?.(JSON.parse(data))
      } catch { /* 忽略坏包 */ }
    } else if (event === 'error') {
      try {
        handlers.onError?.(JSON.parse(data))
      } catch {
        handlers.onError?.(data)
      }
    }
  }

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
  handlers.onDone?.()
}
