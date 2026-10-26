import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi, expensesApi, session, setAuthFailureHandler } from './api'

const BASE = 'http://localhost:8080/api'

function jsonResponse(status, body, contentType = 'application/json') {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null) },
    json: async () => body,
  }
}

const emptyResponse = (status) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: () => null },
  json: async () => ({}),
})

const tokenPair = (token, refreshToken) => ({ token, refreshToken, expiresIn: 900, name: 'Ann', email: 'ann@example.com' })

/** Routes fetch calls by URL so tests read as a description of the server's behaviour. */
function mockServer(handlers) {
  const calls = []
  vi.stubGlobal('fetch', vi.fn(async (url, init = {}) => {
    const path = url.replace(BASE, '')
    calls.push({ path, init })
    const handler = handlers[path]
    if (!handler) throw new Error(`Unexpected request to ${path}`)
    return handler(init, calls.filter((c) => c.path === path).length)
  }))
  return calls
}

const authHeader = (init) => init.headers.Authorization

describe('api session handling', () => {
  let onAuthFailure

  beforeEach(() => {
    onAuthFailure = vi.fn()
    setAuthFailureHandler(onAuthFailure)
    session.save(tokenPair('access-1', 'refresh-1'))
  })

  it('sends the access token as a bearer header', async () => {
    const calls = mockServer({ '/expenses?': () => jsonResponse(200, { content: [] }) })

    await expensesApi.list()

    expect(authHeader(calls[0].init)).toBe('Bearer access-1')
  })

  it('refreshes once on 401, stores the rotated tokens and retries the request', async () => {
    const calls = mockServer({
      '/expenses?': (init) => (authHeader(init) === 'Bearer access-2'
        ? jsonResponse(200, { content: ['ok'] })
        : jsonResponse(401, { message: 'expired' })),
      '/auth/refresh': () => jsonResponse(200, tokenPair('access-2', 'refresh-2')),
    })

    const result = await expensesApi.list()

    expect(result).toEqual({ content: ['ok'] })
    expect(calls.map((c) => c.path)).toEqual(['/expenses?', '/auth/refresh', '/expenses?'])
    expect(JSON.parse(calls[1].init.body)).toEqual({ refreshToken: 'refresh-1' })
    expect(session.getAccessToken()).toBe('access-2')
    expect(session.getRefreshToken()).toBe('refresh-2')
    expect(onAuthFailure).not.toHaveBeenCalled()
  })

  it('shares one refresh between parallel requests (refresh tokens are single-use)', async () => {
    let releaseRefresh
    const refreshGate = new Promise((resolve) => { releaseRefresh = resolve })
    const calls = mockServer({
      '/expenses?': (init) => (authHeader(init) === 'Bearer access-2'
        ? jsonResponse(200, { content: [] })
        : jsonResponse(401, {})),
      '/auth/refresh': async () => {
        await refreshGate
        return jsonResponse(200, tokenPair('access-2', 'refresh-2'))
      },
    })

    const pending = Promise.all([expensesApi.list(), expensesApi.list(), expensesApi.list()])
    await vi.waitFor(() => expect(calls.filter((c) => c.path === '/expenses?')).toHaveLength(3))
    releaseRefresh()
    await pending

    expect(calls.filter((c) => c.path === '/auth/refresh')).toHaveLength(1)
  })

  it('signs out and redirects when the refresh is rejected', async () => {
    mockServer({
      '/expenses?': () => jsonResponse(401, {}),
      '/auth/refresh': () => jsonResponse(401, { message: 'Invalid refresh token' }),
    })

    await expect(expensesApi.list()).rejects.toThrow(/session has expired/i)

    expect(onAuthFailure).toHaveBeenCalledTimes(1)
    expect(session.getAccessToken()).toBeNull()
    expect(session.getRefreshToken()).toBeNull()
    expect(session.getUser()).toBeNull()
  })

  it('signs out immediately when there is no refresh token to try', async () => {
    localStorage.removeItem('refreshToken')
    const calls = mockServer({ '/expenses?': () => jsonResponse(401, {}) })

    await expect(expensesApi.list()).rejects.toThrow(/session has expired/i)

    expect(calls).toHaveLength(1)
    expect(onAuthFailure).toHaveBeenCalledTimes(1)
  })

  it('does not retry forever when the retried request is still unauthorized', async () => {
    const calls = mockServer({
      '/expenses?': () => jsonResponse(401, {}),
      '/auth/refresh': () => jsonResponse(200, tokenPair('access-2', 'refresh-2')),
    })

    await expect(expensesApi.list()).rejects.toThrow(/session has expired/i)

    expect(calls.map((c) => c.path)).toEqual(['/expenses?', '/auth/refresh', '/expenses?'])
    expect(onAuthFailure).toHaveBeenCalledTimes(1)
  })

  it('never attaches a token to, or refreshes after, a failed /auth call', async () => {
    const calls = mockServer({
      '/auth/login': () => jsonResponse(401, { message: 'Invalid email or password' }),
    })

    await expect(authApi.login({ email: 'a@b.co', password: 'x' })).rejects.toThrow('Invalid email or password')

    expect(calls).toHaveLength(1)
    expect(authHeader(calls[0].init)).toBeUndefined()
    expect(onAuthFailure).not.toHaveBeenCalled()
    expect(session.getAccessToken()).toBe('access-1')
  })

  it('surfaces field-level validation errors when there is no message', async () => {
    mockServer({
      '/expenses': () => jsonResponse(400, { errors: { amount: 'Amount must be positive', date: 'Date is required' } }),
    })

    await expect(expensesApi.create({})).rejects.toThrow('Amount must be positive. Date is required')
  })

  it('gives a helpful message for non-JSON error pages', async () => {
    mockServer({ '/expenses': () => jsonResponse(502, null, 'text/html') })

    await expect(expensesApi.create({})).rejects.toThrow(/status 502/)
  })

  it('returns null for 204 responses', async () => {
    mockServer({ '/expenses/7': () => emptyResponse(204) })

    await expect(expensesApi.delete(7)).resolves.toBeNull()
  })
})
