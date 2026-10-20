import { Link, useParams, useSearchParams } from 'react-router-dom'
import { describeError } from '../api/client'
import { tickets as ticketApi, organizations as orgApi } from '../api/endpoints'
import { TICKET_PRIORITIES, TICKET_STATUSES, type TicketFilter, type TicketPriority, type TicketStatus } from '../api/types'
import { useAsync } from '../api/useAsync'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, EmptyState, Input, Pagination, PriorityBadge, Select, Spinner, StatusBadge, formatDate, humanize } from '../components/ui'

export function TicketsPage() {
  const { orgId: orgIdParam } = useParams()
  const orgId = Number(orgIdParam)
  const { user } = useAuth()
  const role = user?.memberships.find((m) => m.organizationId === orgId)?.role
  const [params, setParams] = useSearchParams()

  const filter: TicketFilter = {
    status: (params.get('status') as TicketStatus) || '',
    priority: (params.get('priority') as TicketPriority) || '',
    assigneeId: params.get('assigneeId') ? Number(params.get('assigneeId')) : '',
    unassigned: params.get('unassigned') === 'true',
    q: params.get('q') ?? '',
    page: Number(params.get('page') ?? 0),
    size: 10,
    sort: params.get('sort') ?? 'createdAt,desc',
  }

  const update = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v === undefined || v === '') next.delete(k)
      else next.set(k, v)
    }
    if (!('page' in patch)) next.delete('page')
    setParams(next)
  }

  const list = useAsync(() => ticketApi.list(orgId, filter), [orgId, params.toString()])
  const members = useAsync(() => orgApi.members(orgId), [orgId])
  const technicians = (members.data ?? []).filter((m) => m.role !== 'REQUESTER')

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Tickets</h1>
        <Link to={`/orgs/${orgId}/tickets/new`}>
          <Button>New ticket</Button>
        </Link>
      </div>

      <div className="grid gap-2 rounded-lg border border-slate-200 bg-white p-3 sm:grid-cols-5" role="search">
        <Input placeholder="Search title…" aria-label="Search" value={filter.q} onChange={(e) => update({ q: e.target.value })} />
        <Select aria-label="Status" value={filter.status} onChange={(e) => update({ status: e.target.value })}>
          <option value="">All statuses</option>
          {TICKET_STATUSES.map((s) => (
            <option key={s} value={s}>
              {humanize(s)}
            </option>
          ))}
        </Select>
        <Select aria-label="Priority" value={filter.priority} onChange={(e) => update({ priority: e.target.value })}>
          <option value="">All priorities</option>
          {TICKET_PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {humanize(p)}
            </option>
          ))}
        </Select>
        {role !== 'REQUESTER' && (
          <Select
            aria-label="Assignee"
            value={filter.unassigned ? 'unassigned' : String(filter.assigneeId ?? '')}
            onChange={(e) =>
              e.target.value === 'unassigned'
                ? update({ unassigned: 'true', assigneeId: undefined })
                : update({ unassigned: undefined, assigneeId: e.target.value })
            }
          >
            <option value="">Any assignee</option>
            <option value="unassigned">Unassigned</option>
            {technicians.map((t) => (
              <option key={t.userId} value={t.userId}>
                {t.fullName}
              </option>
            ))}
          </Select>
        )}
        <Select aria-label="Sort" value={filter.sort} onChange={(e) => update({ sort: e.target.value })}>
          <option value="createdAt,desc">Newest first</option>
          <option value="createdAt,asc">Oldest first</option>
          <option value="updatedAt,desc">Recently updated</option>
          <option value="priority,desc">Priority</option>
        </Select>
      </div>

      {list.status === 'error' && (
        <Alert>
          {describeError(list.error)}{' '}
          <button className="underline" onClick={list.reload}>
            Retry
          </button>
        </Alert>
      )}
      {list.status === 'loading' && !list.data && <Spinner label="Loading tickets…" />}
      {list.data && list.data.content.length === 0 && (
        <EmptyState
          title="No tickets match"
          description={params.toString() ? 'Try clearing some filters.' : 'Create the first ticket for this workspace.'}
          action={
            params.toString() ? (
              <Button variant="secondary" onClick={() => setParams(new URLSearchParams())}>
                Clear filters
              </Button>
            ) : undefined
          }
        />
      )}
      {list.data && list.data.content.length > 0 && (
        <div className={`overflow-hidden rounded-lg border border-slate-200 bg-white ${list.status === 'loading' ? 'opacity-60' : ''}`}>
          <table className="w-full text-sm" data-testid="ticket-table">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2">#</th>
                <th className="px-4 py-2">Title</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Priority</th>
                <th className="px-4 py-2">Assignee</th>
                <th className="px-4 py-2">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.data.content.map((t) => (
                <tr key={t.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2 text-slate-500">{t.id}</td>
                  <td className="px-4 py-2">
                    <Link to={`/orgs/${orgId}/tickets/${t.id}`} className="font-medium text-indigo-700 hover:underline">
                      {t.title}
                    </Link>
                    <p className="text-xs text-slate-500">by {t.requester.fullName}</p>
                  </td>
                  <td className="px-4 py-2">
                    <StatusBadge status={t.status} />
                  </td>
                  <td className="px-4 py-2">
                    <PriorityBadge priority={t.priority} />
                  </td>
                  <td className="px-4 py-2 text-slate-700">{t.assignee?.fullName ?? <span className="text-slate-400">Unassigned</span>}</td>
                  <td className="px-4 py-2 text-slate-500">{formatDate(t.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-4 pb-3">
            <p className="pt-3 text-xs text-slate-500">{list.data.totalElements} tickets</p>
            <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={(p) => update({ page: String(p) })} />
          </div>
        </div>
      )}
    </div>
  )
}
