import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError } from '../api/client'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from './LoginPage'

vi.mock('../api/endpoints', () => ({
  auth: { login: vi.fn(), me: vi.fn(), register: vi.fn(), logout: vi.fn() },
}))

import { auth as authApi } from '../api/endpoints'
const loginMock = vi.mocked(authApi.login)

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider initialUser={null}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/orgs" element={<p>Workspace picker</p>} />
          <Route path="/orgs/:orgId/tickets" element={<p>Ticket list</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('validates fields before calling the API', async () => {
    renderLogin()
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(screen.getByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(loginMock).not.toHaveBeenCalled()

    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument()
  })

  it('shows a clear message on bad credentials', async () => {
    loginMock.mockRejectedValueOnce(new ApiError({ status: 401, code: 'BAD_CREDENTIALS', detail: 'Invalid email or password' }))
    renderLogin()
    await userEvent.type(screen.getByLabelText('Email'), 'user@acme.test')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
  })

  it('explains connection problems', async () => {
    loginMock.mockRejectedValueOnce(new NetworkError())
    renderLogin()
    await userEvent.type(screen.getByLabelText('Email'), 'user@acme.test')
    await userEvent.type(screen.getByLabelText('Password'), 'password')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/Could not reach the server/)
  })

  it('goes straight to the only workspace after login', async () => {
    loginMock.mockResolvedValueOnce({
      id: 1,
      email: 'user@acme.test',
      fullName: 'Rita',
      memberships: [{ organizationId: 7, organizationName: 'Acme', slug: 'acme', role: 'REQUESTER' }],
    })
    renderLogin()
    await userEvent.type(screen.getByLabelText('Email'), 'user@acme.test')
    await userEvent.type(screen.getByLabelText('Password'), 'password')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByText('Ticket list')).toBeInTheDocument()
    expect(loginMock).toHaveBeenCalledWith('user@acme.test', 'password')
  })
})
