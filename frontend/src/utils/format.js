export function formatMoney(amount, currency = 'INR') {
  const value = Number(amount) || 0
  try {
    return new Intl.NumberFormat(currency === 'INR' ? 'en-IN' : undefined, {
      style: 'currency',
      currency,
    }).format(value)
  } catch {
    return `${currency} ${value.toFixed(2)}`
  }
}

export function currencySymbol(currency = 'INR') {
  try {
    const part = new Intl.NumberFormat(undefined, { style: 'currency', currency })
      .formatToParts(0)
      .find((p) => p.type === 'currency')
    return part?.value || currency
  } catch {
    return currency
  }
}

/**
 * Accepts both summary shapes and returns `{ total, byCategory: [{categoryId, name, color, icon, total}] }`:
 *  - new: `byCategory` is an array of objects
 *  - old: `byCategory` is a `{ name: amount }` map
 */
export function normalizeSummary(raw) {
  if (!raw) return { total: 0, byCategory: [] }
  const source = raw.byCategory
  let byCategory = []
  if (Array.isArray(source)) {
    byCategory = source.map((c) => ({
      categoryId: c.categoryId ?? null,
      name: c.name || 'Uncategorized',
      color: c.color || null,
      icon: c.icon || null,
      total: Number(c.total) || 0,
    }))
  } else if (source && typeof source === 'object') {
    byCategory = Object.entries(source).map(([name, total]) => ({
      categoryId: null,
      name,
      color: null,
      icon: null,
      total: Number(total) || 0,
    }))
  }
  byCategory.sort((a, b) => b.total - a.total)
  return { total: Number(raw.total) || 0, byCategory }
}
