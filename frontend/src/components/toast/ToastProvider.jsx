import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react'
import { ToastContext } from './toastContext'

const STYLES = {
  success: { icon: CheckCircle2, cls: 'border-accent-200 bg-white text-gray-800', iconCls: 'text-accent-600' },
  error: { icon: AlertCircle, cls: 'border-red-200 bg-white text-gray-800', iconCls: 'text-red-500' },
  info: { icon: Info, cls: 'border-primary-200 bg-white text-gray-800', iconCls: 'text-primary-600' },
}
const DURATION = { success: 3500, info: 4000, error: 6000 }
const MAX_TOASTS = 4

export default function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const nextId = useRef(1)
  const timers = useRef(new Map())

  const dismiss = useCallback((id) => {
    clearTimeout(timers.current.get(id))
    timers.current.delete(id)
    setToasts((list) => list.filter((t) => t.id !== id))
  }, [])

  const push = useCallback((type, message) => {
    const id = nextId.current++
    setToasts((list) => [...list.slice(-(MAX_TOASTS - 1)), { id, type, message }])
    timers.current.set(id, setTimeout(() => dismiss(id), DURATION[type]))
    return id
  }, [dismiss])

  useEffect(() => {
    const pending = timers.current
    return () => pending.forEach((t) => clearTimeout(t))
  }, [])

  const api = useMemo(() => ({
    success: (message) => push('success', message),
    error: (message) => push('error', message),
    info: (message) => push('info', message),
    dismiss,
  }), [push, dismiss])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="fixed bottom-4 right-4 left-4 sm:left-auto z-[100] flex flex-col gap-2 sm:w-96 pointer-events-none"
      >
        {toasts.map((t) => {
          const { icon: Icon, cls, iconCls } = STYLES[t.type]
          return (
            <div
              key={t.id}
              role={t.type === 'error' ? 'alert' : 'status'}
              className={`pointer-events-auto flex items-start gap-3 rounded-xl border p-3.5 shadow-lg text-sm ${cls}`}
            >
              <Icon className={`w-5 h-5 flex-shrink-0 mt-0.5 ${iconCls}`} aria-hidden="true" />
              <p className="flex-1 break-words">{t.message}</p>
              <button
                type="button"
                onClick={() => dismiss(t.id)}
                aria-label="Dismiss notification"
                className="text-gray-400 hover:text-gray-600 rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}
