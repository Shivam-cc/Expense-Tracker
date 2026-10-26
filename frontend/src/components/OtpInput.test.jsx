import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import OtpInput from './OtpInput'

function Harness({ initial = '', onChange = () => {}, disabled = false }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <OtpInput
        value={value}
        disabled={disabled}
        onChange={(v) => { setValue(v); onChange(v) }}
      />
      <output data-testid="value">{value}</output>
    </>
  )
}

const boxes = () => screen.getAllByRole('textbox')
const current = () => screen.getByTestId('value').textContent

describe('OtpInput', () => {
  it('renders six boxes', () => {
    render(<Harness />)
    expect(boxes()).toHaveLength(6)
  })

  it('auto-advances focus while typing digits', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(boxes()[0])
    await user.keyboard('123')

    expect(current()).toBe('123')
    expect(boxes()[3]).toHaveFocus()
  })

  it('ignores non-digit characters', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(boxes()[0])
    await user.keyboard('a-b')

    expect(current()).toBe('')
    expect(boxes()[0]).toHaveFocus()
  })

  it('fills every box from a pasted code and strips formatting', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(boxes()[0])
    await user.paste('12 34-56 789')

    expect(current()).toBe('123456')
    boxes().forEach((box, i) => expect(box).toHaveValue('123456'[i]))
  })

  it('backspace clears the current digit, then steps back and clears the previous one', async () => {
    const user = userEvent.setup()
    render(<Harness initial="12" />)

    await user.click(boxes()[1])
    await user.keyboard('{Backspace}')
    expect(current()).toBe('1')

    await user.keyboard('{Backspace}')
    expect(boxes()[0]).toHaveFocus()
    expect(current()).toBe('')
  })

  it('arrow keys move between boxes', async () => {
    const user = userEvent.setup()
    render(<Harness initial="123" />)

    await user.click(boxes()[1])
    await user.keyboard('{ArrowRight}')
    expect(boxes()[2]).toHaveFocus()
    await user.keyboard('{ArrowLeft}{ArrowLeft}')
    expect(boxes()[0]).toHaveFocus()
  })

  it('reports the full code through onChange', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)

    await user.click(boxes()[0])
    await user.keyboard('987654')

    expect(onChange).toHaveBeenLastCalledWith('987654')
  })

  it('does not accept input when disabled', () => {
    render(<Harness disabled />)
    boxes().forEach((box) => expect(box).toBeDisabled())
  })
})
