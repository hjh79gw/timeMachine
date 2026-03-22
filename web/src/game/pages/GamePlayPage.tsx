import { useState, useEffect, useCallback } from 'react'
import api from '../api/client'
import { GameTurn, Balance, Holding, Ranking } from '../types'
import AiBriefingPanel from '../components/AiBriefingPanel'
import GameChart from '../components/GameChart'
import GameOrderPanel from '../components/GameOrderPanel'
import FamilyChat from '../components/FamilyChat'
import HoldingsSummary from '../components/HoldingsSummary'
import RankingPanel from '../components/RankingPanel'

interface Props {
  gameId: number
  seedMoney: number
  startDate: string
  onFinish: (gameId: number) => void
}

export default function GamePlayPage({ gameId, seedMoney, startDate, onFinish }: Props) {
  const [turn, setTurn] = useState<GameTurn | null>(null)
  const [balance, setBalance] = useState<Balance | null>(null)
  const [holdings, setHoldings] = useState<Holding[]>([])
  const [rankings, setRankings] = useState<Ranking[]>([])
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null)
  const [moving, setMoving] = useState(false)
  const [finishing, setFinishing] = useState(false)
  const [moveError, setMoveError] = useState('')

  const fetchTurn = useCallback(async () => {
    try {
      const res = await api.get(`/game/${gameId}/current-turn`)
      setTurn(res.data)
    } catch {}
  }, [gameId])

  const fetchBalance = useCallback(async () => {
    try {
      const res = await api.get(`/game/${gameId}/balance`)
      setBalance(res.data)
    } catch {}
  }, [gameId])

  const fetchHoldings = useCallback(async () => {
    try {
      const res = await api.get(`/game/${gameId}/holdings`)
      setHoldings(res.data)
    } catch {}
  }, [gameId])

  const fetchRankings = useCallback(async () => {
    try {
      const res = await api.get(`/game/${gameId}/ranking`)
      setRankings(res.data)
    } catch {}
  }, [gameId])

  const refreshAll = useCallback(() => {
    fetchTurn()
    fetchBalance()
    fetchHoldings()
    fetchRankings()
  }, [fetchTurn, fetchBalance, fetchHoldings, fetchRankings])

  useEffect(() => {
    refreshAll()
  }, [refreshAll])

  const handleMove = async () => {
    setMoving(true)
    setMoveError('')
    try {
      await api.post(`/game/${gameId}/move`)
      await refreshAll()
    } catch (e: any) {
      setMoveError(e.response?.data?.error ?? e.response?.data?.message ?? '이동 실패')
    } finally {
      setMoving(false)
    }
  }

  const handleFinish = async () => {
    if (!confirm('게임을 종료하고 최종 분석 결과를 확인하시겠습니까?')) return
    setFinishing(true)
    try {
      await api.post(`/game/${gameId}/finish`)
      onFinish(gameId)
    } catch (e: any) {
      alert(e.response?.data?.error ?? '게임 종료 실패')
    } finally {
      setFinishing(false)
    }
  }

  const currentDate = turn?.currentDate ?? ''
  const moveDaysLabel = turn ? `${turn.turnNumber}턴` : ''

  const profitRate = Number(balance?.profitRate ?? 0)
  const isProfit = profitRate >= 0

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-'
    const y = dateStr.slice(0, 4)
    const m = dateStr.slice(5, 7)
    const d = dateStr.slice(8, 10)
    return `${y}년 ${parseInt(m)}월 ${parseInt(d)}일`
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* 상단 헤더 */}
      <div className="bg-white border-b border-gray-100 px-6 py-3 sticky top-0 z-10">
        <div className="flex items-center justify-between mb-1.5">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 bg-indigo-600 rounded-lg flex items-center justify-center">
              <span className="text-white text-xs font-bold">T</span>
            </div>
            <span className="font-bold text-gray-700 text-sm">타임머신 모의투자</span>
            {moveDaysLabel && (
              <span className="text-xs bg-indigo-100 text-indigo-600 px-2 py-0.5 rounded-full font-bold">
                {moveDaysLabel}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            {moveError && <span className="text-xs text-red-500">{moveError}</span>}
            <button
              onClick={handleMove}
              disabled={moving || !turn}
              className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-bold rounded-lg transition disabled:opacity-50"
            >
              {moving ? (
                <span className="flex items-center gap-1.5">
                  <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  이동 중...
                </span>
              ) : (
                '다음으로 이동'
              )}
            </button>
            <button
              onClick={handleFinish}
              disabled={finishing}
              className="px-4 py-1.5 bg-gray-700 hover:bg-gray-800 text-white text-sm font-bold rounded-lg transition disabled:opacity-50"
            >
              {finishing ? '종료 중...' : '게임 종료'}
            </button>
          </div>
        </div>

        {/* 날짜 + 자산 요약 바 */}
        <div className="flex items-center gap-4 text-sm flex-wrap">
          <div className="flex items-center gap-1">
            <span className="text-gray-400">현재:</span>
            <span className="font-bold text-gray-800">{formatDate(currentDate)}</span>
          </div>
          <div className="h-4 w-px bg-gray-200" />
          <div className="flex items-center gap-1">
            <span className="text-gray-400">시드:</span>
            <span className="font-mono text-gray-700">{(seedMoney / 10000).toLocaleString()}만원</span>
          </div>
          <div className="h-4 w-px bg-gray-200" />
          <div className="flex items-center gap-1">
            <span className="text-gray-400">총자산:</span>
            <span className="font-mono font-bold text-gray-800">
              {balance ? `${balance.totalAsset.toLocaleString()}원` : '-'}
            </span>
          </div>
          <div className="h-4 w-px bg-gray-200" />
          <div className="flex items-center gap-1">
            <span className="text-gray-400">수익률:</span>
            <span className={`font-mono font-bold ${isProfit ? 'text-red-500' : 'text-blue-500'}`}>
              {isProfit ? '+' : ''}{profitRate.toFixed(2)}%
            </span>
          </div>
        </div>
      </div>

      {/* 메인 그리드 */}
      <div className="flex-1 p-4 grid grid-cols-12 gap-4" style={{ minHeight: 0 }}>
        {/* 왼쪽 열 (3칸) */}
        <div className="col-span-3 flex flex-col gap-4">
          <div className="flex-1">
            <AiBriefingPanel turnId={turn?.id ?? null} />
          </div>
          <div>
            <HoldingsSummary holdings={holdings} balance={balance} />
          </div>
        </div>

        {/* 가운데 열 (6칸) */}
        <div className="col-span-6 flex flex-col gap-4">
          <div>
            <GameChart
              symbol={selectedSymbol}
              startDate={startDate}
              currentDate={currentDate}
            />
          </div>
          <div>
            <GameOrderPanel
              turnId={turn?.id ?? null}
              currentDate={currentDate}
              balance={balance}
              onOrderComplete={refreshAll}
              onSymbolChange={setSelectedSymbol}
            />
          </div>
        </div>

        {/* 오른쪽 열 (3칸) */}
        <div className="col-span-3 flex flex-col gap-4">
          <div className="flex-1" style={{ minHeight: '300px' }}>
            <FamilyChat gameId={gameId} />
          </div>
          <div>
            <RankingPanel rankings={rankings} />
          </div>
        </div>
      </div>
    </div>
  )
}
