import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, describeError } from '../api/client'
import { organizations as orgApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Card, EmptyState, Field, Input, RoleBadge } from '../components/ui'

export function OrganizationsPage() {
  const { user, refresh } = useAuth()
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [errors, setErrors] = useState<{ name?: string; slug?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onCreate = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const next: typeof errors = {}
    if (!name.trim()) next.name = 'Name is required'
    if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(slug)) next.slug = 'Use lowercase letters, digits and dashes'
    setErrors(next)
    if (Object.keys(next).length) return
    setSubmitting(true)
    try {
      await orgApi.create(name.trim(), slug)
      await refresh()
      setName('')
      setSlug('')
    } catch (err) {
      if (err instanceof ApiError && err.code === 'SLUG_TAKEN') setErrors({ slug: 'This slug is already in use' })
      else if (err instanceof ApiError && err.code === 'VALIDATION_ERROR') setErrors({ name: err.fieldError('name'), slug: err.fieldError('slug') })
      else setError(describeError(err))
    } finally {
      setSubmitting(false)
    }
  }

  const memberships = user?.memberships ?? []

  return (
    <div className="grid gap-6 md:grid-cols-[2fr_1fr]">
      <Card title="Your workspaces">
        {memberships.length === 0 ? (
          <EmptyState title="You are not a member of any workspace yet" description="Create one, or ask an administrator to add your email." />
        ) : (
          <ul className="divide-y divide-slate-200">
            {memberships.map((m) => (
              <li key={m.organizationId} className="flex items-center justify-between py-3">
                <div>
                  <Link to={`/orgs/${m.organizationId}/tickets`} className="font-medium text-indigo-700 hover:underline">
                    {m.organizationName}
                  </Link>
                  <p className="text-xs text-slate-500">/{m.slug}</p>
                </div>
                <RoleBadge role={m.role} />
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Create a workspace">
        <form onSubmit={onCreate} noValidate className="space-y-3">
          {error && <Alert>{error}</Alert>}
          <Field label="Company name" htmlFor="org-name" error={errors.name}>
            <Input id="org-name" value={name} aria-invalid={!!errors.name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Slug" htmlFor="org-slug" error={errors.slug} hint="Short identifier, e.g. acme-corp">
            <Input id="org-slug" value={slug} aria-invalid={!!errors.slug} onChange={(e) => setSlug(e.target.value.toLowerCase())} />
          </Field>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create'}
          </Button>
        </form>
      </Card>
    </div>
  )
}
