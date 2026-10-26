// VITE_API_URL may be set without a protocol (e.g. in the Vercel dashboard); the browser would then
// treat it as a relative path on the current origin, so https:// is forced when it is missing.
function resolveApiBaseUrl() {
  const raw = import.meta.env.VITE_API_URL
  if (!raw) return 'http://localhost:8080/api'
  const url = raw.startsWith('http://') || raw.startsWith('https://')
    ? raw
    : 'https://' + raw
  return url.replace(/\/$/, '') + '/api'
}

/** Base URL of the backend API, including the trailing `/api` segment. */
export const API_BASE_URL = resolveApiBaseUrl()
