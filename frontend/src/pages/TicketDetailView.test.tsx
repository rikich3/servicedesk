import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { Member, TicketDetail } from '../api/types'
import { TicketDetailView } from './TicketDetailPage'

const detail: TicketDetail = {
  ticket: {
    id: 42,
    title: 'Cannot sign in',
    description: 'Password rejected',
    status: 'OPEN',
    priority: 'HIGH',
    requester: { id: 1, fullName: 'Rita Requester' },
    version: 3,
    reopenCount: 0,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
  },
  comments: [],
  events: [{ id: 1, actor: { id: 1, fullName: 'Rita Requester' }, type: 'CREATED', newValue: 'OPEN', createdAt: '2026-10-01T10:00:00Z' }],
  permissions: { allowedTransitions: ['IN_PROGRESS'], canAssign: true, canClaim: false, canChangePriority: true, canComment: true },
}
const members: Member[] = [
  { userId: 2, email: 't@acme.test', fullName: 'Tom Technician', role: 'TECHNICIAN', joinedAt: '2026-01-01T00:00:00Z' },
  { userId: 1, email: 'r@acme.test', fullName: 'Rita Requester', role: 'REQUESTER', joinedAt: '2026-01-01T00:00:00Z' },
]

function renderView(overrides: Partial<Parameters<typeof TicketDetailView>[0]> = {}) {
  const props = {
    orgId: 1,
    detail,
    members,
    currentUserId: 9,
    onUpdate: vi.fn().mockResolvedValue(undefined),
    onComment: vi.fn().mockResolvedValue(undefined),
    onReload: vi.fn(),
    ...overrides,
  }
  render(
    <MemoryRouter>
      <TicketDetailView {...props} />
    </MemoryRouter>,
  )
  return props
}

describe('TicketDetailView', () => {
  it('sends the current version with every update', async () => {
    const props = renderView()
    await userEvent.click(screen.getByTestId('transition-IN_PROGRESS'))
    expect(props.onUpdate).toHaveBeenCalledWith({ version: 3, status: 'IN_PROGRESS' })
    await userEvent.selectOptions(screen.getByLabelText('Assignee'), '2')
    expect(props.onUpdate).toHaveBeenCalledWith({ version: 3, assigneeId: 2 })
  })

  it('offers a reload when the server reports a stale version', async () => {
    const props = renderView({
      onUpdate: vi.fn().mockRejectedValue(new ApiError({ status: 409, code: 'STALE_VERSION', detail: 'stale' })),
    })
    await userEvent.click(screen.getByTestId('transition-IN_PROGRESS'))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/Someone else changed this ticket/)
    await userEvent.click(screen.getByRole('button', { name: /reload ticket/i }))
    expect(props.onReload).toHaveBeenCalled()
  })

  it('hides actions the caller is not allowed to take', () => {
    renderView({
      detail: { ...detail, permissions: { allowedTransitions: [], canAssign: false, canClaim: false, canChangePriority: false, canComment: true } },
    })
    expect(screen.queryByLabelText('Assignee')).not.toBeInTheDocument()
    expect(screen.getByText(/No status changes available/)).toBeInTheDocument()
    expect(screen.getByText('Unassigned')).toBeInTheDocument()
  })

  it('renders the history in words', () => {
    renderView({
      detail: {
        ...detail,
        events: [
          ...detail.events,
          { id: 2, actor: { id: 5, fullName: 'Alice Admin' }, type: 'ASSIGNED', newValue: 'Tom Technician', createdAt: '2026-10-01T11:00:00Z' },
          { id: 3, actor: { id: 2, fullName: 'Tom Technician' }, type: 'STATUS_CHANGED', oldValue: 'OPEN', newValue: 'IN_PROGRESS', createdAt: '2026-10-01T12:00:00Z' },
        ],
      },
    })
    const history = screen.getByTestId('history')
    expect(history).toHaveTextContent('Alice Admin assigned to Tom Technician')
    expect(history).toHaveTextContent('Tom Technician changed status from Open to In progress')
  })

  it('refuses to post an empty comment', async () => {
    const props = renderView()
    await userEvent.click(screen.getByRole('button', { name: /post comment/i }))
    expect(screen.getByText('Write something before posting')).toBeInTheDocument()
    expect(props.onComment).not.toHaveBeenCalled()
  })
})
