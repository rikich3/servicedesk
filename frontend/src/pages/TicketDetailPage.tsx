import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, describeError } from '../api/client'
import { organizations as orgApi, tickets as ticketApi } from '../api/endpoints'
import { TICKET_PRIORITIES, type Member, type TicketDetail, type TicketEvent, type TicketPriority, type TicketStatus, type UpdateTicketRequest } from '../api/types'
import { useAsync } from '../api/useAsync'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Card, PriorityBadge, Select, Spinner, StatusBadge, Textarea, formatDate, humanize } from '../components/ui'

const transitionLabels: Record<TicketStatus, string> = {
  OPEN: 'Reopen / return to queue',
  IN_PROGRESS: 'Start working',
  RESOLVED: 'Mark as resolved',
  CLOSED: 'Close',
}

function describeEvent(e: TicketEvent): string {
  switch (e.type) {
    case 'CREATED':
      return 'created the ticket'
    case 'ASSIGNED':
      return e.oldValue ? `reassigned from ${e.oldValue} to ${e.newValue}` : `assigned to ${e.newValue}`
    case 'UNASSIGNED':
      return `unassigned ${e.oldValue}`
    case 'STATUS_CHANGED':
      return `changed status from ${humanize(e.oldValue ?? '')} to ${humanize(e.newValue ?? '')}`
    case 'PRIORITY_CHANGED':
      return `changed priority from ${humanize(e.oldValue ?? '')} to ${humanize(e.newValue ?? '')}`
    case 'COMMENTED':
      return 'added a comment'
  }
}

export interface TicketDetailViewProps {
  orgId: number
  detail: TicketDetail
  members: Member[]
  currentUserId: number
  onUpdate: (body: UpdateTicketRequest) => Promise<void>
  onComment: (body: string) => Promise<void>
  onReload: () => void
}

