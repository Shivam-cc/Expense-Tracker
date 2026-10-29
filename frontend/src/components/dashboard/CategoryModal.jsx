import { useId, useState } from 'react'
import Modal from '../ui/Modal'
import Field from '../ui/Field'
import { categoriesApi } from '../../services/api'
import { useToast } from '../toast/toastContext'
import { fieldProps, inputCls, parseApiError } from '../../utils/forms'
import { CATEGORY_COLORS, CATEGORY_ICONS, DEFAULT_CATEGORY_COLOR, DEFAULT_CATEGORY_ICON } from '../../utils/categoryPresets'

export default function CategoryModal({ category, onSave, onClose }) {
  const toast = useToast()
  const uid = useId()
  const isEdit = Boolean(category?.id)
  const [form, setForm] = useState({
    name: category?.name || '',
    icon: category?.icon || DEFAULT_CATEGORY_ICON,
    color: category?.color || DEFAULT_CATEGORY_COLOR,
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
        <fieldset>
          <legend className="text-sm font-medium text-gray-700 mb-2">Icon</legend>
          <div className="grid grid-cols-8 gap-1.5">
            {CATEGORY_ICONS.map(({ emoji, label }) => {
              const selected = form.icon === emoji
              return (
                <button
                  key={emoji}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, icon: emoji }))}
                  aria-label={label}
                  aria-pressed={selected}
                  title={label}
                  className={`h-9 rounded-lg text-base flex items-center justify-center transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${selected ? 'bg-primary-50 ring-2 ring-primary-500' : 'hover:bg-gray-100'}`}
                >
                  {emoji}
                </button>
              )
            })}
          </div>
          <div className="mt-3">
            <Field id={id('icon')} label="Or any emoji" error={fieldErrors.icon}>
              <input
                {...fieldProps(id('icon'), fieldErrors.icon)}
                name="icon" value={form.icon} onChange={handleChange} maxLength={16}
                placeholder="e.g. 🐶"
                className={inputCls}
              />
            </Field>
          </div>
        </fieldset>
        <fieldset>
          <legend className="text-sm font-medium text-gray-700 mb-2">Color</legend>
          <div className="flex items-center gap-2">
            {CATEGORY_COLORS.map((c) => {
              const selected = form.color === c
              return (
                <button
                  key={c}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, color: c }))}
                  aria-label={`Color ${c}`}
                  aria-pressed={selected}
                  className={`w-6 h-6 rounded-full transition-shadow focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${selected ? 'ring-2 ring-offset-2 ring-gray-900' : ''}`}
                  style={{ backgroundColor: c }}
                />
              )
            })}
            <input
              type="color" name="color" value={form.color} onChange={handleChange}
              aria-label="Custom color" title="Custom color"
              className="w-6 h-6 p-0 border-0 bg-transparent rounded-full cursor-pointer"
            />
          </div>
        </fieldset>
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
