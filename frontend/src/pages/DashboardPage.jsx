import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { expensesApi, categoriesApi } from '../services/api'
import {
  Plus, Pencil, Trash2, TrendingDown, LogOut, Tag, Settings,
  X, ChevronLeft, ChevronRight, Wallet, LayoutGrid
} from 'lucide-react'

// ─── Helpers ───────────────────────────────────────────────────────────────
function fmt(amount, currency = 'INR') {
  try {
    return new Intl.NumberFormat(currency === 'INR' ? 'en-IN' : undefined, { style: 'currency', currency }).format(amount)
  } catch {
    return `${currency} ${Number(amount).toFixed(2)}`
  }
}
function today() {
  return new Date().toISOString().split('T')[0]
}
function firstOfMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

// ─── Expense Modal ──────────────────────────────────────────────────────────
function ExpenseModal({ expense, categories, onSave, onClose }) {
  const { user } = useAuth()
  const [form, setForm] = useState({
    description: expense?.description || '',
    amount: expense?.amount || '',
    date: expense?.date || today(),
    categoryId: expense?.categoryId || '',
    notes: expense?.notes || '',
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const payload = {
        ...form,
        amount: parseFloat(form.amount),
        categoryId: form.categoryId ? parseInt(form.categoryId) : null,
      }
      if (expense?.id) {
        await expensesApi.update(expense.id, payload)
      } else {
        await expensesApi.create(payload)
      }
      onSave()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
        <div className="flex items-center justify-between p-6 border-b border-gray-100">
          <h2 className="text-lg font-semibold text-gray-900">
            {expense?.id ? 'Edit Expense' : 'Add Expense'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>
          )}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description *</label>
            <input
              name="description" value={form.description} onChange={handleChange} required
              placeholder="e.g. Grocery shopping"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Amount ({user?.currency || 'INR'}) *</label>
              <input
                type="number" name="amount" value={form.amount} onChange={handleChange}
                required min="0.01" step="0.01" placeholder="0.00"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Date *</label>
              <input
                type="date" name="date" value={form.date} onChange={handleChange} required
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <select
              name="categoryId" value={form.categoryId} onChange={handleChange}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">— None —</option>
              {categories.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
            <textarea
              name="notes" value={form.notes} onChange={handleChange} rows={2}
              placeholder="Optional notes…"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
            />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 px-4 py-2 bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 text-white rounded-lg text-sm font-medium">
              {saving ? 'Saving…' : 'Save Expense'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Category Modal ─────────────────────────────────────────────────────────
function CategoryModal({ category, onSave, onClose }) {
  const [form, setForm] = useState({
    name: category?.name || '',
    icon: category?.icon || '',
    color: category?.color || '#3b82f6',
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      if (category?.id) {
        await categoriesApi.update(category.id, form)
      } else {
        await categoriesApi.create(form)
      }
      onSave()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
        <div className="flex items-center justify-between p-6 border-b border-gray-100">
          <h2 className="text-lg font-semibold text-gray-900">
            {category?.id ? 'Edit Category' : 'Add Category'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>
          )}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Name *</label>
            <input
              name="name" value={form.name} onChange={handleChange} required
              placeholder="e.g. Food & Drinks"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Icon (emoji)</label>
              <input
                name="icon" value={form.icon} onChange={handleChange}
                placeholder="🍔"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Color</label>
              <input
                type="color" name="color" value={form.color} onChange={handleChange}
                className="w-full h-[38px] px-1 py-1 border border-gray-300 rounded-lg cursor-pointer"
              />
            </div>
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 px-4 py-2 bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 text-white rounded-lg text-sm font-medium">
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Confirm Dialog ─────────────────────────────────────────────────────────
function ConfirmDialog({ message, onConfirm, onCancel }) {
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm p-6">
        <h3 className="text-base font-semibold text-gray-900 mb-2">Confirm Delete</h3>
        <p className="text-sm text-gray-600 mb-6">{message}</p>
        <div className="flex gap-3">
          <button onClick={onCancel}
            className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50">
            Cancel
          </button>
          <button onClick={onConfirm}
            className="flex-1 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-sm font-medium">
            Delete
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Dashboard ───────────────────────────────────────────────────────────────
export default function DashboardPage() {
  const { user, logout } = useAuth()
  const [tab, setTab] = useState('expenses')

  // Expenses state
  const [expenses, setExpenses] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const PAGE_SIZE = 10

  // Categories state
  const [categories, setCategories] = useState([])

  // Summary state
  const [summary, setSummary] = useState(null)

  // Modal state
  const [expenseModal, setExpenseModal] = useState(null) // null | {} | {id, ...}
  const [categoryModal, setCategoryModal] = useState(null)
  const [confirm, setConfirm] = useState(null) // { message, onConfirm }

  const [loadingExpenses, setLoadingExpenses] = useState(true)

  // Load categories
  const loadCategories = useCallback(async () => {
    try {
      const data = await categoriesApi.list()
      setCategories(data)
    } catch { /* keep showing the last loaded data */ }
  }, [])

  // Load expenses
  const loadExpenses = useCallback(async (p = 0) => {
    setLoadingExpenses(true)
    try {
      const data = await expensesApi.list({ page: p, size: PAGE_SIZE })
      setExpenses(data.content)
      setTotalElements(data.totalElements)
      setPage(p)
    } catch { /* keep showing the last loaded data */ }
    setLoadingExpenses(false)
  }, [])

  // Load summary for current month
  const loadSummary = useCallback(async () => {
    try {
      const data = await expensesApi.summary(firstOfMonth(), today())
      setSummary(data)
    } catch { /* keep showing the last loaded data */ }
  }, [])

  // Initial data load; the loaders set loading state before fetching.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    loadCategories()
    loadExpenses(0)
    loadSummary()
  }, [loadCategories, loadExpenses, loadSummary])
  /* eslint-enable react-hooks/set-state-in-effect */

  const handleDeleteExpense = (exp) => {
    setConfirm({
      message: `Delete "${exp.description}"? This cannot be undone.`,
      onConfirm: async () => {
        await expensesApi.delete(exp.id)
        setConfirm(null)
        loadExpenses(page)
        loadSummary()
      },
    })
  }

  const handleDeleteCategory = (cat) => {
    setConfirm({
      message: `Delete category "${cat.name}"? Expenses in this category will be uncategorized.`,
      onConfirm: async () => {
        await categoriesApi.delete(cat.id)
        setConfirm(null)
        loadCategories()
      },
    })
  }

  const totalPages = Math.ceil(totalElements / PAGE_SIZE)
  const fmtC = (amount) => fmt(amount, user?.currency)

  // Category lookup map
  const catMap = Object.fromEntries(categories.map(c => [c.id, c]))

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 bg-primary-600 rounded-lg flex items-center justify-center">
              <TrendingDown className="w-4 h-4 text-white" />
            </div>
            <span className="font-semibold text-gray-900">ExpenseFlow</span>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-600 hidden sm:block">
              Hi, <strong>{user?.name}</strong>
            </span>
            <Link
              to="/settings"
              className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-primary-600 transition-colors"
            >
              <Settings className="w-4 h-4" />
              <span className="hidden sm:block">Settings</span>
            </Link>
            <button
              onClick={logout}
              className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-red-600 transition-colors"
            >
              <LogOut className="w-4 h-4" />
              <span className="hidden sm:block">Logout</span>
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
          <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-red-50 rounded-lg flex items-center justify-center">
                <Wallet className="w-5 h-5 text-red-500" />
              </div>
              <div>
                <p className="text-xs text-gray-500 font-medium uppercase tracking-wide">This Month</p>
                <p className="text-xl font-bold text-gray-900">
                  {summary ? fmtC(summary.total) : '—'}
                </p>
              </div>
            </div>
          </div>
          <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary-50 rounded-lg flex items-center justify-center">
                <LayoutGrid className="w-5 h-5 text-primary-500" />
              </div>
              <div>
                <p className="text-xs text-gray-500 font-medium uppercase tracking-wide">Total Expenses</p>
                <p className="text-xl font-bold text-gray-900">{totalElements}</p>
              </div>
            </div>
          </div>
          <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-accent-50 rounded-lg flex items-center justify-center">
                <Tag className="w-5 h-5 text-accent-600" />
              </div>
              <div>
                <p className="text-xs text-gray-500 font-medium uppercase tracking-wide">Categories</p>
                <p className="text-xl font-bold text-gray-900">{categories.length}</p>
              </div>
            </div>
          </div>
        </div>

        {/* Top spends by category this month */}
        {summary?.byCategory?.length > 0 && (
          <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100 mb-6">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Spending by Category (This Month)</h3>
            <div className="flex flex-wrap gap-2">
              {summary.byCategory.map((c) => (
                <span key={c.categoryId ?? 'uncategorized'} className="px-3 py-1.5 bg-primary-50 text-primary-700 rounded-full text-sm font-medium">
                  {c.name}: {fmtC(c.total)}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Tabs */}
        <div className="flex items-center gap-1 bg-gray-100 p-1 rounded-xl mb-5 w-fit">
          <button
            onClick={() => setTab('expenses')}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              tab === 'expenses' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            Expenses
          </button>
          <button
            onClick={() => setTab('categories')}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              tab === 'categories' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            Categories
          </button>
        </div>

        {/* ── Expenses Tab ── */}
        {tab === 'expenses' && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-900">All Expenses</h2>
              <button
                onClick={() => setExpenseModal({})}
                className="flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-3.5 py-2 rounded-lg transition-colors"
              >
                <Plus className="w-4 h-4" /> Add Expense
              </button>
            </div>

            {loadingExpenses ? (
              <div className="flex items-center justify-center py-16 text-gray-400 text-sm">Loading…</div>
            ) : expenses.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-gray-400">
                <Wallet className="w-10 h-10 mb-3 opacity-30" />
                <p className="text-sm">No expenses yet. Add your first one!</p>
              </div>
            ) : (
              <>
                <div className="divide-y divide-gray-50">
                  {expenses.map(exp => {
                    const cat = catMap[exp.categoryId]
                    return (
                      <div key={exp.id} className="flex items-center justify-between px-5 py-3.5 hover:bg-gray-50 transition-colors">
                        <div className="flex items-center gap-3 min-w-0">
                          <div
                            className="w-8 h-8 rounded-lg flex items-center justify-center text-sm flex-shrink-0"
                            style={{ backgroundColor: cat?.color ? `${cat.color}22` : '#f3f4f6' }}
                          >
                            {cat?.icon || '💸'}
                          </div>
                          <div className="min-w-0">
                            <p className="text-sm font-medium text-gray-900 truncate">{exp.description}</p>
                            <div className="flex items-center gap-2 mt-0.5">
                              <span className="text-xs text-gray-400">{exp.date}</span>
                              {exp.categoryName && (
                                <span className="text-xs px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-600">
                                  {exp.categoryName}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-3 flex-shrink-0 ml-3">
                          <span className="text-sm font-semibold text-gray-900">{fmtC(exp.amount)}</span>
                          <button
                            onClick={() => setExpenseModal(exp)}
                            className="text-gray-400 hover:text-primary-600 transition-colors"
                          >
                            <Pencil className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDeleteExpense(exp)}
                            className="text-gray-400 hover:text-red-500 transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>

                {/* Pagination */}
                {totalPages > 1 && (
                  <div className="flex items-center justify-between px-5 py-3 border-t border-gray-100">
                    <span className="text-xs text-gray-400">
                      Page {page + 1} of {totalPages} · {totalElements} total
                    </span>
                    <div className="flex gap-1">
                      <button
                        onClick={() => loadExpenses(page - 1)} disabled={page === 0}
                        className="p-1.5 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        <ChevronLeft className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => loadExpenses(page + 1)} disabled={page >= totalPages - 1}
                        className="p-1.5 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        <ChevronRight className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* ── Categories Tab ── */}
        {tab === 'categories' && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-900">Categories</h2>
              <button
                onClick={() => setCategoryModal({})}
                className="flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-3.5 py-2 rounded-lg transition-colors"
              >
                <Plus className="w-4 h-4" /> Add Category
              </button>
            </div>

            {categories.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-gray-400">
                <Tag className="w-10 h-10 mb-3 opacity-30" />
                <p className="text-sm">No categories yet. Add one to organise expenses.</p>
              </div>
            ) : (
              <div className="divide-y divide-gray-50">
                {categories.map(cat => (
                  <div key={cat.id} className="flex items-center justify-between px-5 py-3.5 hover:bg-gray-50 transition-colors">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-9 h-9 rounded-lg flex items-center justify-center text-base"
                        style={{ backgroundColor: cat.color ? `${cat.color}22` : '#f3f4f6' }}
                      >
                        {cat.icon || '🏷️'}
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900">{cat.name}</p>
                        {cat.color && (
                          <div className="flex items-center gap-1 mt-0.5">
                            <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: cat.color }} />
                            <span className="text-xs text-gray-400">{cat.color}</span>
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setCategoryModal(cat)}
                        className="text-gray-400 hover:text-primary-600 transition-colors"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDeleteCategory(cat)}
                        className="text-gray-400 hover:text-red-500 transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      {/* Modals */}
      {expenseModal !== null && (
        <ExpenseModal
          expense={expenseModal}
          categories={categories}
          onSave={() => { setExpenseModal(null); loadExpenses(page); loadSummary() }}
          onClose={() => setExpenseModal(null)}
        />
      )}
      {categoryModal !== null && (
        <CategoryModal
          category={categoryModal}
          onSave={() => { setCategoryModal(null); loadCategories() }}
          onClose={() => setCategoryModal(null)}
        />
      )}
      {confirm && (
        <ConfirmDialog
          message={confirm.message}
          onConfirm={confirm.onConfirm}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  )
}
