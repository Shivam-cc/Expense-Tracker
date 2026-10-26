import { useState } from 'react'
import Modal from '../ui/Modal'

/** `onConfirm` may be async; if it throws the dialog stays open, shows the error, and re-enables the button. */
export default function ConfirmDialog({ title = 'Confirm Delete', message, confirmLabel = 'Delete', onConfirm, onClose }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const handleConfirm = async () => {
    setBusy(true)
    setError('')
    try {
      await onConfirm()
      onClose()
    } catch (err) {
      setError(err?.message || 'Something went wrong. Please try again.')
      setBusy(false)
    }
  }

  return (
    <Modal title={title} size="sm" onClose={onClose} dismissible={!busy} hideHeader>
      <div className="p-6">
        <h3 aria-hidden="true" className="text-base font-semibold text-gray-900 mb-2">{title}</h3>
        <p className="text-sm text-gray-600 mb-4">{message}</p>
        {error && (
          <div role="alert" className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>
        )}
        <div className="flex gap-3">
          <button
            type="button" onClick={onClose} disabled={busy} data-autofocus
            className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button" onClick={handleConfirm} disabled={busy}
            className="flex-1 px-4 py-2 bg-red-600 hover:bg-red-700 disabled:bg-red-300 text-white rounded-lg text-sm font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
          >
            {busy ? 'Deleting…' : confirmLabel}
          </button>
        </div>
      </div>
    </Modal>
  )
}
