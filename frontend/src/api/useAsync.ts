import { useCallback, useEffect, useRef, useState } from 'react'

export type AsyncState<T> =
  | { status: 'loading'; data?: T; error?: undefined }
  | { status: 'success'; data: T; error?: undefined }
  | { status: 'error'; data?: T; error: unknown }

/**
 * Tiny data-loading hook: runs `loader` whenever `deps` change, exposes loading / error /
 * data states, and keeps the previous data visible while reloading so lists don't flicker.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' })
  const runId = useRef(0)

  const reload = useCallback(() => {
    const id = ++runId.current
    setState((prev) => ({ status: 'loading', data: prev.data }))
    loader().then(
      (data) => {
        if (id === runId.current) setState({ status: 'success', data })
      },
      (error: unknown) => {
        if (id === runId.current) setState((prev) => ({ status: 'error', error, data: prev.data }))
      },
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    reload()
  }, [reload])

  return { ...state, reload }
}
