import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import Modal from './Modal'

function Stacked({ onOuterClose, onInnerClose }) {
  const [inner, setInner] = useState(false)
  return (
    <Modal title="Outer" onClose={onOuterClose}>
      <button type="button" onClick={() => setInner(true)}>Open inner</button>
      {inner && (
        <Modal
          title="Inner"
          onClose={() => {
            onInnerClose()
            setInner(false)
          }}
        >
          <p>Inner body</p>
        </Modal>
      )}
    </Modal>
  )
}

describe('Modal stack', () => {
  it('Escape closes only the topmost modal', async () => {
    const user = userEvent.setup()
    const onOuterClose = vi.fn()
    const onInnerClose = vi.fn()
    render(<Stacked onOuterClose={onOuterClose} onInnerClose={onInnerClose} />)

    await user.click(screen.getByRole('button', { name: 'Open inner' }))
    expect(screen.getByText('Inner body')).not.toBeNull()

    await user.keyboard('{Escape}')
    expect(onInnerClose).toHaveBeenCalledTimes(1)
    expect(onOuterClose).not.toHaveBeenCalled()

    await user.keyboard('{Escape}')
    expect(onOuterClose).toHaveBeenCalledTimes(1)
    expect(onInnerClose).toHaveBeenCalledTimes(1)
  })
})
