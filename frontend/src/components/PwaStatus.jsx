import { useSyncExternalStore } from 'react'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { RefreshCw, WifiOff } from 'lucide-react'

function subscribe(callback) {
  window.addEventListener('online', callback)
  window.addEventListener('offline', callback)
  return () => {
    window.removeEventListener('online', callback)
    window.removeEventListener('offline', callback)
  }
}

function useOnlineStatus() {
  return useSyncExternalStore(subscribe, () => navigator.onLine, () => true)
}

/** Offline banner plus a "new version available" prompt (service worker uses registerType: 'prompt'). */
export default function PwaStatus() {
  const online = useOnlineStatus()
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW()

  return (
    <>
      {!online && (
        <div
          role="status"
          className="fixed top-0 inset-x-0 z-[90] bg-amber-500 text-white text-sm font-medium px-4 py-2 flex items-center justify-center gap-2"
        >
          <WifiOff className="w-4 h-4" aria-hidden="true" />
          You are offline. Changes can&apos;t be saved until you reconnect.
        </div>
      )}
      {needRefresh && (
        <div
          role="alert"
          className="fixed bottom-4 left-4 right-4 sm:right-auto sm:max-w-sm z-[95] bg-gray-900 text-white rounded-xl shadow-lg p-4 flex items-center gap-3"
        >
          <RefreshCw className="w-5 h-5 flex-shrink-0 text-primary-300" aria-hidden="true" />
          <p className="text-sm flex-1">A new version of ExpenseFlow is available.</p>
          <button
            type="button"
            onClick={() => updateServiceWorker(true)}
            className="text-sm font-semibold bg-white text-gray-900 rounded-lg px-3 py-1.5 hover:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-white"
          >
            Reload
          </button>
          <button
            type="button"
            onClick={() => setNeedRefresh(false)}
            className="text-sm text-gray-300 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-white rounded"
          >
            Later
          </button>
        </div>
      )}
    </>
  )
}
