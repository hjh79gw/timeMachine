import { Ranking } from '../types'

interface Props {
  rankings: Ranking[]
}

const medalColors = ['text-yellow-500', 'text-gray-400', 'text-amber-600']
const medalSymbols = ['1', '2', '3']

export default function RankingPanel({ rankings }: Props) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 p-4 flex flex-col gap-3">
      <div className="flex items-center gap-2 mb-1">
        <div className="w-6 h-6 bg-purple-600 rounded-md flex items-center justify-center flex-shrink-0">
          <span className="text-white text-xs font-bold">순</span>
        </div>
        <span className="font-bold text-gray-800 text-sm">가족 수익률 현황</span>
      </div>

      {rankings.length === 0 ? (
        <p className="text-xs text-gray-400 text-center py-2">순위 데이터가 없습니다</p>
      ) : (
        <div className="space-y-2 max-h-60 overflow-y-auto">
          {rankings.map((r, idx) => {
            const profitRate = Number(r.profitRate ?? 0)
            const isProfit = profitRate >= 0
            return (
              <div
                key={r.userId}
                className={`flex items-center gap-3 rounded-lg px-3 py-2 ${
                  idx === 0 ? 'bg-yellow-50 border border-yellow-100' : 'bg-gray-50'
                }`}
              >
                <span className={`text-sm font-bold w-5 text-center ${medalColors[idx] ?? 'text-gray-500'}`}>
                  {idx < 3 ? medalSymbols[idx] : r.rank}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-bold text-gray-800 truncate">{r.userName}</p>
                  <p className="text-xs text-gray-400 font-mono">{r.totalAsset.toLocaleString()}원</p>
                </div>
                <span className={`text-xs font-bold font-mono ${isProfit ? 'text-red-500' : 'text-blue-500'}`}>
                  {isProfit ? '+' : ''}{profitRate.toFixed(2)}%
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
