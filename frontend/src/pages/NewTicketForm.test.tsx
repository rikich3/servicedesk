import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { NewTicketForm } from './NewTicketPage'

describe('NewTicketForm', () => {
  it('requires title and description', async () => {
    const onSubmit = vi.fn()
    render(<NewTicketForm onSubmit={onSubmit} />)
    await userEvent.click(screen.getByRole('button', { name: /create ticket/i }))
    expect(screen.getByText('Title is required')).toBeInTheDocument()
    expect(screen.getByText(/Describe the problem/)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits trimmed values with the chosen priority', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<NewTicketForm onSubmit={onSubmit} />)
    await userEvent.type(screen.getByLabelText('Title'), '  VPN drops  ')
    await userEvent.type(screen.getByLabelText('Description'), 'Every 10 minutes')
    await userEvent.selectOptions(screen.getByLabelText('Priority'), 'URGENT')
    await userEvent.click(screen.getByRole('button', { name: /create ticket/i }))
    expect(onSubmit).toHaveBeenCalledWith('VPN drops', 'Every 10 minutes', 'URGENT')
  })

  it('maps server-side validation errors to fields', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({ status: 400, code: 'VALIDATION_ERROR', errors: [{ field: 'title', message: 'size must be between 0 and 160' }] }),
    )
    render(<NewTicketForm onSubmit={onSubmit} />)
    await userEvent.type(screen.getByLabelText('Title'), 'x')
    await userEvent.type(screen.getByLabelText('Description'), 'y')
    await userEvent.click(screen.getByRole('button', { name: /create ticket/i }))
    expect(await screen.findByText('size must be between 0 and 160')).toBeInTheDocument()
  })
})
