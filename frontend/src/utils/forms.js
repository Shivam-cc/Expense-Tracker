export const inputCls =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:border-transparent aria-[invalid=true]:border-red-400'

/** Attributes linking an input to its <Field> label and error message. */
export function fieldProps(id, error) {
  return {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? `${id}-error` : undefined,
  }
}

/**
 * Splits a thrown API error into a banner message and per-field messages.
 * `err.errors` is the `{ field: message }` map of 400 validation responses (when the API layer provides it).
 */
export function parseApiError(err, fallback = 'Something went wrong. Please try again.') {
  const fieldErrors = err?.errors && typeof err.errors === 'object' ? err.errors : {}
  const hasFields = Object.keys(fieldErrors).length > 0
  return {
    message: hasFields ? 'Please fix the highlighted fields.' : err?.message || fallback,
    fieldErrors,
  }
}
