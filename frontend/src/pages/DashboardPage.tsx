import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { describeError } from '../api/client'
import { reports } from '../api/endpoints'
import { TICKET_PRIORITIES, TICKET_STATUSES } from '../api/types'
import { useAsync } from '../api/useAsync'
import { Alert, Card, Select, Spinner, humanize } from '../components/ui'

const ranges = [
  { label: 'Last 7 days', days: 7 },
  { label: 'Last 30 days', days: 30 },
  { label: 'Last 90 days', days: 90 },
]

function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

function Bars({ data }: { data: { label: string; value: number }[] }) {
  const max = Math.max(1, ...data.map((d) => d.value))
  return (
    <ul className="space-y-2">
      {data.map((d) => (
        <li key={d.label} className="text-sm">
          <div className="flex justify-between">
            <span>{d.label}</span>
            <span className="text-slate-500">{d.value}</span>
          </div>
          <div className="mt-1 h-2 rounded bg-slate-100">
            <div className="h-2 rounded bg-indigo-500" style={{ width: `${(d.value / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  )
}

export function DashboardPage() {
  const { orgId: orgIdParam } = useParams()
  const orgId = Number(orgIdParam)
  const [days, setDays] = useState(30)
  const summary = useAsync(() => {
    const to = new Date()
    const from = new Date(to.getTime() - days * 24 * 3600 * 1000)
    return reports.summary(orgId, from.toISOString(), to.toISOString())
  }, [orgId, days])

  if (summary.status === 'error') return <Alert>{describeError(summary.error)}</Alert>
  if (!summary.data) return <Spinner label="Loading indicators…" />
  const s = summary.data
  const hours = (h?: number) => (h == null ? '—' : `${h.toFixed(1)} h`)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <div className="w-44">
          <Select aria-label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {ranges.map((r) => (
              <option key={r.days} value={r.days}>
                {r.label}
              </option>
            ))}
          </Select>
        </div>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Open tickets" value={s.openTickets} hint={`${s.unassignedTickets} unassigned`} />
        <Stat label="Created in period" value={s.createdInPeriod} />
        <Stat label="Resolved in period" value={s.resolvedInPeriod} hint={`${s.reopenedTickets} reopened at least once`} />
        <Stat label="Avg. resolution time" value={hours(s.avgResolutionHours)} hint="created → resolved" />
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Tickets by status">
          <Bars data={TICKET_STATUSES.map((st) => ({ label: humanize(st), value: s.byStatus[st] ?? 0 }))} />
        </Card>
        <Card title="Open tickets by priority">
          <Bars data={TICKET_PRIORITIES.map((p) => ({ label: humanize(p), value: s.byPriority[p] ?? 0 }))} />
        </Card>
      </div>
      <Card title="Workload per technician">
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-slate-500">
            <tr>
              <th className="py-2">Technician</th>
              <th className="py-2">Active</th>
              <th className="py-2">In progress</th>
              <th className="py-2">Resolved (period)</th>
              <th className="py-2">Avg. resolution</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {s.workload.map((w) => (
              <tr key={w.userId}>
                <td className="py-2 font-medium">{w.fullName}</td>
                <td className="py-2">{w.activeAssigned}</td>
                <td className="py-2">{w.inProgress}</td>
                <td className="py-2">{w.resolvedInPeriod}</td>
                <td className="py-2">{hours(w.avgResolutionHours)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  )
}
