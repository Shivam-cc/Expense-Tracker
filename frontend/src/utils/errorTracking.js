const dsn = import.meta.env.VITE_SENTRY_DSN

// The SDK is imported dynamically so it is only downloaded when a DSN is configured.
let sentry = null

export function initErrorTracking() {
  if (!dsn) return
  sentry = import('@sentry/react')
    .then((Sentry) => {
      Sentry.init({
        dsn,
        environment: import.meta.env.MODE,
        sendDefaultPii: false,
        tracesSampleRate: 0,
      })
      return Sentry
    })
    .catch(() => null)
}

export function reportError(error, context) {
  sentry?.then((Sentry) => Sentry?.captureException(error, { extra: context }))
}
