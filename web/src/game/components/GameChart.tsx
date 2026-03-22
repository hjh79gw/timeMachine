import { useState, useEffect, useRef } from 'react'
import { createChart, IChartApi, ISeriesApi, CandlestickSeries, HistogramSeries } from 'lightweight-charts'
import api from '../api/client'

interface Props {
  symbol: string | null
  startDate: string
  currentDate: string
}

type ChartMode = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'SIMULATED'

export default function GameChart({ symbol, startDate, currentDate }: Props) {
  const [mode, setMode] = useState<ChartMode>('DAILY')
  const chartContainerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const volumeSeriesRef = useRef<ISeriesApi<'Histogram'> | null>(null)

  // symbol이 생겼을 때 차트 초기화, 사라지면 제거
  useEffect(() => {
    if (!symbol || !chartContainerRef.current) return

    // 이미 있으면 재사용
    if (chartRef.current) return

    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth || 500,
      height: 260,
      layout: { background: { color: '#ffffff' }, textColor: '#374151' },
      grid: { vertLines: { color: '#f3f4f6' }, horzLines: { color: '#f3f4f6' } },
      rightPriceScale: { borderColor: '#e5e7eb' },
      timeScale: { borderColor: '#e5e7eb', timeVisible: true },
    })
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#ef4444',
      downColor: '#3b82f6',
      borderUpColor: '#ef4444',
      borderDownColor: '#3b82f6',
      wickUpColor: '#ef4444',
      wickDownColor: '#3b82f6',
    })
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
    })
    chart.priceScale('volume').applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } })

    chartRef.current = chart
    candleSeriesRef.current = candleSeries
    volumeSeriesRef.current = volumeSeries

    const ro = new ResizeObserver(() => {
      if (chartContainerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: chartContainerRef.current.clientWidth })
      }
    })
    ro.observe(chartContainerRef.current)

    return () => {
      ro.disconnect()
      chart.remove()
      chartRef.current = null
      candleSeriesRef.current = null
      volumeSeriesRef.current = null
    }
  }, [symbol])

  // 차트 데이터 로드
  useEffect(() => {
    if (!symbol || !currentDate || !chartRef.current) return

    const effectiveFrom = startDate || (() => {
      const d = new Date(currentDate)
      d.setMonth(d.getMonth() - 6)
      return d.toISOString().slice(0, 10)
    })()

    if (mode === 'DAILY' || mode === 'WEEKLY' || mode === 'MONTHLY') {
      const endpoint = mode === 'DAILY' ? 'daily' : mode === 'WEEKLY' ? 'weekly' : 'monthly'
      const fromDate = mode === 'DAILY' ? effectiveFrom : (() => {
        const d = new Date(currentDate)
        d.setFullYear(d.getFullYear() - (mode === 'MONTHLY' ? 5 : 2))
        return d.toISOString().slice(0, 10)
      })()
      api.get(`/game/stocks/${symbol}/${endpoint}`, {
        params: { from: fromDate, to: currentDate },
      }).then(res => {
        const raw: any[] = res.data
        if (!raw.length || !candleSeriesRef.current) return
        candleSeriesRef.current.setData(raw.map(d => ({
          time: d.tradeDate as any,
          open: Number(d.openPrice),
          high: Number(d.highPrice),
          low: Number(d.lowPrice),
          close: Number(d.closePrice),
        })))
        volumeSeriesRef.current?.setData(raw.map(d => ({
          time: d.tradeDate as any,
          value: d.volume,
          color: d.closePrice >= d.openPrice ? '#fca5a5' : '#93c5fd',
        })))
        chartRef.current?.timeScale().fitContent()
      }).catch(() => {})
    } else {
      api.get(`/game/stocks/${symbol}/simulated-candles`, {
        params: { date: currentDate },
      }).then(res => {
        const raw: any[] = res.data
        if (!raw.length || !candleSeriesRef.current) return
        candleSeriesRef.current.setData(raw.map(d => ({
          time: d.time as any,
          open: d.open, high: d.high, low: d.low, close: d.close,
        })))
        volumeSeriesRef.current?.setData(raw.map(d => ({
          time: d.time as any,
          value: d.volume ?? 0,
          color: d.close >= d.open ? '#fca5a5' : '#93c5fd',
        })))
        chartRef.current?.timeScale().fitContent()
      }).catch(() => {})
    }
  }, [symbol, mode, startDate, currentDate])

  return (
    <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
      {!symbol ? (
        <div className="flex items-center justify-center h-64">
          <p className="text-sm text-gray-400">종목을 선택하면 차트가 표시됩니다</p>
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between px-4 pt-3 pb-2 border-b border-gray-100">
            <span className="font-bold text-gray-800 text-sm">{symbol} 차트</span>
            <div className="flex rounded-lg overflow-hidden border border-gray-200">
              {(['DAILY', 'WEEKLY', 'MONTHLY', 'SIMULATED'] as ChartMode[]).map(m => (
                <button key={m} onClick={() => setMode(m)}
                  className={`px-3 py-1 text-xs font-bold transition ${mode === m ? 'bg-gray-900 text-white' : 'text-gray-400 hover:bg-gray-50'}`}>
                  {m === 'DAILY' ? '일봉' : m === 'WEEKLY' ? '주봉' : m === 'MONTHLY' ? '월봉' : '분봉'}
                </button>
              ))}
            </div>
          </div>
          <div ref={chartContainerRef} className="w-full" />
          {mode === 'SIMULATED' && (
            <div className="px-4 py-2 bg-amber-50 border-t border-amber-100">
              <p className="text-xs text-amber-600">시뮬레이션 분봉입니다. 실제와 다를 수 있습니다.</p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
