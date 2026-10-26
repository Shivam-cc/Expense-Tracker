import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

const SIZES = { sm: 'max-w-sm', md: 'max-w-md' }

/**
 * Accessible modal dialog: Escape and backdrop click close it (unless `dismissible` is false, e.g. while
 * saving), Tab is trapped inside, and focus returns to the previously focused element on close.
 */
export default function Modal({ title, onClose, size = 'md', dismissible = true, hideHeader = false, children }) {
  const titleId = useId()
  const panelRef = useRef(null)
  const stateRef = useRef({ onClose, dismissible })

  useEffect(() => {
    stateRef.current = { onClose, dismissible }
  })

  useEffect(() => {
    const panel = panelRef.current
    const previouslyFocused = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const focusables = () => Array.from(panel.querySelectorAll(FOCUSABLE))
    const initial = panel.querySelector('[data-autofocus]') || focusables()[0] || panel
    initial.focus()

    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (stateRef.current.dismissible) {
          e.stopPropagation()
          stateRef.current.onClose()
        }
        return
      }
      if (e.key !== 'Tab') return
      const items = focusables()
      if (items.length === 0) {
        e.preventDefault()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      if (e.shiftKey && (document.activeElement === first || !panel.contains(document.activeElement))) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && (document.activeElement === last || !panel.contains(document.activeElement))) {
        e.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKeyDown)

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
      if (previouslyFocused instanceof HTMLElement && document.contains(previouslyFocused)) {
        previouslyFocused.focus()
      }
    }
  }, [])

  const handleBackdropMouseDown = (e) => {
    if (e.target === e.currentTarget && dismissible) onClose()
  }

  return createPortal(
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onMouseDown={handleBackdropMouseDown}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`bg-white rounded-2xl shadow-xl w-full ${SIZES[size]} max-h-[calc(100vh-2rem)] overflow-y-auto focus:outline-none`}
      >
        {hideHeader ? (
          <h2 id={titleId} className="sr-only">{title}</h2>
        ) : (
          <div className="flex items-center justify-between p-6 border-b border-gray-100">
            <h2 id={titleId} className="text-lg font-semibold text-gray-900">{title}</h2>
            <button
              type="button"
              onClick={onClose}
              disabled={!dismissible}
              aria-label="Close dialog"
              className="text-gray-400 hover:text-gray-600 rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 disabled:opacity-40"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        )}
        {children}
      </div>
    </div>,
    document.body,
  )
}
