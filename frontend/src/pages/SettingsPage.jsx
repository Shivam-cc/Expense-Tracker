import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { userApi } from '../services/api'
import { PASSWORD_HINT, validatePassword } from '../utils/password'
import { ArrowLeft, TrendingDown, LogOut, Trash2, X } from 'lucide-react'

const inputCls =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'
const primaryBtn =
  'px-4 py-2 bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 text-white rounded-lg text-sm font-medium transition-colors'

function Notice({ notice }) {
  if (!notice) return null
  const tone = notice.type === 'error'
    ? 'bg-red-50 border-red-200 text-red-700'
    : 'bg-green-50 border-green-200 text-green-700'
  return <div className={`mb-4 p-3 border rounded-lg text-sm ${tone}`}>{notice.text}</div>
}

function Section({ title, description, children }) {
  return (
    <section className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
      <h2 className="font-semibold text-gray-900">{title}</h2>
      {description && <p className="text-sm text-gray-500 mt-1 mb-4">{description}</p>}
      {!description && <div className="mb-4" />}
      {children}
    </section>
  )
}

function ProfileSection() {
  const { user, updateUser } = useAuth()
  const [name, setName] = useState(user?.name || '')
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setNotice(null)
    setSaving(true)
    try {
      const updated = await userApi.update({ name: name.trim() })
      updateUser({ name: updated.name })
      setNotice({ type: 'success', text: 'Profile updated.' })
    } catch (err) {
      setNotice({ type: 'error', text: err.message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Section title="Profile">
      <Notice notice={notice} />
      <form onSubmit={handleSubmit} className="space-y-4 max-w-sm">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            required minLength={2} maxLength={50}
            className={inputCls}
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
          <input value={user?.email || ''} disabled className={`${inputCls} bg-gray-50 text-gray-500`} />
          <p className="text-xs text-gray-400 mt-1">Changing your email address isn't supported yet.</p>
        </div>
        <button type="submit" disabled={saving || name.trim() === user?.name} className={primaryBtn}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </Section>
  )
}

function PasswordSection() {
  const { applySession } = useAuth()
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' })
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setNotice(null)
    const passwordError = validatePassword(form.newPassword)
    if (passwordError) { setNotice({ type: 'error', text: passwordError }); return }
    setSaving(true)
    try {
      // Server revokes every other session and returns a fresh token pair for this one.
      const session = await userApi.changePassword(form)
      applySession(session)
      setForm({ currentPassword: '', newPassword: '' })
      setNotice({ type: 'success', text: 'Password changed. Other devices have been signed out.' })
    } catch (err) {
      setNotice({ type: 'error', text: err.message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Section title="Change password">
      <Notice notice={notice} />
      <form onSubmit={handleSubmit} className="space-y-4 max-w-sm">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Current password</label>
          <input
            type="password" name="currentPassword" value={form.currentPassword}
            onChange={handleChange} required autoComplete="current-password" className={inputCls}
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">New password</label>
          <input
            type="password" name="newPassword" value={form.newPassword}
            onChange={handleChange} required minLength={8} maxLength={128}
            autoComplete="new-password" className={inputCls}
          />
          <p className="text-xs text-gray-400 mt-1">{PASSWORD_HINT}</p>
        </div>
        <button type="submit" disabled={saving} className={primaryBtn}>
          {saving ? 'Updating…' : 'Update password'}
        </button>
      </form>
    </Section>
  )
}

function SessionsSection() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [working, setWorking] = useState(false)
  const [notice, setNotice] = useState(null)

  const handleLogoutAll = async () => {
    setNotice(null)
    setWorking(true)
    try {
      await userApi.logoutAll()
      await logout()
      navigate('/login', { replace: true })
    } catch (err) {
      setNotice({ type: 'error', text: err.message })
      setWorking(false)
    }
  }

  return (
    <Section
      title="Sessions"
      description="Sign out of ExpenseFlow on every device, including this one."
    >
      <Notice notice={notice} />
      <button
        onClick={handleLogoutAll}
        disabled={working}
        className="flex items-center gap-1.5 px-4 py-2 border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50 rounded-lg text-sm font-medium"
      >
        <LogOut className="w-4 h-4" /> {working ? 'Signing out…' : 'Log out all devices'}
      </button>
    </Section>
  )
}

function DeleteAccountModal({ onClose }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [deleting, setDeleting] = useState(false)

  const handleDelete = async (e) => {
    e.preventDefault()
    setError('')
    setDeleting(true)
    try {
      await userApi.deleteAccount(password)
      await logout()
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
      setDeleting(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
        <div className="flex items-center justify-between p-6 border-b border-gray-100">
          <h2 className="text-lg font-semibold text-gray-900">Delete account</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600" aria-label="Close">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={handleDelete} className="p-6 space-y-4">
          <p className="text-sm text-gray-600">
            This permanently deletes your account, all of your expenses and categories. This cannot be undone.
          </p>
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>
          )}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Confirm with your password</label>
            <input
              type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              required autoComplete="current-password" className={inputCls}
            />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={deleting || !password}
              className="flex-1 px-4 py-2 bg-red-600 hover:bg-red-700 disabled:bg-red-300 text-white rounded-lg text-sm font-medium">
              {deleting ? 'Deleting…' : 'Delete forever'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function SettingsPage() {
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-3xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 bg-primary-600 rounded-lg flex items-center justify-center">
              <TrendingDown className="w-4 h-4 text-white" />
            </div>
            <span className="font-semibold text-gray-900">Settings</span>
          </div>
          <Link to="/dashboard" className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="w-4 h-4" /> Back to dashboard
          </Link>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-6">
        <ProfileSection />
        <PasswordSection />
        <SessionsSection />
        <Section
          title="Delete account"
          description="Permanently remove your account and all of your data."
        >
          <button
            onClick={() => setConfirmingDelete(true)}
            className="flex items-center gap-1.5 px-4 py-2 border border-red-200 text-red-600 hover:bg-red-50 rounded-lg text-sm font-medium"
          >
            <Trash2 className="w-4 h-4" /> Delete my account
          </button>
        </Section>
      </main>

      {confirmingDelete && <DeleteAccountModal onClose={() => setConfirmingDelete(false)} />}
    </div>
  )
}