/** Presentational component (no routing/network) so conflict and permission states are testable. */
export function TicketDetailView({ orgId, detail, members, currentUserId, onUpdate, onComment, onReload }: TicketDetailViewProps) {
  const { ticket, comments, events, permissions } = detail
  const [error, setError] = useState<{ message: string; stale: boolean } | null>(null)
  const [busy, setBusy] = useState(false)
  const [comment, setComment] = useState('')
  const [commentError, setCommentError] = useState<string | null>(null)
  const technicians = members.filter((m) => m.role !== 'REQUESTER')

  const run = async (body: Omit<UpdateTicketRequest, 'version'>) => {
    setError(null)
    setBusy(true)
    try {
      await onUpdate({ version: ticket.version, ...body })
    } catch (err) {
      setError({ message: describeError(err), stale: err instanceof ApiError && err.code === 'STALE_VERSION' })
    } finally {
      setBusy(false)
    }
  }

  const submitComment = async (e: FormEvent) => {
    e.preventDefault()
    if (!comment.trim()) {
      setCommentError('Write something before posting')
      return
    }
    setCommentError(null)
    setBusy(true)
    try {
      await onComment(comment.trim())
      setComment('')
    } catch (err) {
      setCommentError(describeError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <Link to={`/orgs/${orgId}/tickets`} className="text-sm text-indigo-600 hover:underline">
            ← Back to tickets
          </Link>
          <h1 className="text-xl font-semibold" data-testid="ticket-title">
            #{ticket.id} {ticket.title}
          </h1>
          <p className="text-sm text-slate-500">
            Requested by {ticket.requester.fullName} · {formatDate(ticket.createdAt)}
            {ticket.reopenCount > 0 && ` · reopened ${ticket.reopenCount}×`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={ticket.status} />
          <PriorityBadge priority={ticket.priority} />
        </div>
      </div>

      {error && (
        <Alert>
          {error.message}{' '}
          {error.stale && (
            <button className="font-medium underline" onClick={onReload}>
              Reload ticket
            </button>
          )}
        </Alert>
      )}

      <div className="grid gap-4 lg:grid-cols-[2fr_1fr]">
        <div className="space-y-4">
          <Card title="Description">
            <p className="whitespace-pre-wrap text-sm text-slate-800">{ticket.description}</p>
          </Card>

          <Card title={`Comments (${comments.length})`}>
            {comments.length === 0 ? (
              <p className="text-sm text-slate-500">No comments yet.</p>
            ) : (
              <ul className="space-y-3" data-testid="comments">
                {comments.map((c) => (
                  <li key={c.id} className="rounded-md bg-slate-50 p-3">
                    <p className="text-xs text-slate-500">
                      <span className="font-medium text-slate-700">{c.author.fullName}</span> · {formatDate(c.createdAt)}
                    </p>
                    <p className="mt-1 whitespace-pre-wrap text-sm">{c.body}</p>
                  </li>
                ))}
              </ul>
            )}
            {permissions.canComment && (
              <form onSubmit={submitComment} className="mt-4 space-y-2" noValidate>
                <Textarea aria-label="New comment" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Reply or ask for more information…" />
                {commentError && (
                  <p className="text-xs text-rose-600" role="alert">
                    {commentError}
                  </p>
                )}
                <Button type="submit" variant="secondary" disabled={busy}>
                  Post comment
                </Button>
              </form>
            )}
          </Card>
        </div>

        <div className="space-y-4">
          <Card title="Actions">
            <div className="space-y-3 text-sm">
              <div>
                <p className="text-xs font-medium uppercase text-slate-500">Assignee</p>
                {permissions.canAssign ? (
                  <Select
                    aria-label="Assignee"
                    value={ticket.assignee?.id ?? ''}
                    disabled={busy}
                    onChange={(e) => (e.target.value === '' ? run({ unassign: true }) : run({ assigneeId: Number(e.target.value) }))}
                  >
                    <option value="">Unassigned</option>
                    {technicians.map((t) => (
                      <option key={t.userId} value={t.userId}>
                        {t.fullName}
                      </option>
                    ))}
                  </Select>
                ) : (
                  <p className="text-slate-800">{ticket.assignee?.fullName ?? 'Unassigned'}</p>
                )}
                {permissions.canClaim && (
                  <Button variant="secondary" className="mt-2" disabled={busy} onClick={() => run({ assigneeId: currentUserId })}>
                    Claim this ticket
                  </Button>
                )}
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-slate-500">Priority</p>
                {permissions.canChangePriority ? (
                  <Select aria-label="Priority" value={ticket.priority} disabled={busy} onChange={(e) => run({ priority: e.target.value as TicketPriority })}>
                    {TICKET_PRIORITIES.map((p) => (
                      <option key={p} value={p}>
                        {humanize(p)}
                      </option>
                    ))}
                  </Select>
                ) : (
                  <PriorityBadge priority={ticket.priority} />
                )}
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-slate-500">Status</p>
                {permissions.allowedTransitions.length === 0 ? (
                  <p className="text-slate-500">No status changes available for you right now.</p>
                ) : (
                  <div className="mt-1 flex flex-col gap-2">
                    {permissions.allowedTransitions.map((s) => (
                      <Button key={s} disabled={busy} onClick={() => run({ status: s })} data-testid={`transition-${s}`}>
                        {transitionLabels[s]}
                      </Button>
                    ))}
                  </div>
                )}
              </div>
              <dl className="grid grid-cols-2 gap-1 border-t border-slate-200 pt-3 text-xs text-slate-500">
                <dt>Version</dt>
                <dd data-testid="ticket-version">{ticket.version}</dd>
                <dt>Resolved</dt>
                <dd>{formatDate(ticket.resolvedAt)}</dd>
                <dt>Closed</dt>
                <dd>{formatDate(ticket.closedAt)}</dd>
              </dl>
            </div>
          </Card>

          <Card title="History">
            <ol className="space-y-2 text-sm" data-testid="history">
              {events.map((e) => (
                <li key={e.id} className="flex gap-2">
                  <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-indigo-400" aria-hidden />
                  <div>
                    <p>
                      <span className="font-medium">{e.actor.fullName}</span> {describeEvent(e)}
                    </p>
                    <p className="text-xs text-slate-500">{formatDate(e.createdAt)}</p>
                  </div>
                </li>
              ))}
            </ol>
          </Card>
        </div>
      </div>
    </div>
  )
}

export function TicketDetailPage() {
  const { orgId: orgIdParam, ticketId: ticketIdParam } = useParams()
  const orgId = Number(orgIdParam)
  const ticketId = Number(ticketIdParam)
  const { user } = useAuth()
  const detail = useAsync(() => ticketApi.get(orgId, ticketId), [orgId, ticketId])
  const members = useAsync(() => orgApi.members(orgId), [orgId])

  if (detail.status === 'error' && !detail.data) {
    return (
      <Alert>
        {describeError(detail.error)}{' '}
        <Link to={`/orgs/${orgId}/tickets`} className="underline">
          Back to tickets
        </Link>
      </Alert>
    )
  }
  if (!detail.data) return <Spinner label="Loading ticket…" />

  return (
    <TicketDetailView
      orgId={orgId}
      detail={detail.data}
      members={members.data ?? []}
      currentUserId={user!.id}
      onUpdate={async (body) => {
        await ticketApi.update(orgId, ticketId, body)
        detail.reload()
      }}
      onComment={async (body) => {
        await ticketApi.comment(orgId, ticketId, body)
        detail.reload()
      }}
      onReload={detail.reload}
    />
  )
}
