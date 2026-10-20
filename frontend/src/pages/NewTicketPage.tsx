import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, describeError } from '../api/client'
import { tickets as ticketApi } from '../api/endpoints'
import { TICKET_PRIORITIES, type TicketPriority } from '../api/types'
import { Alert, Button, Card, Field, Input, Select, Textarea, humanize } from '../components/ui'

export interface NewTicketFormProps {
  onSubmit: (title: string, description: string, priority: TicketPriority) => Promise<void>
  onCancel?: () => void
}

/** Pure form component so it can be tested without routing or the network. */
export function NewTicketForm({ onSubmit, onCancel }: NewTicketFormProps) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')
  const [errors, setErrors] = useState<{ title?: string; description?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const next: typeof errors = {}
    if (!title.trim()) next.title = 'Title is required'
    else if (title.trim().length > 160) next.title = 'Title must be 160 characters or fewer'
    if (!description.trim()) next.description = 'Describe the problem so a technician can reproduce it'
    setErrors(next)
    if (Object.keys(next).length) return
    setSubmitting(true)
    try {
      await onSubmit(title.trim(), description.trim(), priority)
    } catch (err) {
      if (err instanceof ApiError && err.code === 'VALIDATION_ERROR') {
        setErrors({ title: err.fieldError('title'), description: err.fieldError('description') })
      } else {
        setError(describeError(err))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {error && <Alert>{error}</Alert>}
      <Field label="Title" htmlFor="title" error={errors.title}>
        <Input id="title" value={title} aria-invalid={!!errors.title} onChange={(e) => setTitle(e.target.value)} placeholder="Short summary of the problem" />
      </Field>
      <Field label="Description" htmlFor="description" error={errors.description}>
        <Textarea id="description" rows={6} value={description} aria-invalid={!!errors.description} onChange={(e) => setDescription(e.target.value)} placeholder="What happened, since when, what you already tried…" />
      </Field>
      <Field label="Priority" htmlFor="priority">
        <Select id="priority" value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}>
          {TICKET_PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {humanize(p)}
            </option>
          ))}
        </Select>
      </Field>
      <div className="flex gap-2">
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create ticket'}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}

export function NewTicketPage() {
  const { orgId: orgIdParam } = useParams()
  const orgId = Number(orgIdParam)
  const navigate = useNavigate()
  return (
    <div className="mx-auto max-w-2xl">
      <Card title="New ticket">
        <NewTicketForm
          onSubmit={async (title, description, priority) => {
            const ticket = await ticketApi.create(orgId, title, description, priority)
            navigate(`/orgs/${orgId}/tickets/${ticket.id}`)
          }}
          onCancel={() => navigate(`/orgs/${orgId}/tickets`)}
        />
      </Card>
    </div>
  )
}
