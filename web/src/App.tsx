import { useState, useEffect } from 'react'
import axios from 'axios'
import LoginPage from './pages/LoginPage'
import GameApp from './game/GameApp'

const api = axios.create({ baseURL: 'http://localhost:8082/api', withCredentials: true })

export default function App() {
  const [loggedIn, setLoggedIn] = useState<boolean | null>(null)

  useEffect(() => {
    api.get('/auth/me')
      .then(() => setLoggedIn(true))
      .catch(() => setLoggedIn(false))
  }, [])

  if (loggedIn === null) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <span className="w-8 h-8 border-4 border-indigo-400 border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (!loggedIn) {
    return <LoginPage onLogin={() => setLoggedIn(true)} />
  }

  return <GameApp />
}
