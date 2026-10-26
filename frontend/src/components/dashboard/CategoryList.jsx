import { Pencil, Trash2, Tag, Plus } from 'lucide-react'
import Skeleton from '../ui/Skeleton'
import ErrorState from '../ui/ErrorState'

export default function CategoryList({ categories, error, onRetry, onAdd, onEdit, onDelete }) {
  if (error && !categories) return <ErrorState message={error.message || 'Could not load categories.'} onRetry={onRetry} />
  if (!categories) {
    return (
      <div role="status" aria-label="Loading categories" className="divide-y divide-gray-50">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex items-center gap-3 px-5 py-3.5">
            <Skeleton className="w-9 h-9 rounded-lg" />
            <Skeleton className="h-4 w-32" />
          </div>
        ))}
      </div>
    )
  }
  if (categories.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center px-4">
        <Tag className="w-10 h-10 mb-3 text-gray-300" aria-hidden="true" />
        <p className="text-sm text-gray-500 mb-4">No categories yet. Add one to organise expenses.</p>
        <button
          type="button" onClick={onAdd}
          className="inline-flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-3.5 py-2 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
        >
          <Plus className="w-4 h-4" aria-hidden="true" /> Add category
        </button>
      </div>
    )
  }
  return (
    <ul className="divide-y divide-gray-50">
      {categories.map((cat) => (
        <li key={cat.id} className="flex items-center justify-between px-5 py-3.5 hover:bg-gray-50 transition-colors">
          <div className="flex items-center gap-3">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center text-base"
              style={{ backgroundColor: cat.color ? `${cat.color}22` : '#f3f4f6' }}
              aria-hidden="true"
            >
              {cat.icon || '🏷️'}
            </div>
            <div>
              <p className="text-sm font-medium text-gray-900">{cat.name}</p>
              {cat.color && (
                <div className="flex items-center gap-1 mt-0.5">
                  <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: cat.color }} aria-hidden="true" />
                  <span className="text-xs text-gray-400">{cat.color}</span>
                </div>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button" onClick={() => onEdit(cat)} aria-label={`Edit category ${cat.name}`}
              className="p-1 rounded text-gray-400 hover:text-primary-600 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <Pencil className="w-4 h-4" />
            </button>
            <button
              type="button" onClick={() => onDelete(cat)} aria-label={`Delete category ${cat.name}`}
              className="p-1 rounded text-gray-400 hover:text-red-500 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </li>
      ))}
    </ul>
  )
}
