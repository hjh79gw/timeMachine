import { Holding, Balance } from '../types'

interface Props {
  holdings: Holding[]
  balance: Balance | null
}

export default function HoldingsSummary({ holdings, balance }: Props) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 p-4 flex flex-col gap-3">
      <div className="flex items-center gap-2 mb-1">
        <div className="w-6 h-6 bg-amber-500 rounded-md flex items-center justify-center flex-shrink-0">
          <span className="text-white text-xs font-bold">보</span>
        </div>
        <span className="font-bold text-gray-800 text-sm">보유 종목</span>
      </div>

      {/* 잔고 요약 */}
      {balance && (
        <div className="bg-gray-50 rounded-lg px-3 py-2 grid grid-cols-2 gap-2 text-xs">
          <div>
            <p className="text-gray-400">보유 현금</p>
            <p className="font-bold font-mono text-gray-700">{balance.cash.toLocaleString()}원</p>
          </div>
          <div>
            <p className="text-gray-400">평가 금액</p>
            <p className="font-bold font-mono text-gray-700">{balance.holdingsValue.toLocaleString()}원</p>
          </div>
        </div>
      )}

      {/* 종목 목록 */}
      {holdings.length === 0 ? (
        <p className="text-xs text-gray-400 text-center py-2">보유 종목이 없습니다</p>
      ) : (
        <div className="space-y-2 max-h-52 overflow-y-auto">
          {holdings.map(h => {
            const isProfit = h.profitRate >= 0
            return (
              <div key={h.symbol} className="border border-gray-100 rounded-lg p-2">
                <div className="flex items-center justify-between mb-1">
                  <div>
                    <span className="text-xs font-bold text-gray-800">{h.stockName}</span>
                    <span className="text-xs text-gray-400 ml-1">{h.symbol}</span>
                  </div>
                  <span className={`text-xs font-bold ${isProfit ? 'text-red-500' : 'text-blue-500'}`}>
                    {isProfit ? '+' : ''}{h.profitRate.toFixed(2)}%
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-1 text-xs">
                  <div>
                    <p className="text-gray-400">수량</p>
                    <p className="font-mono text-gray-700">{h.quantity.toLocaleString()}주</p>
                  </div>
                  <div>
                    <p className="text-gray-400">평균단가</p>
                    <p className="font-mono text-gray-700">{h.avgBuyPrice.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-gray-400">현재가</p>
                    <p className={`font-mono font-bold ${isProfit ? 'text-red-500' : 'text-blue-500'}`}>
                      {h.currentPrice.toLocaleString()}
                    </p>
                  </div>
                </div>
                <div className="flex justify-between mt-1 pt-1 border-t border-gray-50">
                  <span className="text-xs text-gray-400">평가손익</span>
                  <span className={`text-xs font-bold font-mono ${isProfit ? 'text-red-500' : 'text-blue-500'}`}>
                    {isProfit ? '+' : ''}{(h.evalAmount - h.avgBuyPrice * h.quantity).toLocaleString()}원
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
