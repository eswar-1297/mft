import { useCallback, useEffect, useRef, useState } from 'react'

interface PollState<T> {
  data: T | null
  error: string | null
  loading: boolean
  refresh: () => void
}

/**
 * Fetches immediately and then every {@code intervalMs}. Keeps the dashboard and transfer views
 * "live" during a demo without a websocket. Set intervalMs to 0 to fetch once (no polling).
 */
export function usePoll<T>(fetcher: () => Promise<T>, intervalMs: number): PollState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const savedFetcher = useRef(fetcher)
  savedFetcher.current = fetcher

  const run = useCallback(async () => {
    try {
      const result = await savedFetcher.current()
      setData(result)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Request failed')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    run()
    if (intervalMs <= 0) return
    const id = setInterval(run, intervalMs)
    return () => clearInterval(id)
  }, [run, intervalMs])

  return { data, error, loading, refresh: run }
}
