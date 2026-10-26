import { useId, useState } from 'react'
import Modal from '../ui/Modal'
import Field from '../ui/Field'
import { categoriesApi } from '../../services/api'
import { useToast } from '../toast/toastContext'
import { fieldProps, inputCls, parseApiError } from '../../utils/forms'

export default function CategoryModal({ category, onSave, onClose }) {
  const toast = useToast()
  const uid = useId()
  const isEdit = Boolean(category?.id)
  const [form, setForm] = useState({
    name: category?.name || '',
    icon: category?.icon || '',
    color: category?.color || '#3b82f6',
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
      if (isEdit) await categoriesApi.update(category.id, form)
      else await categoriesApi.create(form)
      toast.success(isEdit ? 'Category updated' : 'Category added')
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
    <Modal title={isEdit ? 'Edit Category' : 'Add Category'} size="sm" onClose={onClose} dismissible={!saving}>
      <form onSubmit={handleSubmit} className="p-6 space-y-4">
        {error && (
          <div role="alert" className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>
        )}
        <Field id={id('name')} label="Name" required error={fieldErrors.name}>
          <input
            {...fieldProps(id('name'), fieldErrors.name)}
            name="name" value={form.name} onChange={handleChange} required maxLength={100}
            placeholder="e.g. Food & Drinks" data-autofocus
            className={inputCls}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field id={id('icon')} label="Icon (emoji)" error={fieldErrors.icon}>
            <input
              {...fieldProps(id('icon'), fieldErrors.icon)}
              name="icon" value={form.icon} onChange={handleChange}
              placeholder="🍔"
              className={inputCls}
            />
          </Field>
          <Field id={id('color')} label="Color" error={fieldErrors.color}>
            <input
              {...fieldProps(id('color'), fieldErrors.color)}
              type="color" name="color" value={form.color} onChange={handleChange}
              className="w-full h-[38px] px-1 py-1 border border-gray-300 rounded-lg cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            />
          </Field>
        </div>
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
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
