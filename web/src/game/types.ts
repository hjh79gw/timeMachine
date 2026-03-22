export interface GameSession {
  id: number
  seedMoney: number
  gameDuration: string
  startDate: string
  endDate: string
  moveDays: number
  difficulty: 'EASY' | 'HARD'
  currentDate: string
  status: 'IN_PROGRESS' | 'FINISHED'
}

export interface GameTurn {
  id: number
  gameId: number
  turnNumber: number
  currentDate: string
  aiBriefing: string | null
  status: 'ACTIVE' | 'COMPLETED'
}

export interface GameOrder {
  symbol: string
  orderType: 'BUY' | 'SELL'
  priceType: 'MARKET' | 'LIMIT'
  orderPrice?: number
  quantity: number
}

export interface Holding {
  symbol: string
  stockName: string
  quantity: number
  avgBuyPrice: number
  currentPrice: number
  evalAmount: number
  profitRate: number
}

export interface Balance {
  cash: number
  holdingsValue: number
  totalAsset: number
  profitRate: number
}

export interface Ranking {
  userId: number
  userName: string
  totalAsset: number
  profitRate: number
  rank: number
}

export interface StockPrice {
  symbol: string
  tradeDate: string
  openPrice: number
  highPrice: number
  lowPrice: number
  closePrice: number
  volume: number
  changeRate: number
}

export interface GameHistoryItem {
  id: number
  seedMoney: number
  gameDuration: string
  startDate: string
  endDate: string
  status: 'IN_PROGRESS' | 'FINISHED'
  profitRate: number
  totalAsset: number
  createdAt: string
}

export interface TradeHistory {
  id: number
  symbol: string
  stockName: string
  orderType: 'BUY' | 'SELL'
  quantity: number
  price: number
  amount: number
  tradeDate: string
}

export interface PortfolioHistory {
  date: string
  totalAsset: number
  cash: number
  holdingsValue: number
}

export interface ChatMessage {
  id: number
  userId: number
  userName: string
  message: string
  sentAt: string
}
