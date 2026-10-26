import { createContext, useContext } from 'react'

export const ToastContext = createContext(null)

/** Returns `{ success, error, info, dismiss }`; each takes a message string. */
export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  return ctx
}
