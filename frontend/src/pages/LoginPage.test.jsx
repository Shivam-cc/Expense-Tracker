import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import LoginPage from './LoginPage'

const login = vi.fn()

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ login }),
}))

function renderLogin(state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<h1>Dashboard</h1>} />
        <Route path="/forgot-password" element={<h1>Forgot</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

async function fillAndSubmit(user, email = 'Ann@Example.com', password = 'Passw0rd123') {
  await user.type(screen.getByPlaceholderText('you@example.com'), email)
  await user.type(screen.getByPlaceholderText('••••••••'), password)
  await user.click(screen.getByRole('button', { name: 'Sign In' }))
}

describe('LoginPage', () => {
  beforeEach(() => {
    login.mockReset()
  })

  it('submits the lower-cased email and password, then goes to the dashboard', async () => {
    login.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderLogin()

    await fillAndSubmit(user)

    expect(login).toHaveBeenCalledWith('ann@example.com', 'Passw0rd123')
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })

  it('shows the server error and stays on the page when login fails', async () => {
    login.mockRejectedValue(new Error('Invalid email or password'))
    const user = userEvent.setup()
    renderLogin()

    await fillAndSubmit(user)

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Dashboard' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign In' })).toBeEnabled()
  })

  it('disables the button while the request is in flight', async () => {
    let finish
    login.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    const user = userEvent.setup()
    renderLogin()

    await fillAndSubmit(user)

    expect(await screen.findByRole('button', { name: 'Signing in…' })).toBeDisabled()
    finish()
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument())
  })

  it('clears an earlier error when the next attempt starts', async () => {
    login.mockRejectedValueOnce(new Error('Invalid email or password')).mockResolvedValueOnce(undefined)
    const user = userEvent.setup()
    renderLogin()

    await fillAndSubmit(user)
    await screen.findByText('Invalid email or password')
    await user.click(screen.getByRole('button', { name: 'Sign In' }))

    await waitFor(() => expect(screen.queryByText('Invalid email or password')).not.toBeInTheDocument())
  })

  it('shows a notice passed in through navigation state, e.g. after a password reset', () => {
    renderLogin({ notice: 'Password updated. Please sign in.' })

    expect(screen.getByText('Password updated. Please sign in.')).toBeInTheDocument()
  })

  it('links to password recovery', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('link', { name: 'Forgot password?' }))

    expect(await screen.findByRole('heading', { name: 'Forgot' })).toBeInTheDocument()
  })
})
