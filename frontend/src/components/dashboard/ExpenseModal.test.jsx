import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ExpenseModal from './ExpenseModal'

vi.mock('../toast/toastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))

const categories = [{ id: 1, name: 'Food', icon: '🍔' }]

function renderModal(props = {}) {
  const onAddCategory = vi.fn()
  render(
    <ExpenseModal
      expense={null}
      categories={[]}
      currency="INR"
      onSave={vi.fn()}
      onClose={vi.fn()}
      onAddCategory={onAddCategory}
      {...props}
    />,
  )
  return { onAddCategory }
}

describe('ExpenseModal category field', () => {
  it('labels the empty choice Uncategorized and shows no hint when categories exist', () => {
    renderModal({ categories })

    expect(screen.getByRole('option', { name: 'Uncategorized' })).not.toBeNull()
    expect(screen.queryByText(/No categories yet/)).toBeNull()
    expect(screen.queryByRole('button', { name: 'Add a category' })).toBeNull()
  })

  it('offers to add a category when the user has none', async () => {
    const user = userEvent.setup()
    const { onAddCategory } = renderModal({ categories: [] })

    expect(screen.getByText(/No categories yet\./)).not.toBeNull()
    await user.click(screen.getByRole('button', { name: 'Add a category' }))
    expect(onAddCategory).toHaveBeenCalledTimes(1)
  })

  it('shows a load failure instead of the empty message when categories fail to load', () => {
    renderModal({ categories: [], categoriesError: new Error('boom') })

    expect(screen.getByText('Categories could not be loaded.')).not.toBeNull()
    expect(screen.queryByText(/No categories yet/)).toBeNull()
  })

  it('hides the hint while categories are still loading', () => {
    renderModal({ categories: [], categoriesLoading: true })

    expect(screen.queryByText(/No categories yet/)).toBeNull()
    expect(screen.queryByText('Categories could not be loaded.')).toBeNull()
  })
})
