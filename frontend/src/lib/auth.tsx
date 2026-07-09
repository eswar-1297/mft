import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, clearToken, getToken, setToken } from './api'
import type { LoginResponse, User } from './types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (tenantSlug: string, email: string, password: string, otp?: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const logout = useCallback(() => {
    clearToken()
    setUser(null)
  }, [])

  // On boot, if we have a token, confirm it by loading the current user.
  useEffect(() => {
    let active = true
    async function boot() {
      if (!getToken()) {
        setLoading(false)
        return
      }
      try {
        const me = await api<User>('/api/auth/me')
        if (active) setUser(me)
      } catch {
        clearToken()
      } finally {
        if (active) setLoading(false)
      }
    }
    boot()
    const onUnauthorized = () => setUser(null)
    window.addEventListener('mft-unauthorized', onUnauthorized)
    return () => {
      active = false
      window.removeEventListener('mft-unauthorized', onUnauthorized)
    }
  }, [])

  const login = useCallback(
    async (tenantSlug: string, email: string, password: string, otp?: string) => {
      const res = await api<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: { tenantSlug, email, password, otp },
      })
      setToken(res.accessToken)
      setUser(res.user)
    },
    [],
  )

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
