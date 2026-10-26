import { useRef } from 'react'

/**
 * 6-box OTP input with auto-advance, backspace navigation, and paste support.
 * Props:
 *   value    – string (up to 6 digits)
 *   onChange – (newValue: string) => void
 *   disabled – boolean
 */
export default function OtpInput({ value = '', onChange, disabled = false }) {
  const inputs = useRef([])
  const digits = value.padEnd(6, ' ').split('').slice(0, 6)

  const update = (index, char) => {
    const arr = value.split('').slice(0, 6)
    while (arr.length < 6) arr.push('')
    arr[index] = char
    onChange(arr.join('').trimEnd().replace(/[^0-9]/g, ''))
  }

  const handleChange = (e, i) => {
    const char = e.target.value.replace(/[^0-9]/g, '').slice(-1)
    if (!char) return
    update(i, char)
    if (i < 5) inputs.current[i + 1]?.focus()
  }

  const handleKeyDown = (e, i) => {
    if (e.key === 'Backspace') {
      e.preventDefault()
      if (digits[i].trim()) {
        update(i, '')
      } else if (i > 0) {
        inputs.current[i - 1]?.focus()
        update(i - 1, '')
      }
    } else if (e.key === 'ArrowLeft' && i > 0) {
      inputs.current[i - 1]?.focus()
    } else if (e.key === 'ArrowRight' && i < 5) {
      inputs.current[i + 1]?.focus()
    }
  }

  const handlePaste = (e) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/[^0-9]/g, '').slice(0, 6)
    onChange(pasted)
    const focusIdx = Math.min(pasted.length, 5)
    inputs.current[focusIdx]?.focus()
  }

  return (
    <div className="flex gap-3 justify-center" role="group" aria-label="6-digit verification code">
      {Array.from({ length: 6 }).map((_, i) => (
        <input
          key={i}
          ref={(el) => (inputs.current[i] = el)}
          type="text"
          inputMode="numeric"
          maxLength={1}
          aria-label={`Digit ${i + 1} of 6`}
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          value={digits[i].trim()}
          onChange={(e) => handleChange(e, i)}
          onKeyDown={(e) => handleKeyDown(e, i)}
          onPaste={i === 0 ? handlePaste : undefined}
          onFocus={(e) => e.target.select()}
          disabled={disabled}
          className="w-11 h-12 text-center text-xl font-bold border-2 border-gray-300 rounded-xl
                     focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200
                     disabled:bg-gray-50 disabled:text-gray-400
                     transition-colors"
        />
      ))}
    </div>
  )
}
