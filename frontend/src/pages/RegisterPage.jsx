import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { authApi } from '../services/api'
import OtpInput from '../components/OtpInput'
import { PASSWORD_HINT, validatePassword } from '../utils/password'
import { Eye, EyeOff, TrendingDown, ArrowLeft, Mail } from 'lucide-react'

function fmtTime(secs) {
  const m = String(Math.floor(secs / 60)).padStart(2, '0')
  const s = String(secs % 60).padStart(2, '0')
  return `${m}:${s}`
}

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  // ── step 1 state ──────────────────────────────────────────────────────────
  const [step, setStep] = useState(1)
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)

  // ── step 2 state ──────────────────────────────────────────────────────────
  const [otp, setOtp] = useState('')
  const [devOtp, setDevOtp] = useState(null)  // only set locally when backend returns it
  const [countdown, setCountdown] = useState(300) // 5-min expiry
  const [resendCooldown, setResendCooldown] = useState(30)
  const canResend = resendCooldown <= 0

  // ── shared ────────────────────────────────────────────────────────────────
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // OTP expiry countdown
  useEffect(() => {
    if (step !== 2 || countdown <= 0) return
    const t = setInterval(() => setCountdown((c) => c - 1), 1000)
    return () => clearInterval(t)
  }, [step, countdown])

  // Resend cooldown
  useEffect(() => {
    if (step !== 2 || canResend) return
    const t = setInterval(() => setResendCooldown((c) => c - 1), 1000)
    return () => clearInterval(t)
  }, [step, resendCooldown, canResend])

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm({ ...form, [name]: name === 'email' ? value.toLowerCase() : value })
  }

  // ── Step 1: send OTP ──────────────────────────────────────────────────────
  const handleSendOtp = async (e) => {
    e.preventDefault()
    setError('')
    const passwordError = validatePassword(form.password)
    if (passwordError) { setError(passwordError); return }
    setLoading(true)
    try {
      const res = await authApi.sendOtp({ name: form.name, email: form.email })
      if (res.devOtp) setDevOtp(res.devOtp)
      setStep(2)
      setCountdown(300)
      setResendCooldown(30)
      setOtp('')
    } catch (err) {
      setError(err.message || 'Failed to send code. Try again.')
    } finally {
      setLoading(false)
    }
  }

  // ── Step 2: verify OTP & create account ──────────────────────────────────
  const handleVerify = async (e) => {
    e.preventDefault()
    if (otp.length < 6) { setError('Please enter the full 6-digit code.'); return }
    setError('')
    setLoading(true)
    try {
      await register(form.name, form.email, form.password, otp)
      navigate('/dashboard')
    } catch (err) {
      setError(err.message || 'Verification failed. Try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    setError('')
    setLoading(true)
    try {
      const res = await authApi.sendOtp({ name: form.name, email: form.email })
      if (res.devOtp) setDevOtp(res.devOtp)
      setCountdown(300)
      setResendCooldown(30)
      setOtp('')
    } catch (err) {
      setError(err.message || 'Failed to resend code.')
    } finally {
      setLoading(false)
    }
  }

  const inputCls =
    'w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm'

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-blue-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 bg-blue-600 rounded-2xl mb-4">
            {step === 1 ? (
              <TrendingDown className="w-7 h-7 text-white" />
            ) : (
              <Mail className="w-7 h-7 text-white" />
            )}
          </div>
          {step === 1 ? (
            <>
              <h1 className="text-2xl font-bold text-gray-900">Create account</h1>
              <p className="text-gray-500 mt-1">Start tracking your expenses today</p>
            </>
          ) : (
            <>
              <h1 className="text-2xl font-bold text-gray-900">Verify your email</h1>
              <p className="text-gray-500 mt-1">
                We sent a code to <strong>{form.email}</strong>
              </p>
            </>
          )}
        </div>

        <div className="bg-white rounded-2xl shadow-lg p-8">
          {/* Progress indicator */}
          <div className="flex items-center gap-2 mb-6 justify-center">
            <div className="flex items-center gap-1.5">
              <div className="w-6 h-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center font-bold">1</div>
              <span className={`text-xs font-medium ${step === 1 ? 'text-blue-600' : 'text-gray-400'}`}>Details</span>
            </div>
            <div className={`h-px w-8 ${step === 2 ? 'bg-blue-600' : 'bg-gray-200'}`} />
            <div className="flex items-center gap-1.5">
              <div className={`w-6 h-6 rounded-full text-xs flex items-center justify-center font-bold
                ${step === 2 ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-400'}`}>2</div>
              <span className={`text-xs font-medium ${step === 2 ? 'text-blue-600' : 'text-gray-400'}`}>Verify</span>
            </div>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
              {error}
            </div>
          )}

          {/* ── STEP 1 ──────────────────────────────────────────────────── */}
          {step === 1 && (
            <form onSubmit={handleSendOtp} className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
                <input
                  type="text"
                  name="name"
                  value={form.name}
                  onChange={handleChange}
                  required
                  minLength={2}
                  placeholder="Jane Doe"
                  className={inputCls}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={handleChange}
                  required
                  placeholder="you@example.com"
                  className={inputCls}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Password <span className="text-gray-400 font-normal">({PASSWORD_HINT})</span>
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    name="password"
                    value={form.password}
                    onChange={handleChange}
                    required
                    minLength={8}
                    maxLength={128}
                    placeholder="••••••••"
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
                disabled={loading}
                className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white font-medium py-2.5 rounded-lg transition-colors text-sm"
              >
                {loading ? 'Sending code…' : 'Send Verification Code →'}
              </button>
            </form>
          )}

          {/* ── STEP 2 ──────────────────────────────────────────────────── */}
          {step === 2 && (
            <form onSubmit={handleVerify} className="space-y-5">
              {devOtp ? (
                <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm flex items-center gap-2">
                  <span className="text-amber-700 font-medium">Dev mode — your code:</span>
                  <span className="font-mono font-bold text-amber-800 tracking-widest text-base">{devOtp}</span>
                </div>
              ) : (
                <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-700">
                  A 6-digit code was sent to <strong>{form.email}</strong>. Check your inbox (and spam folder).
                </div>
              )}

              <OtpInput
                value={otp}
                onChange={setOtp}
                disabled={loading || countdown <= 0}
              />

              <p className="text-center text-sm text-gray-500">
                Code expires in{' '}
                <span className={`font-semibold ${countdown < 60 ? 'text-red-500' : 'text-gray-700'}`}>
                  {fmtTime(countdown)}
                </span>
              </p>

              <button
                type="submit"
                disabled={loading || otp.length < 6 || countdown <= 0}
                className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white font-medium py-2.5 rounded-lg transition-colors text-sm"
              >
                {loading ? 'Verifying…' : 'Verify & Create Account'}
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
                  onClick={handleResend}
                  disabled={!canResend || loading}
                  className="text-blue-600 hover:text-blue-700 disabled:text-gray-400 disabled:cursor-not-allowed"
                >
                  {canResend ? 'Resend code' : `Resend in ${resendCooldown}s`}
                </button>
              </div>
            </form>
          )}

          {step === 1 && (
            <p className="text-center text-sm text-gray-500 mt-6">
              Already have an account?{' '}
              <Link to="/login" className="text-blue-600 hover:text-blue-700 font-medium">
                Sign in
              </Link>
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

