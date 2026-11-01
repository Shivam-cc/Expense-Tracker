import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import SettingsPage from './SettingsPage'

const logout = vi.fn()
const updateUser = vi.fn()
const applySession = vi.fn()
const user = { name: 'Ann', email: 'ann@example.com', currency: 'INR' }

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user, logout, updateUser, applySession }),
}))

const deleteAccount = vi.fn()

vi.mock('../services/api', () => ({
  userApi: {
    deleteAccount: (...args) => deleteAccount(...args),
    update: vi.fn(),
    changePassword: vi.fn(),
    logoutAll: vi.fn(),
  },
}))

function renderSettings() {
  return render(
    <MemoryRouter initialEntries={['/settings']}>
      <Routes>
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/dashboard" element={<h1>Dashboard</h1>} />
        <Route path="/" element={<h1>Home</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

async function openDeleteModal(u) {
  await u.click(screen.getByRole('tab', { name: 'Danger zone' }))
  await u.click(screen.getByRole('button', { name: /Delete my account/ }))
}

describe('SettingsPage', () => {
  beforeEach(() => {
    logout.mockReset()
    deleteAccount.mockReset()
  })

  it('renders the profile section with the current user details', () => {
    renderSettings()

    expect(screen.getByRole('heading', { name: 'Profile' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('Ann')
    expect(screen.getByLabelText('Email')).toHaveValue('ann@example.com')
    expect(screen.getByLabelText('Email')).toBeDisabled()
  })

  it('links back to the dashboard', async () => {
    const u = userEvent.setup()
    renderSettings()

    const link = screen.getByRole('link', { name: /Back to dashboard/ })
    expect(link).toHaveAttribute('href', '/dashboard')

    await u.click(link)
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })

  it('does not call the API when the delete button is first clicked', async () => {
    const u = userEvent.setup()
    renderSettings()

    await openDeleteModal(u)

    expect(screen.getByText('Confirm with your password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete forever' })).toBeDisabled()
    expect(deleteAccount).not.toHaveBeenCalled()
  })

  it('does not call the API when the confirmation is cancelled', async () => {
    const u = userEvent.setup()
    const { container } = renderSettings()

    await openDeleteModal(u)
    await u.type(container.querySelector('input[type="password"]'), 'Passw0rd123')
    await u.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('button', { name: 'Delete forever' })).not.toBeInTheDocument()
    expect(deleteAccount).not.toHaveBeenCalled()
  })

  it('deletes the account with the confirmed password, then logs out and goes home', async () => {
    deleteAccount.mockResolvedValue(undefined)
    logout.mockResolvedValue(undefined)
    const u = userEvent.setup()
    const { container } = renderSettings()

    await openDeleteModal(u)
    await u.type(container.querySelector('input[type="password"]'), 'Passw0rd123')
    await u.click(screen.getByRole('button', { name: 'Delete forever' }))

    expect(deleteAccount).toHaveBeenCalledWith('Passw0rd123')
    await waitFor(() => expect(logout).toHaveBeenCalled())
    expect(await screen.findByRole('heading', { name: 'Home' })).toBeInTheDocument()
  })
})
