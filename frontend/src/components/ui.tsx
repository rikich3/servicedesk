import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import type { Role, TicketPriority, TicketStatus } from '../api/types'

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' | 'ghost' }) {
  const base = 'inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50'
  const variants = {
    primary: 'bg-indigo-600 text-white hover:bg-indigo-700',
    secondary: 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50',
    danger: 'bg-rose-600 text-white hover:bg-rose-700',
    ghost: 'text-slate-700 hover:bg-slate-100',
  }
  return <button className={`${base} ${variants[variant]} ${className}`} {...props} />
}

interface FieldProps {
  label: string
  error?: string
  hint?: string
  children: ReactNode
  htmlFor?: string
}

export function Field({ label, error, hint, children, htmlFor }: FieldProps) {
  return (
    <div className="space-y-1">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-slate-500">{hint}</p>}
      {error && (
        <p className="text-xs text-rose-600" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

const inputClass =
  'block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 aria-[invalid=true]:border-rose-500'

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={inputClass} {...props} />
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={inputClass} {...props} />
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={inputClass} {...props} />
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'success' | 'info'; children: ReactNode }) {
  const styles = {
    error: 'border-rose-200 bg-rose-50 text-rose-800',
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    info: 'border-sky-200 bg-sky-50 text-sky-800',
  }
  return (
    <div role={kind === 'error' ? 'alert' : 'status'} className={`rounded-md border px-3 py-2 text-sm ${styles[kind]}`}>
      {children}
    </div>
  )
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-2 py-6 text-sm text-slate-500">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-indigo-600" aria-hidden />
      {label}
    </div>
  )
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 px-6 py-12 text-center">
      <p className="text-base font-medium text-slate-800">{title}</p>
      {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
      {(title || actions) && (
        <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          {title && <h2 className="text-sm font-semibold text-slate-800">{title}</h2>}
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

const statusStyles: Record<TicketStatus, string> = {
  OPEN: 'bg-sky-100 text-sky-800',
  IN_PROGRESS: 'bg-amber-100 text-amber-800',
  RESOLVED: 'bg-emerald-100 text-emerald-800',
  CLOSED: 'bg-slate-200 text-slate-700',
}
const priorityStyles: Record<TicketPriority, string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-sky-100 text-sky-800',
  HIGH: 'bg-orange-100 text-orange-800',
  URGENT: 'bg-rose-100 text-rose-800',
}
const roleStyles: Record<Role, string> = {
  REQUESTER: 'bg-slate-100 text-slate-700',
  TECHNICIAN: 'bg-violet-100 text-violet-800',
  ADMIN: 'bg-indigo-100 text-indigo-800',
}

export function humanize(value: string): string {
  return value.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase())
}

export function StatusBadge({ status }: { status: TicketStatus }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyles[status]}`}>{humanize(status)}</span>
}

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${priorityStyles[priority]}`}>{humanize(priority)}</span>
}

export function RoleBadge({ role }: { role: Role }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${roleStyles[role]}`}>{humanize(role)}</span>
}

export function formatDate(iso?: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav className="flex items-center justify-between pt-3 text-sm" aria-label="Pagination">
      <Button variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span className="text-slate-600">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Next
      </Button>
    </nav>
  )
}
