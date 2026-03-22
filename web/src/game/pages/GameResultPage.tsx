import { useState, useEffect, useRef } from 'react'
import { createChart, IChartApi, ISeriesApi, LineSeries } from 'lightweight-charts'
import api from '../api/client'
import { TradeHistory, PortfolioHistory } from '../types'

interface Props {
  gameId: number
  onBack: () => void
}

interface Analysis {
  report: string
  finalProfitRate: number
  totalAsset: number
  totalTrades: number
}

export default function GameResultPage({ gameId, onBack }: Props) {
  const [analysis, setAnalysis] = useState<Analysis | null>(null)
  const [analysisLoading, setAnalysisLoading] = useState(true)
  const [trades, setTrades] = useState<TradeHistory[]>([])
  const [portfolioHistory, setPortfolioHistory] = useState<PortfolioHistory[]>([])

  const chartContainerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const lineSeriesRef = useRef<ISeriesApi<'Line'> | null>(null)

  // 분석 데이터 로드
  useEffect(() => {
    setAnalysisLoading(true)
    api.get(`/game/${gameId}/analysis`)
      .then(res => setAnalysis(res.data))
      .catch(() => setAnalysis(null))
      .finally(() => setAnalysisLoading(false))

    api.get(`/game/${gameId}/portfolio-history`)
      .then(res => setPortfolioHistory(res.data))
      .catch(() => {})

    api.get(`/game/${gameId}/trades`)
      .then(res => setTrades(res.data))
      .catch(() => {})
  }, [gameId])

  // 차트 초기화
  useEffect(() => {
    if (!chartContainerRef.current) return
    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth,
      height: 200,
      layout: { background: { color: '#ffffff' }, textColor: '#374151' },
      grid: { vertLines: { color: '#f3f4f6' }, horzLines: { color: '#f3f4f6' } },
      rightPriceScale: { borderColor: '#e5e7eb' },
      timeScale: { borderColor: '#e5e7eb', timeVisible: false },
    })
    const lineSeries = chart.addSeries(LineSeries, {
      color: '#6366f1',
      lineWidth: 2,
    })
    chartRef.current = chart
    lineSeriesRef.current = lineSeries

    const ro = new ResizeObserver(() => {
      if (chartContainerRef.current) {
        chart.applyOptions({ width: chartContainerRef.current.clientWidth })
      }
    })
    ro.observe(chartContainerRef.current)
    return () => { chart.remove(); ro.disconnect() }
  }, [])

  // 포트폴리오 히스토리 데이터 반영
  useEffect(() => {
    if (!portfolioHistory.length || !lineSeriesRef.current) return
    const data = portfolioHistory.map(d => ({
      time: d.date as any,
      value: d.totalAsset,
    }))
    lineSeriesRef.current.setData(data)
    chartRef.current?.timeScale().fitContent()
  }, [portfolioHistory])

  const isProfit = (analysis?.finalProfitRate ?? 0) >= 0

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      {/* 헤더 */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={onBack}
          className="w-8 h-8 flex items-center justify-center rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50"
        >
          ←
        </button>
        <div>
          <h1 className="font-bold text-gray-900 text-xl">게임 결과 분석</h1>
          <p className="text-sm text-gray-400">나비가 내 투자 패턴을 분석했습니다</p>
        </div>
      </div>

      {/* 최종 성과 요약 */}
      {analysis && (
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 mb-6">
          <div className="grid grid-cols-3 gap-4 text-center">
            <div>
              <p className="text-xs text-gray-400 mb-1">최종 자산</p>
              <p className="text-xl font-bold font-mono text-gray-900">
                {(analysis.totalAsset / 10000).toFixed(0)}만원
              </p>
            </div>
            <div>
              <p className="text-xs text-gray-400 mb-1">최종 수익률</p>
              <p className={`text-xl font-bold font-mono ${isProfit ? 'text-red-500' : 'text-blue-500'}`}>
                {isProfit ? '+' : ''}{analysis.finalProfitRate.toFixed(2)}%
              </p>
            </div>
            <div>
              <p className="text-xs text-gray-400 mb-1">총 거래 횟수</p>
              <p className="text-xl font-bold font-mono text-gray-900">{analysis.totalTrades}회</p>
            </div>
          </div>
        </div>
      )}

      {/* 자산 변동 그래프 */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 mb-6">
        <h2 className="font-bold text-gray-800 mb-4">자산 변동 그래프</h2>
        <div ref={chartContainerRef} className="w-full" style={{ display: portfolioHistory.length === 0 ? 'none' : 'block' }} />
        {portfolioHistory.length === 0 && (
          <div className="flex items-center justify-center h-32">
            <p className="text-sm text-gray-400">포트폴리오 데이터가 없습니다</p>
          </div>
        )}
      </div>

      {/* AI 분석 리포트 */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-2 mb-4">
          <div className="w-6 h-6 bg-indigo-600 rounded-md flex items-center justify-center">
            <span className="text-white text-xs font-bold">나비</span>
          </div>
          <h2 className="font-bold text-gray-800">나비 최종 분석 리포트</h2>
        </div>

        {analysisLoading ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 mb-4">
              <span className="w-4 h-4 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
              <span className="text-sm text-gray-500">나비가 매매 이력을 분석하고 있습니다...</span>
            </div>
            <div className="animate-pulse space-y-2">
              <div className="h-3 bg-gray-200 rounded w-full" />
              <div className="h-3 bg-gray-200 rounded w-5/6" />
              <div className="h-3 bg-gray-200 rounded w-4/5" />
              <div className="h-3 bg-gray-200 rounded w-full" />
              <div className="h-3 bg-gray-200 rounded w-3/4" />
            </div>
          </div>
        ) : analysis?.report ? (
          <div className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
            {analysis.report}
          </div>
        ) : (
          <p className="text-sm text-gray-400 text-center py-4">분석 리포트를 불러올 수 없습니다</p>
        )}
      </div>

      {/* 전체 매매 내역 */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
        <h2 className="font-bold text-gray-800 mb-4">전체 매매 내역</h2>

        {trades.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-4">매매 내역이 없습니다</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="text-left py-2 px-3 text-xs text-gray-400 font-medium">날짜</th>
                  <th className="text-left py-2 px-3 text-xs text-gray-400 font-medium">종목</th>
                  <th className="text-center py-2 px-3 text-xs text-gray-400 font-medium">구분</th>
                  <th className="text-right py-2 px-3 text-xs text-gray-400 font-medium">수량</th>
                  <th className="text-right py-2 px-3 text-xs text-gray-400 font-medium">단가</th>
                  <th className="text-right py-2 px-3 text-xs text-gray-400 font-medium">금액</th>
                </tr>
              </thead>
              <tbody>
                {trades.map((trade, idx) => (
                  <tr key={trade.id ?? idx} className="border-b border-gray-50 hover:bg-gray-50">
                    <td className="py-2 px-3 text-xs text-gray-500">{trade.tradeDate}</td>
                    <td className="py-2 px-3">
                      <p className="font-medium text-gray-800">{trade.stockName}</p>
                      <p className="text-xs text-gray-400">{trade.symbol}</p>
                    </td>
                    <td className="py-2 px-3 text-center">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                        trade.orderType === 'BUY'
                          ? 'bg-red-100 text-red-600'
                          : 'bg-blue-100 text-blue-600'
                      }`}>
                        {trade.orderType === 'BUY' ? '매수' : '매도'}
                      </span>
                    </td>
                    <td className="py-2 px-3 text-right font-mono text-gray-700">{trade.quantity.toLocaleString()}</td>
                    <td className="py-2 px-3 text-right font-mono text-gray-700">{trade.price.toLocaleString()}</td>
                    <td className="py-2 px-3 text-right font-mono text-gray-700">{trade.amount.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="mt-6 text-center">
        <button
          onClick={onBack}
          className="px-8 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl transition"
        >
          새 게임 시작
        </button>
      </div>
    </div>
  )
}
