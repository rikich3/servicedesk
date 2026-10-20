import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, describeError } from '../api/client'
import { organizations as orgApi } from '../api/endpoints'
import { ROLES, type Role } from '../api/types'
import { useAsync } from '../api/useAsync'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Card, Field, Input, RoleBadge, Select, Spinner, formatDate, humanize } from '../components/ui'

export function MembersPage() {
  const { orgId: orgIdParam } = useParams()
  const orgId = Number(orgIdParam)
  const { user, refresh } = useAuth()
  const isAdmin = user?.memberships.find((m) => m.organizationId === orgId)?.role === 'ADMIN'
  const members = useAsync(() => orgApi.members(orgId), [orgId])
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('REQUESTER')
  const [formError, setFormError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const add = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setFormError('Enter a valid email address')
      return
    }
    setBusy(true)
    try {
      await orgApi.addMember(orgId, email.trim(), role)
      setEmail('')
      members.reload()
    } catch (err) {
      if (err instanceof ApiError && err.code === 'NOT_FOUND') setFormError('No account exists with that email. Ask them to register first.')
      else if (err instanceof ApiError && err.code === 'ALREADY_MEMBER') setFormError('That user is already a member.')
      else setFormError(describeError(err))
    } finally {
      setBusy(false)
    }
  }

  const act = async (fn: () => Promise<unknown>) => {
    setError(null)
    setBusy(true)
    try {
      await fn()
      members.reload()
      await refresh()
    } catch (err) {
      setError(describeError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-6 md:grid-cols-[2fr_1fr]">
      <Card title="Members">
        {error && <Alert>{error}</Alert>}
        {members.status === 'error' && <Alert>{describeError(members.error)}</Alert>}
        {!members.data ? (
          <Spinner />
        ) : (
          <table className="w-full text-sm">
            <thead className="text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="py-2">Name</th>
                <th className="py-2">Email</th>
                <th className="py-2">Role</th>
                <th className="py-2">Joined</th>
                {isAdmin && <th className="py-2" />}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {members.data.map((m) => (
                <tr key={m.userId}>
                  <td className="py-2 font-medium">{m.fullName}</td>
                  <td className="py-2 text-slate-600">{m.email}</td>
                  <td className="py-2">
                    {isAdmin ? (
                      <Select aria-label={`Role for ${m.fullName}`} value={m.role} disabled={busy} onChange={(e) => act(() => orgApi.changeRole(orgId, m.userId, e.target.value as Role))}>
                        {ROLES.map((r) => (
                          <option key={r} value={r}>
                            {humanize(r)}
                          </option>
                        ))}
                      </Select>
                    ) : (
                      <RoleBadge role={m.role} />
                    )}
                  </td>
                  <td className="py-2 text-slate-500">{formatDate(m.joinedAt)}</td>
                  {isAdmin && (
                    <td className="py-2 text-right">
                      <Button variant="ghost" disabled={busy || m.userId === user?.id} onClick={() => act(() => orgApi.removeMember(orgId, m.userId))}>
                        Remove
                      </Button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      {isAdmin && (
        <Card title="Add a member">
          <form onSubmit={add} noValidate className="space-y-3">
            <Field label="Email of an existing account" htmlFor="member-email" error={formError ?? undefined}>
              <Input id="member-email" type="email" value={email} aria-invalid={!!formError} onChange={(e) => setEmail(e.target.value)} />
            </Field>
            <Field label="Role" htmlFor="member-role">
              <Select id="member-role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {humanize(r)}
                  </option>
                ))}
              </Select>
            </Field>
            <Button type="submit" disabled={busy}>
              Add member
            </Button>
          </form>
        </Card>
      )}
    </div>
  )
}
