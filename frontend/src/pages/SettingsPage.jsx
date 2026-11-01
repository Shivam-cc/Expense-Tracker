import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { userApi } from '../services/api'
import { PASSWORD_HINT, validatePassword } from '../utils/password'
import { ArrowLeft, LogOut, Trash2, X } from 'lucide-react'
import Tabs from '../components/ui/Tabs'

const inputCls =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'
const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'JPY', 'AUD', 'CAD', 'SGD', 'AED', 'CHF', 'CNY']

const primaryBtn =
  'px-4 py-2 bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 text-white rounded-lg text-sm font-medium transition-colors'

function Notice({ notice }) {
  if (!notice) return null
  const tone = notice.type === 'error'
    ? 'bg-red-50 border-red-200 text-red-700'
    : 'bg-green-50 border-green-200 text-green-700'
  return <div className={`mb-4 p-3 border rounded-lg text-sm ${tone}`}>{notice.text}</div>
}

function Section({ title, description, danger = false, children }) {
  return (
    <section className={`bg-white rounded-xl shadow-sm border ${danger ? 'border-red-100' : 'border-gray-100'}`}>
      <div className={`px-5 py-4 border-b ${danger ? 'border-red-100' : 'border-gray-100'}`}>
        <h2 className={`font-semibold ${danger ? 'text-red-700' : 'text-gray-900'}`}>{title}</h2>
        {description && <p className="text-sm text-gray-500 mt-0.5">{description}</p>}
      </div>
      <div className="p-5">{children}</div>
    </section>
  )
}

function Field({ id, label, hint, className = '', children }) {
  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      {children}
      {hint && <p className="text-xs text-gray-500 mt-1">{hint}</p>}
    </div>
  )
}

// Text on the left, the action on the right (stacked on small screens).
function ActionRow({ text, children }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <p className="text-sm text-gray-600 max-w-xl">{text}</p>
      <div className="shrink-0">{children}</div>
    </div>
  )
}

function ProfileSection() {
  const { user, updateUser } = useAuth()
  const [name, setName] = useState(user?.name || '')
  const [currency, setCurrency] = useState(user?.currency || 'INR')
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setNotice(null)
    setSaving(true)
    try {
      const updated = await userApi.update({ name: name.trim(), currency })
      updateUser({ name: updated.name, currency: updated.currency })
      setNotice({ type: 'success', text: 'Profile updated.' })
    } catch (err) {
      setNotice({ type: 'error', text: err.message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Section title="Profile" description="How your name and money are shown across ExpenseFlow.">
      <Notice notice={notice} />
      <form onSubmit={handleSubmit} className="grid sm:grid-cols-2 gap-4">
        <Field id="profile-name" label="Name">
          <input
            id="profile-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required minLength={2} maxLength={50}
            className={inputCls}
          />
        </Field>
        <Field id="profile-currency" label="Currency">
          <select id="profile-currency" value={currency} onChange={(e) => setCurrency(e.target.value)} className={inputCls}>
            {[...new Set([user?.currency, ...CURRENCIES].filter(Boolean))].map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </Field>
        <Field id="profile-email" label="Email" hint="Changing your email address isn't supported yet." className="sm:col-span-2">
          <input id="profile-email" value={user?.email || ''} disabled className={`${inputCls} bg-gray-50 text-gray-500`} />
        </Field>
        <div className="sm:col-span-2 flex justify-end">
          <button type="submit" disabled={saving || (name.trim() === user?.name && currency === (user?.currency || 'INR'))} className={primaryBtn}>
            {saving ? 'Saving…' : 'Save changes'}
          </button>
        </div>
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
    <Section title="Change password" description="Other devices are signed out when you change it.">
      <Notice notice={notice} />
      <form onSubmit={handleSubmit} className="grid sm:grid-cols-2 gap-4">
        <Field id="current-password" label="Current password">
          <input
            id="current-password"
            type="password" name="currentPassword" value={form.currentPassword}
            onChange={handleChange} required autoComplete="current-password" className={inputCls}
          />
        </Field>
        <Field id="new-password" label="New password" hint={PASSWORD_HINT}>
          <input
            id="new-password"
            type="password" name="newPassword" value={form.newPassword}
            onChange={handleChange} required minLength={8} maxLength={128}
            autoComplete="new-password" className={inputCls}
          />
        </Field>
        <div className="sm:col-span-2 flex justify-end">
          <button type="submit" disabled={saving} className={primaryBtn}>
            {saving ? 'Updating…' : 'Update password'}
          </button>
        </div>
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
    <Section title="Sessions">
      <Notice notice={notice} />
      <ActionRow text="Sign out of ExpenseFlow on every device, including this one.">
        <button
          onClick={handleLogoutAll}
          disabled={working}
          className="flex items-center gap-1.5 px-4 py-2 border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50 rounded-lg text-sm font-medium"
        >
          <LogOut className="w-4 h-4" /> {working ? 'Signing out…' : 'Log out all devices'}
        </button>
      </ActionRow>
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

const TABS = [
  { id: 'profile', label: 'Profile' },
  { id: 'security', label: 'Security' },
  { id: 'danger', label: 'Danger zone' },
]

export default function SettingsPage() {
  const [tab, setTab] = useState('profile')
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  return (
    <>
      <Link
        to="/dashboard"
        className="inline-flex items-center gap-1 mb-3 -ml-1 px-1 py-0.5 rounded text-sm font-medium text-gray-600 hover:text-primary-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
      >
        <ArrowLeft className="w-4 h-4" aria-hidden="true" /> Back to dashboard
      </Link>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-500">Manage your profile, password, sessions and account.</p>
      </div>

      <Tabs tabs={TABS} value={tab} onChange={setTab} label="Settings sections">
        {tab === 'profile' && <ProfileSection />}
        {tab === 'security' && (
          <div className="space-y-4">
            <PasswordSection />
            <SessionsSection />
          </div>
        )}
        {tab === 'danger' && (
          <Section title="Delete account" danger>
            <ActionRow text="Permanently remove your account and all of your expenses and categories. This cannot be undone.">
              <button
                onClick={() => setConfirmingDelete(true)}
                className="flex items-center gap-1.5 px-4 py-2 border border-red-200 text-red-600 hover:bg-red-50 rounded-lg text-sm font-medium"
              >
                <Trash2 className="w-4 h-4" /> Delete my account
              </button>
            </ActionRow>
          </Section>
        )}
      </Tabs>

      {confirmingDelete && <DeleteAccountModal onClose={() => setConfirmingDelete(false)} />}
    </>
  )
}
