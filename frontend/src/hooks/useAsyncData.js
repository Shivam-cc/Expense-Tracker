import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Loads data whenever `key` changes. While a new request is in flight the previous `data` is kept
 * (so lists don't flash empty) and `loading` is true; stale responses are discarded.
 */
export function useAsyncData(fetcher, key) {
  const [reloadTick, setReloadTick] = useState(0)
  const [result, setResult] = useState({ key: null, data: null, error: null })
  const fetcherRef = useRef(fetcher)
  const requestKey = `${key}#${reloadTick}`

  useEffect(() => {
    fetcherRef.current = fetcher
  })

  useEffect(() => {
    let cancelled = false
    fetcherRef.current().then(
      (data) => { if (!cancelled) setResult({ key: requestKey, data, error: null }) },
      (error) => { if (!cancelled) setResult({ key: requestKey, data: null, error }) },
    )
    return () => { cancelled = true }
  }, [requestKey])

  const reload = useCallback(() => setReloadTick((t) => t + 1), [])
  const settled = result.key === requestKey

  return {
    data: result.data,
    error: settled ? result.error : null,
    loading: !settled,
    reload,
  }
}
