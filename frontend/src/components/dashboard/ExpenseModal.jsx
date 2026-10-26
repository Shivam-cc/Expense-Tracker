import { useId, useState } from 'react'
import Modal from '../ui/Modal'
import Field from '../ui/Field'
import { expensesApi } from '../../services/api'
import { useToast } from '../toast/toastContext'
import { currencySymbol } from '../../utils/format'
import { today } from '../../utils/date'
import { fieldProps, inputCls, parseApiError } from '../../utils/forms'

export default function ExpenseModal({ expense, categories, currency, onSave, onClose }) {
  const toast = useToast()
  const uid = useId()
  const isEdit = Boolean(expense?.id)
  const [form, setForm] = useState({
    description: expense?.description || '',
    amount: expense?.amount ?? '',
    date: expense?.date || today(),
    categoryId: expense?.categoryId || '',
    notes: expense?.notes || '',
  })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    if (fieldErrors[name]) setFieldErrors((fe) => ({ ...fe, [name]: undefined }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setFieldErrors({})
    setSaving(true)
    try {
      const payload = {
        ...form,
        amount: parseFloat(form.amount),
        categoryId: form.categoryId ? parseInt(form.categoryId, 10) : null,
      }
      if (isEdit) await expensesApi.update(expense.id, payload)
      else await expensesApi.create(payload)
      toast.success(isEdit ? 'Expense updated' : 'Expense added')
      onSave()
    } catch (err) {
      const parsed = parseApiError(err)
      setError(parsed.message)
      setFieldErrors(parsed.fieldErrors)
      toast.error(parsed.message)
    } finally {
      setSaving(false)
    }
  }

  const id = (name) => `${uid}-${name}`

  return (
    <Modal title={isEdit ? 'Edit Expense' : 'Add Expense'} onClose={onClose} dismissible={!saving}>
      <form onSubmit={handleSubmit} className="p-6 space-y-4">
        {error && (
          <div role="alert" className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>
        )}
        <Field id={id('description')} label="Description" required error={fieldErrors.description}>
          <input
            {...fieldProps(id('description'), fieldErrors.description)}
            name="description" value={form.description} onChange={handleChange} required maxLength={255}
            placeholder="e.g. Grocery shopping" data-autofocus
            className={inputCls}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field id={id('amount')} label={`Amount (${currencySymbol(currency)})`} required error={fieldErrors.amount}>
            <input
              {...fieldProps(id('amount'), fieldErrors.amount)}
              type="number" name="amount" value={form.amount} onChange={handleChange}
              required min="0.01" step="0.01" placeholder="0.00" inputMode="decimal"
              className={inputCls}
            />
          </Field>
          <Field id={id('date')} label="Date" required error={fieldErrors.date}>
            <input
              {...fieldProps(id('date'), fieldErrors.date)}
              type="date" name="date" value={form.date} onChange={handleChange} required
              className={inputCls}
            />
          </Field>
        </div>
        <Field id={id('categoryId')} label="Category" error={fieldErrors.categoryId}>
          <select
            {...fieldProps(id('categoryId'), fieldErrors.categoryId)}
            name="categoryId" value={form.categoryId} onChange={handleChange}
            className={inputCls}
          >
            <option value="">— None —</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </Field>
        <Field id={id('notes')} label="Notes" error={fieldErrors.notes}>
          <textarea
            {...fieldProps(id('notes'), fieldErrors.notes)}
            name="notes" value={form.notes} onChange={handleChange} rows={2}
            placeholder="Optional notes…"
            className={`${inputCls} resize-none`}
          />
        </Field>
        <div className="flex gap-3 pt-2">
          <button
            type="button" onClick={onClose} disabled={saving}
            className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit" disabled={saving}
            className="flex-1 px-4 py-2 bg-primary-600 hover:bg-primary-700 disabled:bg-primary-300 text-white rounded-lg text-sm font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
          >
            {saving ? 'Saving…' : 'Save Expense'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
