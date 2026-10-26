import { useId, useRef } from 'react'

/**
 * WAI-ARIA tabs: a tablist with roving tabindex and arrow/Home/End navigation, followed by the
 * tabpanel for the selected tab (its content is passed as `children`).
 */
export default function Tabs({ tabs, value, onChange, label, children }) {
  const prefix = useId()
  const refs = useRef({})

  const handleKeyDown = (e) => {
    const index = tabs.findIndex((t) => t.id === value)
    let next = null
    if (e.key === 'ArrowRight') next = (index + 1) % tabs.length
    else if (e.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = tabs.length - 1
    if (next === null) return
    e.preventDefault()
    const target = tabs[next].id
    onChange(target)
    refs.current[target]?.focus()
  }

  return (
    <>
      <div
        role="tablist"
        aria-label={label}
        onKeyDown={handleKeyDown}
        className="flex items-center gap-1 bg-gray-100 p-1 rounded-xl mb-5 w-fit"
      >
        {tabs.map((t) => {
          const selected = t.id === value
          return (
            <button
              key={t.id}
              ref={(el) => { refs.current[t.id] = el }}
              type="button"
              role="tab"
              id={`${prefix}-tab-${t.id}`}
              aria-selected={selected}
              aria-controls={`${prefix}-panel`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(t.id)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${
                selected ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {t.label}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={`${prefix}-panel`} aria-labelledby={`${prefix}-tab-${value}`}>
        {children}
      </div>
    </>
  )
}
