import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, describeError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Field, Input } from '../components/ui'

export function RegisterPage() {
  const { register, login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [errors, setErrors] = useState<Partial<typeof form>>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value })

  const validate = () => {
    const next: typeof errors = {}
    if (!form.fullName.trim()) next.fullName = 'Full name is required'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = 'Enter a valid email address'
    if (form.password.length < 8) next.password = 'Password must be at least 8 characters'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!validate()) return
    setSubmitting(true)
    try {
      await register(form.email.trim(), form.password, form.fullName.trim())
      await login(form.email.trim(), form.password)
      navigate('/orgs', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'VALIDATION_ERROR') {
        setErrors({ fullName: err.fieldError('fullName'), email: err.fieldError('email'), password: err.fieldError('password') })
      } else if (err instanceof ApiError && err.code === 'EMAIL_TAKEN') {
        setErrors({ email: 'An account with this email already exists' })
      } else {
        setError(describeError(err))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={onSubmit} noValidate className="w-full max-w-sm space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Create your account</h1>
        {error && <Alert>{error}</Alert>}
        <Field label="Full name" htmlFor="fullName" error={errors.fullName}>
          <Input id="fullName" value={form.fullName} aria-invalid={!!errors.fullName} onChange={set('fullName')} />
        </Field>
        <Field label="Email" htmlFor="email" error={errors.email}>
          <Input id="email" type="email" value={form.email} aria-invalid={!!errors.email} onChange={set('email')} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password} hint="At least 8 characters">
          <Input id="password" type="password" value={form.password} aria-invalid={!!errors.password} onChange={set('password')} />
        </Field>
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create account'}
        </Button>
        <p className="text-center text-sm text-slate-500">
          Already registered?{' '}
          <Link to="/login" className="text-indigo-600 hover:underline">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  )
}
