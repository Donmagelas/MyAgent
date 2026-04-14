import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || ''

export const apiClient = axios.create({
  baseURL,
  timeout: 30000
})

export function buildAuthHeaders(token) {
  if (!token) {
    return {}
  }
  return {
    Authorization: `Bearer ${token}`
  }
}

export async function extractHttpError(error) {
  if (error?.response?.data?.message) {
    return error.response.data.message
  }
  if (typeof error?.response?.data === 'string' && error.response.data.trim()) {
    return error.response.data
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '请求失败'
}

export async function postSseStream({ url, token, body, signal, onEvent }) {
  const response = await fetch(`${baseURL}${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...buildAuthHeaders(token)
    },
    body: JSON.stringify(body),
    signal
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `请求失败：${response.status}`)
  }

  if (!response.body) {
    throw new Error('响应流为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const frames = buffer.split(/\r?\n\r?\n/)
    buffer = frames.pop() ?? ''
    for (const frame of frames) {
      emitFrame(frame, onEvent)
    }
  }

  if (buffer.trim()) {
    emitFrame(buffer, onEvent)
  }
}

function emitFrame(frame, onEvent) {
  const lines = frame.split(/\r?\n/)
  const dataLines = []
  let eventName = ''

  for (const rawLine of lines) {
    const line = rawLine.trimEnd()
    if (!line) {
      continue
    }
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (!dataLines.length) {
    return
  }

  const payload = JSON.parse(dataLines.join('\n'))
  onEvent({
    event: eventName || payload.type,
    data: payload
  })
}
