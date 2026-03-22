import { useState } from 'react'
import GameLobbyPage from './pages/GameLobbyPage'
import GamePlayPage from './pages/GamePlayPage'
import GameResultPage from './pages/GameResultPage'

type GameView = 'lobby' | 'play' | 'result'

interface GameState {
  gameId: number
  seedMoney: number
  startDate: string
}

export default function GameApp() {
  const [view, setView] = useState<GameView>('lobby')
  const [gameState, setGameState] = useState<GameState | null>(null)

  const handleGameStart = (gameId: number, seedMoney?: number, startDate?: string) => {
    setGameState({
      gameId,
      seedMoney: seedMoney ?? 10000000,
      startDate: startDate ?? '',
    })
    setView('play')
  }

  const handleFinish = (_gameId: number) => {
    setView('result')
  }

  const handleBackToLobby = () => {
    setGameState(null)
    setView('lobby')
  }

  if (view === 'play' && gameState) {
    return (
      <GamePlayPage
        gameId={gameState.gameId}
        seedMoney={gameState.seedMoney}
        startDate={gameState.startDate}
        onFinish={handleFinish}
      />
    )
  }

  if (view === 'result' && gameState) {
    return (
      <GameResultPage
        gameId={gameState.gameId}
        onBack={handleBackToLobby}
      />
    )
  }

  return (
    <GameLobbyPage
      onGameStart={(gameId, seedMoney, startDate) => handleGameStart(gameId, seedMoney, startDate)}
    />
  )
}
