import { AlertCircle, RefreshCw } from 'lucide-react'

export default function ErrorState({ message = 'Something went wrong while loading.', onRetry, compact = false }) {
  return (
    <div
      role="alert"
      className={`flex flex-col items-center justify-center text-center text-gray-500 ${compact ? 'py-6' : 'py-16'}`}
    >
      <AlertCircle className="w-9 h-9 mb-3 text-red-400" aria-hidden="true" />
      <p className="text-sm mb-4 max-w-xs">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        >
          <RefreshCw className="w-4 h-4" /> Try again
        </button>
      )}
    </div>
  )
}
