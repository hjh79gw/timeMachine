import { useState, useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/client'
import { ChatMessage } from '../types'

interface Props {
  gameId: number
}

export default function FamilyChat({ gameId }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  // 채팅 히스토리 로드
  useEffect(() => {
    api.get(`/game/${gameId}/chat`)
      .then(res => setMessages(res.data))
      .catch(() => {})
  }, [gameId])

  // WebSocket STOMP 연결
  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`http://localhost:8082/ws`),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/game/${gameId}/chat`, msg => {
          try {
            const data = JSON.parse(msg.body) as ChatMessage
            setMessages(prev => [...prev, data])
          } catch {}
        })
      },
      onDisconnect: () => setConnected(false),
    })
    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [gameId])

  // 자동 스크롤
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = async () => {
    const text = input.trim()
    if (!text) return
    setInput('')

    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: `/app/game/${gameId}/chat`,
        body: JSON.stringify({ message: text }),
      })
    } else {
      // fallback to REST
      try {
        await api.post(`/game/${gameId}/chat`, { message: text })
      } catch {}
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  return (
    <div className="bg-white rounded-xl border border-gray-100 flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 bg-green-600 rounded-md flex items-center justify-center flex-shrink-0">
            <span className="text-white text-xs font-bold">채</span>
          </div>
          <span className="font-bold text-gray-800 text-sm">가족 채팅</span>
        </div>
        <div className={`flex items-center gap-1`}>
          <div className={`w-2 h-2 rounded-full ${connected ? 'bg-green-400' : 'bg-gray-300'}`} />
          <span className="text-xs text-gray-400">{connected ? '연결됨' : '연결 중...'}</span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-2 min-h-0">
        {messages.length === 0 ? (
          <div className="flex items-center justify-center h-full">
            <p className="text-xs text-gray-400">아직 채팅이 없습니다</p>
          </div>
        ) : (
          messages.map((msg, idx) => (
            <div key={msg.id ?? idx} className="flex flex-col gap-0.5">
              <div className="flex items-center gap-1">
                <span className="text-xs font-bold text-indigo-600">{msg.userName}</span>
                <span className="text-xs text-gray-300">
                  {msg.sentAt ? new Date(msg.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : ''}
                </span>
              </div>
              <div className="bg-gray-100 rounded-lg px-3 py-1.5 text-xs text-gray-700 max-w-full break-words">
                {msg.message}
              </div>
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>

      <div className="px-3 py-2 border-t border-gray-100 flex gap-2">
        <input
          type="text"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="메시지 입력..."
          className="flex-1 border border-gray-200 rounded-lg px-3 py-1.5 text-xs focus:outline-none focus:border-indigo-400"
        />
        <button
          onClick={sendMessage}
          disabled={!input.trim()}
          className="px-3 py-1.5 bg-indigo-600 text-white text-xs font-bold rounded-lg disabled:opacity-40 hover:bg-indigo-700 transition"
        >
          전송
        </button>
      </div>
    </div>
  )
}
