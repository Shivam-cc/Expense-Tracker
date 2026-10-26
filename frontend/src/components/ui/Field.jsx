export default function Field({ id, label, required, error, hint, children }) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1">
        {label}
        {required && <span aria-hidden="true"> *</span>}
        {hint && <span className="text-gray-400 font-normal"> {hint}</span>}
      </label>
      {children}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-xs text-red-600">{error}</p>
      )}
    </div>
  )
}
