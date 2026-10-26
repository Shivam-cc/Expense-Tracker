import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../services/api'
import OtpInput from '../components/OtpInput'
import { PASSWORD_HINT, validatePassword } from '../utils/password'
import { Eye, EyeOff, KeyRound, Mail, ArrowLeft } from 'lucide-react'

const inputCls =
  'w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent text-sm'

export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [email, setEmail] = useState('')
  const [otp, setOtp] = useState('')
  const [devOtp, setDevOtp] = useState(null) // only present when the backend runs in dev mode
  const [newPassword, setNewPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const requestCode = async (e) => {
    e?.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await authApi.forgotPassword(email.trim().toLowerCase())
      setDevOtp(res.devOtp || null)
      setOtp('')
      setStep(2)
    } catch (err) {
      setError(err.message || 'Could not send the reset code. Try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleReset = async (e) => {
    e.preventDefault()
    setError('')
    if (otp.length < 6) { setError('Please enter the full 6-digit code.'); return }
    const passwordError = validatePassword(newPassword)
    if (passwordError) { setError(passwordError); return }
    setLoading(true)
    try {
      await authApi.resetPassword({ email: email.trim().toLowerCase(), otp, newPassword })
      navigate('/login', { replace: true, state: { notice: 'Password updated. Sign in with your new password.' } })
    } catch (err) {
      setError(err.message || 'Could not reset the password. Try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-50 to-primary-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 bg-primary-600 rounded-2xl mb-4">
            {step === 1 ? <KeyRound className="w-7 h-7 text-white" /> : <Mail className="w-7 h-7 text-white" />}
          </div>
          <h1 className="text-2xl font-bold text-gray-900">
            {step === 1 ? 'Forgot your password?' : 'Reset your password'}
          </h1>
          <p className="text-gray-500 mt-1">
            {step === 1
              ? "Enter your email and we'll send you a reset code"
              : <>If an account exists for <strong>{email}</strong>, a code is on its way</>}
          </p>
        </div>

        <div className="bg-white rounded-2xl shadow-lg p-8">
          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>
          )}

          {step === 1 && (
            <form onSubmit={requestCode} className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value.toLowerCase())}
                  required
                  placeholder="you@example.com"
                  className={inputCls}
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 text-white font-medium py-2.5 rounded-lg transition-colors text-sm"
              >
                {loading ? 'Sending codeÃ¢â‚¬Â¦' : 'Send reset code'}
              </button>
            </form>
          )}

          {step === 2 && (
            <form onSubmit={handleReset} className="space-y-5">
              {devOtp && (
                <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm flex items-center gap-2">
                  <span className="text-amber-700 font-medium">Dev mode - your code:</span>
                  <span className="font-mono font-bold text-amber-800 tracking-widest text-base">{devOtp}</span>
                </div>
              )}
              <OtpInput value={otp} onChange={setOtp} disabled={loading} />
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  New password <span className="text-gray-400 font-normal">({PASSWORD_HINT})</span>
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    minLength={8}
                    maxLength={128}
                    autoComplete="new-password"
                    className={`${inputCls} pr-10`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>
              <button
                type="submit"
                disabled={loading || otp.length < 6}
                className="w-full bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 text-white font-medium py-2.5 rounded-lg transition-colors text-sm"
              >
                {loading ? 'UpdatingÃ¢â‚¬Â¦' : 'Reset password'}
              </button>
              <div className="flex items-center justify-between text-sm pt-1">
                <button
                  type="button"
                  onClick={() => { setStep(1); setError('') }}
                  className="text-gray-500 hover:text-gray-700 flex items-center gap-1"
                >
                  <ArrowLeft className="w-4 h-4" /> Back
                </button>
                <button
                  type="button"
                  onClick={requestCode}
                  disabled={loading}
                  className="text-primary-600 hover:text-primary-700 disabled:text-gray-400"
                >
                  Resend code
                </button>
              </div>
            </form>
          )}

          <p className="text-center text-sm text-gray-500 mt-6">
            Remembered it?{' '}
            <Link to="/login" className="text-primary-600 hover:text-primary-700 font-medium">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
