import { useState, useEffect } from 'react'
import api from '../api/client'

interface Props {
  turnId: number | null
}

export default function AiBriefingPanel({ turnId }: Props) {
  const [briefing, setBriefing] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!turnId) return
    setLoading(true)
    setBriefing(null)
    api.get(`/game/turns/${turnId}/briefing`)
      .then(res => {
        setBriefing(res.data.briefing ?? res.data)
      })
      .catch(() => {
        setBriefing(null)
      })
      .finally(() => setLoading(false))
  }, [turnId])

  return (
    <div className="bg-white rounded-xl border border-gray-100 p-4 h-full flex flex-col">
      <div className="flex items-center gap-2 mb-3">
        <div className="w-6 h-6 bg-indigo-600 rounded-md flex items-center justify-center flex-shrink-0">
          <span className="text-white text-xs font-bold">나비</span>
        </div>
        <span className="font-bold text-gray-800 text-sm">나비 시장 동향 브리핑</span>
      </div>

      {loading ? (
        <div className="flex-1 space-y-2">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
            <div className="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
            <div className="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
            <span className="text-xs text-gray-400">시장 동향을 분석하고 있습니다...</span>
          </div>
          <div className="animate-pulse space-y-2">
            <div className="h-3 bg-gray-200 rounded w-full" />
            <div className="h-3 bg-gray-200 rounded w-5/6" />
            <div className="h-3 bg-gray-200 rounded w-4/5" />
            <div className="h-3 bg-gray-200 rounded w-full" />
            <div className="h-3 bg-gray-200 rounded w-3/4" />
            <div className="h-3 bg-gray-200 rounded w-5/6" />
          </div>
        </div>
      ) : briefing ? (
        <div className="flex-1 overflow-y-auto">
          <div className="text-xs text-gray-700 leading-relaxed whitespace-pre-wrap">
            {briefing}
          </div>
        </div>
      ) : (
        <div className="flex-1 flex items-center justify-center">
          <p className="text-xs text-gray-400 text-center">
            {turnId ? '나비 브리핑 데이터를 불러올 수 없습니다.' : '턴 정보를 기다리는 중...'}
          </p>
        </div>
      )}

      <div className="mt-3 pt-3 border-t border-gray-100">
        <p className="text-xs text-gray-400 leading-tight">
          이 브리핑은 교육 목적의 뉴스 정리이며, 투자 조언이 아닙니다.
        </p>
      </div>
    </div>
  )
}
