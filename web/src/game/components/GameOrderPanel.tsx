import { useState, useEffect, useRef, useCallback } from 'react'
import api from '../api/client'
import { Balance } from '../types'

interface StockSearchResult {
  symbol: string
  name: string   // backend returns 'name'
  market: string
}

interface Props {
  turnId: number | null
  currentDate: string
  balance: Balance | null
  onOrderComplete: () => void
  onSymbolChange: (symbol: string) => void
}

type OrderMode = 'BUY' | 'SELL'
type PriceType = 'MARKET' | 'LIMIT'

export default function GameOrderPanel({ turnId, currentDate, balance, onOrderComplete, onSymbolChange }: Props) {
  const [keyword, setKeyword] = useState('')
  const [searchResults, setSearchResults] = useState<StockSearchResult[]>([])
  const [selectedStock, setSelectedStock] = useState<StockSearchResult | null>(null)
  const [currentPrice, setCurrentPrice] = useState<number | null>(null)
  const [priceLoading, setPriceLoading] = useState(false)
  const [priceError, setPriceError] = useState(false)
  const [mode, setMode] = useState<OrderMode>('BUY')
  const [priceType, setPriceType] = useState<PriceType>('MARKET')
  const [quantity, setQuantity] = useState('')
  const [limitPrice, setLimitPrice] = useState('')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [showDropdown, setShowDropdown] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 종목 검색 debounce
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!keyword.trim()) {
      setSearchResults([])
      setShowDropdown(false)
      return
    }
    debounceRef.current = setTimeout(() => {
      api.get('/game/stocks/search', { params: { keyword } })
        .then(res => {
          setSearchResults(res.data)
          setShowDropdown(true)
        })
        .catch(() => setSearchResults([]))
    }, 300)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [keyword])

  // 선택 종목 당일 시세 조회
  useEffect(() => {
    if (!selectedStock || !currentDate) return
    setPriceLoading(true)
    setPriceError(false)
    setCurrentPrice(null)
    api.get(`/game/stocks/${selectedStock.symbol}/price`, { params: { date: currentDate } })
      .then(res => {
        setCurrentPrice(res.data.closePrice ?? res.data.currentPrice ?? res.data.price ?? null)
      })
      .catch(() => { setCurrentPrice(null); setPriceError(true) })
      .finally(() => setPriceLoading(false))
  }, [selectedStock, currentDate])

  const handleSelectStock = (stock: StockSearchResult) => {
    setSelectedStock(stock)
    setKeyword(stock.name)
    setShowDropdown(false)
    setQuantity('')
    setLimitPrice('')
    setMessage('')
    setError('')
    onSymbolChange(stock.symbol)
  }

  const qty = parseInt(quantity) || 0
  const effectivePrice = priceType === 'MARKET' ? (currentPrice ?? 0) : (parseFloat(limitPrice) || 0)
  const totalAmount = effectivePrice * qty

  const maxBuyQty = balance && effectivePrice > 0 ? Math.floor(balance.cash / effectivePrice) : 0

  const setQtyByPercent = useCallback((pct: number) => {
    if (mode === 'BUY') {
      setQuantity(String(Math.floor(maxBuyQty * pct / 100)))
    }
  }, [mode, maxBuyQty])

  const handleOrder = async () => {
    if (!turnId || !selectedStock || qty <= 0) return
    setLoading(true)
    setError('')
    setMessage('')
    try {
      await api.post(`/game/turns/${turnId}/orders`, {
        symbol: selectedStock.symbol,
        orderType: mode,
        priceType,
        orderPrice: priceType === 'LIMIT' ? parseFloat(limitPrice) : undefined,
        quantity: qty,
      })
      setMessage(`${selectedStock.name} ${mode === 'BUY' ? '매수' : '매도'} 주문 완료`)
      setQuantity('')
      onOrderComplete()
    } catch (e: any) {
      setError(e.response?.data?.error ?? e.response?.data?.message ?? '주문 실패')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-white rounded-xl border border-gray-100 p-4 flex flex-col gap-3">
      <div className="flex items-center gap-2 mb-1">
        <div className="w-6 h-6 bg-rose-600 rounded-md flex items-center justify-center flex-shrink-0">
          <span className="text-white text-xs font-bold">주</span>
        </div>
        <span className="font-bold text-gray-800 text-sm">주문 패널</span>
      </div>

      {/* 종목 검색 */}
      <div className="relative">
        <p className="text-xs text-gray-400 mb-1">종목 검색</p>
        <input
          type="text"
          value={keyword}
          onChange={e => setKeyword(e.target.value)}
          onFocus={() => searchResults.length > 0 && setShowDropdown(true)}
          placeholder="종목명 또는 코드 입력"
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400"
        />
        {showDropdown && searchResults.length > 0 && (
          <div className="absolute z-20 top-full mt-1 left-0 right-0 bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-y-auto">
            {searchResults.map(s => (
              <button
                key={s.symbol}
                onClick={() => handleSelectStock(s)}
                className="w-full px-3 py-2 text-left hover:bg-gray-50 flex items-center justify-between"
              >
                <span className="text-sm font-medium text-gray-800">{s.name}</span>
                <span className="text-xs text-gray-400">{s.symbol}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 현재 시세 */}
      {selectedStock && (
        <div className="flex items-center justify-between bg-gray-50 rounded-lg px-3 py-2">
          <div>
            <p className="text-xs text-gray-400">현재 시세 ({currentDate})</p>
            <p className="text-sm font-bold font-mono text-gray-800">
              {priceLoading ? '조회 중...' : priceError ? '이 기간 데이터 없음' : currentPrice != null ? `${currentPrice.toLocaleString()}원` : '-'}
            </p>
          </div>
          <p className="text-xs text-gray-500 text-right">
            주문은 해당일<br />시가로 체결됩니다
          </p>
        </div>
      )}

      {/* 매수/매도 탭 */}
      <div className="flex border border-gray-200 rounded-lg overflow-hidden">
        <button
          onClick={() => setMode('BUY')}
          className={`flex-1 py-2 text-sm font-bold transition ${
            mode === 'BUY' ? 'bg-red-500 text-white' : 'text-gray-400 hover:bg-gray-50'
          }`}
        >
          매수
        </button>
        <button
          onClick={() => setMode('SELL')}
          className={`flex-1 py-2 text-sm font-bold transition ${
            mode === 'SELL' ? 'bg-blue-500 text-white' : 'text-gray-400 hover:bg-gray-50'
          }`}
        >
          매도
        </button>
      </div>

      {/* 주문 유형 */}
      <div>
        <p className="text-xs text-gray-400 mb-1">주문 유형</p>
        <div className="flex rounded-lg overflow-hidden border border-gray-200">
          <button
            onClick={() => setPriceType('MARKET')}
            className={`flex-1 py-1.5 text-xs font-bold transition ${
              priceType === 'MARKET' ? 'bg-gray-900 text-white' : 'text-gray-400 hover:bg-gray-50'
            }`}
          >
            시장가
          </button>
          <button
            onClick={() => setPriceType('LIMIT')}
            className={`flex-1 py-1.5 text-xs font-bold transition ${
              priceType === 'LIMIT' ? 'bg-gray-900 text-white' : 'text-gray-400 hover:bg-gray-50'
            }`}
          >
            지정가
          </button>
        </div>
      </div>

      {/* 지정가 입력 */}
      {priceType === 'LIMIT' && (
        <div>
          <p className="text-xs text-gray-400 mb-1">지정 단가</p>
          <input
            type="number"
            value={limitPrice}
            onChange={e => setLimitPrice(e.target.value)}
            placeholder={currentPrice?.toLocaleString() ?? '가격 입력'}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-right font-mono text-sm focus:outline-none focus:border-blue-400"
          />
        </div>
      )}

      {/* 수량 입력 */}
      <div>
        <div className="flex justify-between mb-1">
          <p className="text-xs text-gray-400">수량</p>
          {mode === 'BUY' && (
            <p className="text-xs text-gray-400">최대 {maxBuyQty.toLocaleString()}주</p>
          )}
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setQuantity(q => String(Math.max(0, (parseInt(q) || 0) - 1)))}
            className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center text-gray-600 font-bold hover:bg-gray-50"
          >
            −
          </button>
          <input
            type="number"
            value={quantity}
            min={0}
            onChange={e => setQuantity(e.target.value)}
            className="flex-1 border border-gray-200 rounded-lg px-2 py-1.5 text-center font-mono text-sm focus:outline-none"
          />
          <button
            onClick={() => setQuantity(q => String((parseInt(q) || 0) + 1))}
            className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center text-gray-600 font-bold hover:bg-gray-50"
          >
            +
          </button>
        </div>
        {mode === 'BUY' && (
          <div className="flex gap-1 mt-2">
            {[10, 25, 50, 100].map(pct => (
              <button
                key={pct}
                onClick={() => setQtyByPercent(pct)}
                className="flex-1 py-1 text-xs border border-gray-200 rounded-lg text-gray-500 hover:bg-gray-50"
              >
                {pct}%
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 주문 요약 */}
      <div className="space-y-1 text-xs bg-gray-50 rounded-lg p-3">
        <div className="flex justify-between">
          <span className="text-gray-400">주문 단가</span>
          <span className="font-mono text-gray-600">
            {priceType === 'MARKET' ? '시장가' : `${parseFloat(limitPrice || '0').toLocaleString()}원`}
          </span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-400">주문 수량</span>
          <span className="font-mono text-gray-600">{qty.toLocaleString()}주</span>
        </div>
        <div className="flex justify-between font-bold border-t border-gray-200 pt-1 mt-1">
          <span className="text-gray-500">총 주문 금액</span>
          <span className="font-mono">{totalAmount.toLocaleString()}원</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-400">보유 현금</span>
          <span className="font-mono text-gray-600">
            {balance ? `${balance.cash.toLocaleString()}원` : '-'}
          </span>
        </div>
      </div>

      {message && <p className="text-green-600 text-xs text-center">{message}</p>}
      {error && <p className="text-red-500 text-xs text-center">{error}</p>}

      <button
        onClick={handleOrder}
        disabled={loading || qty <= 0 || !selectedStock || !turnId}
        className={`w-full py-3 rounded-xl text-white text-sm font-bold disabled:opacity-40 transition ${
          mode === 'BUY' ? 'bg-red-500 hover:bg-red-600' : 'bg-blue-500 hover:bg-blue-600'
        }`}
      >
        {loading
          ? '처리 중...'
          : selectedStock
          ? `${selectedStock.name} ${mode === 'BUY' ? '매수' : '매도'}`
          : '종목을 선택하세요'}
      </button>
    </div>
  )
}
