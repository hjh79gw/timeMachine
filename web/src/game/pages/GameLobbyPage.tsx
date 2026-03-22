import { useState, useEffect } from 'react'
import api from '../api/client'
import { GameHistoryItem } from '../types'

interface Props {
  onGameStart: (gameId: number, seedMoney: number, startDate: string) => void
}

type SeedMoney = 5000000 | 10000000 | 30000000
type Duration = '3M' | '6M' | '1Y'
type MoveDays = 7 | 14 | 30
type Difficulty = 'EASY' | 'HARD'

const SEED_OPTIONS: { value: SeedMoney; label: string }[] = [
  { value: 5000000, label: '500만원' },
  { value: 10000000, label: '1,000만원' },
  { value: 30000000, label: '3,000만원' },
]

const DURATION_OPTIONS: { value: Duration; label: string }[] = [
  { value: '3M', label: '3개월' },
  { value: '6M', label: '6개월' },
  { value: '1Y', label: '1년' },
]

const MOVE_OPTIONS: { value: MoveDays; label: string }[] = [
  { value: 7, label: '7일' },
  { value: 14, label: '14일' },
  { value: 30, label: '30일' },
]

const DIFFICULTY_OPTIONS: { value: Difficulty; label: string; desc: string }[] = [
  { value: 'EASY', label: '초보', desc: 'AI 힌트 + 간단한 분석' },
  { value: 'HARD', label: '고급', desc: '최소 가이드, 자력으로 분석' },
]

export default function GameLobbyPage({ onGameStart }: Props) {
  const [seedMoney, setSeedMoney] = useState<SeedMoney>(10000000)
  const [gameDuration, setGameDuration] = useState<Duration>('6M')
  const [moveDays, setMoveDays] = useState<MoveDays>(7)
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [history, setHistory] = useState<GameHistoryItem[]>([])
  const [historyLoading, setHistoryLoading] = useState(true)

  useEffect(() => {
    setHistoryLoading(true)
    api.get('/game/history')
      .then(res => setHistory(res.data))
      .catch(() => setHistory([]))
      .finally(() => setHistoryLoading(false))
  }, [])

  const handleStart = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await api.post('/game/start', {
        seedMoney,
        gameDuration,
        moveDays,
        difficulty,
      })
      onGameStart(res.data.id ?? res.data.gameId, res.data.seedMoney, res.data.startDate ?? '')
    } catch (e: any) {
      setError(e.response?.data?.error ?? e.response?.data?.message ?? '게임 시작에 실패했습니다')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <div className="text-center mb-8">
        <div className="w-16 h-16 bg-indigo-600 rounded-2xl flex items-center justify-center mx-auto mb-3">
          <span className="text-white text-2xl font-bold">T</span>
        </div>
        <h1 className="text-2xl font-bold text-gray-900">타임머신 모의투자</h1>
        <p className="text-gray-500 mt-1 text-sm">과거 시장으로 돌아가 투자 실력을 겨루세요</p>
      </div>

      {/* 게임 설정 폼 */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 mb-6 space-y-6">
        <h2 className="font-bold text-gray-800 text-lg">게임 설정</h2>

        {/* 시드머니 */}
        <div>
          <p className="text-sm font-bold text-gray-700 mb-2">시드머니</p>
          <div className="flex gap-2">
            {SEED_OPTIONS.map(opt => (
              <button
                key={opt.value}
                onClick={() => setSeedMoney(opt.value)}
                className={`flex-1 py-2.5 rounded-xl text-sm font-bold border-2 transition ${
                  seedMoney === opt.value
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                    : 'border-gray-200 text-gray-400 hover:border-gray-300'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* 게임 기간 */}
        <div>
          <p className="text-sm font-bold text-gray-700 mb-2">게임 기간</p>
          <div className="flex gap-2">
            {DURATION_OPTIONS.map(opt => (
              <button
                key={opt.value}
                onClick={() => setGameDuration(opt.value)}
                className={`flex-1 py-2.5 rounded-xl text-sm font-bold border-2 transition ${
                  gameDuration === opt.value
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                    : 'border-gray-200 text-gray-400 hover:border-gray-300'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* 이동 단위 */}
        <div>
          <p className="text-sm font-bold text-gray-700 mb-2">이동 단위</p>
          <div className="flex gap-2">
            {MOVE_OPTIONS.map(opt => (
              <button
                key={opt.value}
                onClick={() => setMoveDays(opt.value)}
                className={`flex-1 py-2.5 rounded-xl text-sm font-bold border-2 transition ${
                  moveDays === opt.value
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                    : 'border-gray-200 text-gray-400 hover:border-gray-300'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* 난이도 */}
        <div>
          <p className="text-sm font-bold text-gray-700 mb-2">난이도</p>
          <div className="flex gap-3">
            {DIFFICULTY_OPTIONS.map(opt => (
              <button
                key={opt.value}
                onClick={() => setDifficulty(opt.value)}
                className={`flex-1 py-3 rounded-xl border-2 transition ${
                  difficulty === opt.value
                    ? 'border-indigo-600 bg-indigo-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <p className={`text-sm font-bold ${difficulty === opt.value ? 'text-indigo-700' : 'text-gray-500'}`}>
                  {opt.label}
                </p>
                <p className="text-xs text-gray-400 mt-0.5">{opt.desc}</p>
              </button>
            ))}
          </div>
        </div>

        {error && <p className="text-red-500 text-sm text-center">{error}</p>}

        <button
          onClick={handleStart}
          disabled={loading}
          className="w-full py-4 bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-base rounded-xl transition disabled:opacity-50"
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              시장 상황을 준비하고 있습니다...
            </span>
          ) : (
            '게임 시작'
          )}
        </button>
      </div>

      {/* 게임 이력 */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
        <h2 className="font-bold text-gray-800 text-lg mb-4">과거 게임 이력</h2>

        {historyLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map(i => (
              <div key={i} className="animate-pulse h-14 bg-gray-100 rounded-xl" />
            ))}
          </div>
        ) : history.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-6">아직 게임 이력이 없습니다</p>
        ) : (
          <div className="space-y-3">
            {history.map(item => {
              const isProfit = item.profitRate >= 0
              return (
                <div key={item.id} className="flex items-center gap-3 border border-gray-100 rounded-xl px-4 py-3">
                  <div className={`w-2 h-10 rounded-full ${item.status === 'IN_PROGRESS' ? 'bg-green-400' : 'bg-gray-300'}`} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-bold text-gray-800">
                      시드 {(item.seedMoney / 10000).toLocaleString()}만원 · {item.gameDuration}
                    </p>
                    <p className="text-xs text-gray-400">{item.startDate} ~ {item.endDate}</p>
                  </div>
                  <div className="text-right">
                    <p className={`text-sm font-bold ${isProfit ? 'text-red-500' : 'text-blue-500'}`}>
                      {isProfit ? '+' : ''}{item.profitRate?.toFixed(2) ?? '0.00'}%
                    </p>
                    <p className="text-xs text-gray-400">
                      {item.status === 'IN_PROGRESS' ? '진행 중' : '완료'}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
