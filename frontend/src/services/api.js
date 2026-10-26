// Ensure the API URL always has a protocol prefix.
// If VITE_API_URL is set without https:// (e.g. in Vercel dashboard) the browser
// would treat it as a relative path on the current origin - so we force https://.
function resolveBaseUrl() {
  const raw = import.meta.env.VITE_API_URL
  if (!raw) return 'http://localhost:8080/api'
  const url = raw.startsWith('http://') || raw.startsWith('https://')
    ? raw
    : 'https://' + raw
  return url.replace(/\/$/, '') + '/api'   // strip trailing slash, add /api
}

const BASE_URL = resolveBaseUrl()

// Tokens live in localStorage: the API is on a different site (Railway) than the SPA (Vercel), so
// third-party cookies are unreliable. The trade-off is that XSS could read them, hence the CSP in vercel.json
// and the short 15-minute access token lifetime.
export const session = {
  getAccessToken: () => localStorage.getItem('token'),
  getRefreshToken: () => localStorage.getItem('refreshToken'),
  getUser() {
    const saved = localStorage.getItem('user')
    return saved ? JSON.parse(saved) : null
  },
  save(auth) {
    const profile = { ...auth }
    delete profile.token
    delete profile.refreshToken
    delete profile.expiresIn
    localStorage.setItem('token', auth.token)
    localStorage.setItem('refreshToken', auth.refreshToken)
    localStorage.setItem('user', JSON.stringify(profile))
  },
  saveUser(profile) {
    localStorage.setItem('user', JSON.stringify(profile))
  },
  clear() {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  },
}

let onAuthFailure = () => window.location.assign('/login')

/** Called after the session is cleared because it could not be refreshed. */
export function setAuthFailureHandler(handler) {
  onAuthFailure = handler
}

let refreshInFlight = null

// Single-flight: parallel 401s share one refresh, because refresh tokens are single-use
// and a second concurrent refresh with the same token would look like token theft.
function refreshAccessToken() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = session.getRefreshToken()
      if (!refreshToken) throw new Error('No refresh token')
      const res = await fetch(`${BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) throw new Error('Session expired')
      const auth = await res.json()
      session.save(auth)
      return auth.token
    })().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function readError(res) {
  const ct = res.headers.get('content-type') || ''
  if (ct.includes('application/json')) {
    const err = await res.json().catch(() => ({}))
    const fieldErrors = err.errors ? Object.values(err.errors).join('. ') : ''
    return err.message || fieldErrors || `Request failed with status ${res.status}`
  }
  // Non-JSON error page (HTML 404/502 from proxy/CDN)
  return `Request failed with status ${res.status}. Check that the backend is reachable.`
}

async function request(path, options = {}, isRetry = false) {
  // /auth/* endpoints are public: never send a (possibly stale) bearer token, never try to refresh.
  const isAuthPath = path.startsWith('/auth/')
  const token = isAuthPath ? null : session.getAccessToken()
  const { headers, ...rest } = options
  const res = await fetch(`${BASE_URL}${path}`, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  })

  if (res.status === 401 && !isAuthPath) {
    if (!isRetry && session.getRefreshToken()) {
      try {
        await refreshAccessToken()
        return request(path, options, true)
      } catch {
        // fall through to sign-out
      }
    }
    session.clear()
    onAuthFailure()
    throw new Error('Your session has expired. Please sign in again.')
  }

  if (!res.ok) throw new Error(await readError(res))
  if (res.status === 204) return null
  const contentType = res.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    throw new Error('Server returned an unexpected response format.')
  }
  return res.json()
}

// Same auth rules as request(), but for binary responses (CSV export). Returns { blob, filename }.
async function requestBlob(path, isRetry = false) {
  const token = session.getAccessToken()
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })

  if (res.status === 401) {
    if (!isRetry && session.getRefreshToken()) {
      try {
        await refreshAccessToken()
        return requestBlob(path, true)
      } catch {
        // fall through to sign-out
      }
    }
    session.clear()
    onAuthFailure()
    throw new Error('Your session has expired. Please sign in again.')
  }

  if (!res.ok) throw new Error(await readError(res))
  const disposition = res.headers.get('content-disposition') || ''
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  return {
    blob: await res.blob(),
    filename: match ? decodeURIComponent(match[1]) : 'expenses.csv',
  }
}

const json = (method, data) => ({ method, body: JSON.stringify(data) })

export const authApi = {
  login: (data) => request('/auth/login', json('POST', data)),
  register: (data) => request('/auth/register', json('POST', data)),
  sendOtp: (data) => request('/auth/send-otp', json('POST', data)),
  logout: (refreshToken) => request('/auth/logout', json('POST', { refreshToken })),
  forgotPassword: (email) => request('/auth/forgot-password', json('POST', { email })),
  resetPassword: (data) => request('/auth/reset-password', json('POST', data)),
}

export const userApi = {
  me: () => request('/users/me'),
  update: (data) => request('/users/me', json('PATCH', data)),
  changePassword: (data) => request('/users/me/password', json('POST', data)),
  logoutAll: () => request('/users/me/logout-all', { method: 'POST' }),
  deleteAccount: (password) => request('/users/me', json('DELETE', { password })),
}

export const expensesApi = {
  list: (params = {}) => {
    const qs = new URLSearchParams(params).toString()
    return request(`/expenses?${qs}`)
  },
  create: (data) => request('/expenses', json('POST', data)),
  update: (id, data) => request(`/expenses/${id}`, json('PUT', data)),
  delete: (id) => request(`/expenses/${id}`, { method: 'DELETE' }),
  summary: (startDate, endDate) =>
    request(`/expenses/summary?startDate=${startDate}&endDate=${endDate}`),
  // Bearer auth means a plain <a href> can't be used, so the CSV is fetched as a blob.
  export: (params = {}) => {
    const qs = new URLSearchParams(params).toString()
    return requestBlob(`/expenses/export?${qs}`)
  },
}

export const categoriesApi = {
  list: () => request('/categories'),
  create: (data) => request('/categories', json('POST', data)),
  update: (id, data) => request(`/categories/${id}`, json('PUT', data)),
  delete: (id) => request(`/categories/${id}`, { method: 'DELETE' }),
}
