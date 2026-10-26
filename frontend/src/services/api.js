import { API_BASE_URL as BASE_URL } from '../config'

function getToken() {
  return localStorage.getItem('token')
}

async function request(path, options = {}) {
  const token = getToken()
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...options,
  })
  if (!res.ok) {
    const ct = res.headers.get('content-type') || ''
    if (ct.includes('application/json')) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Request failed with status ${res.status}`)
    }
    // Non-JSON error page (HTML 404/502 from proxy/CDN)
    throw new Error(`Request failed with status ${res.status}. Check that the backend is reachable.`)
  }
  if (res.status === 204) return null
  const contentType = res.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    throw new Error('Server returned an unexpected response format.')
  }
  return res.json()
}

export const authApi = {
  login: (data) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  register: (data) =>
    request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  sendOtp: (data) =>
    request('/auth/send-otp', { method: 'POST', body: JSON.stringify(data) }),
}

export const expensesApi = {
  list: (params = {}) => {
    const qs = new URLSearchParams(params).toString()
    return request(`/expenses?${qs}`)
  },
  create: (data) =>
    request('/expenses', { method: 'POST', body: JSON.stringify(data) }),
  update: (id, data) =>
    request(`/expenses/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id) =>
    request(`/expenses/${id}`, { method: 'DELETE' }),
  summary: (startDate, endDate) =>
    request(`/expenses/summary?startDate=${startDate}&endDate=${endDate}`),
  // Bearer auth means a plain <a href> can't be used, so the CSV is fetched as a blob.
  export: async (params = {}) => {
    const qs = new URLSearchParams(params).toString()
    const token = getToken()
    const res = await fetch(`${BASE_URL}/expenses/export?${qs}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Export failed with status ${res.status}`)
    }
    const disposition = res.headers.get('content-disposition') || ''
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
    return { blob: await res.blob(), filename: match ? decodeURIComponent(match[1]) : 'expenses.csv' }
  },
}

export const categoriesApi = {
  list: () => request('/categories'),
  create: (data) =>
    request('/categories', { method: 'POST', body: JSON.stringify(data) }),
  update: (id, data) =>
    request(`/categories/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id) =>
    request(`/categories/${id}`, { method: 'DELETE' }),
}
