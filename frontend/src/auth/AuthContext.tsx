import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { auth as authApi } from '../api/endpoints'
import type { CurrentUser } from '../api/types'

interface AuthContextValue {
  user: CurrentUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<CurrentUser>
  register: (email: string, password: string, fullName: string) => Promise<CurrentUser>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children, initialUser }: { children: ReactNode; initialUser?: CurrentUser | null }) {
  const [user, setUser] = useState<CurrentUser | null>(initialUser ?? null)
  const [loading, setLoading] = useState(initialUser === undefined)

  const refresh = useCallback(async () => {
    try {
      setUser(await authApi.me())
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setUser(null)
      else throw error
    }
  }, [])

  useEffect(() => {
    if (initialUser !== undefined) return
    refresh().catch(() => setUser(null)).finally(() => setLoading(false))
  }, [refresh, initialUser])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      refresh,
      login: async (email, password) => {
        const u = await authApi.login(email, password)
        setUser(u)
        return u
      },
      register: async (email, password, fullName) => {
        const u = await authApi.register(email, password, fullName)
        return u
      },
      logout: async () => {
        await authApi.logout()
        setUser(null)
      },
    }),
    [user, loading, refresh],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
