import { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { authApi, session, setAuthFailureHandler } from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => session.getAccessToken())
  const [user, setUser] = useState(() => session.getUser())

  const clearLocal = useCallback(() => {
    session.clear()
    setToken(null)
    setUser(null)
  }, [])

  // When a refresh fails, api.js clears storage; mirror that into React state so routes redirect to /login.
  useEffect(() => {
    setAuthFailureHandler(clearLocal)
  }, [clearLocal])

  /** Stores a token pair + profile returned by login/register/refresh/change-password. */
  const applySession = useCallback((res) => {
    session.save(res)
    setToken(res.token)
    setUser(session.getUser())
  }, [])

  const login = useCallback(async (email, password) => {
    const res = await authApi.login({ email: email.trim().toLowerCase(), password })
    applySession(res)
  }, [applySession])

  const register = useCallback(async (name, email, password, otp) => {
    const res = await authApi.register({
      name: name.trim(),
      email: email.trim().toLowerCase(),
      password,
      otp,
    })
    applySession(res)
  }, [applySession])

  const logout = useCallback(async () => {
    const refreshToken = session.getRefreshToken()
    clearLocal()
    if (refreshToken) {
      // Best effort: the local session is already gone even if the server can't be reached.
      try { await authApi.logout(refreshToken) } catch { /* ignore */ }
    }
  }, [clearLocal])

  const updateUser = useCallback((patch) => {
    const next = { ...session.getUser(), ...patch }
    session.saveUser(next)
    setUser(next)
  }, [])

  return (
    <AuthContext.Provider
      value={{ token, user, login, register, logout, applySession, updateUser, isAuthenticated: !!token }}
    >
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext)
}
