import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, describeError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Field, Input } from '../components/ui'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = () => {
    const next: typeof errors = {}
    if (!email.trim()) next.email = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) next.email = 'Enter a valid email address'
    if (!password) next.password = 'Password is required'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!validate()) return
    setSubmitting(true)
    try {
      const user = await login(email.trim(), password)
      const from = (location.state as { from?: string } | null)?.from
      if (from) navigate(from, { replace: true })
      else if (user.memberships.length === 1) navigate(`/orgs/${user.memberships[0].organizationId}/tickets`, { replace: true })
      else navigate('/orgs', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'BAD_CREDENTIALS') setError('Invalid email or password.')
      else setError(describeError(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={onSubmit} noValidate className="w-full max-w-sm space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Sign in to ServiceDesk</h1>
          <p className="text-sm text-slate-500">Track and resolve support requests for your team.</p>
        </div>
        {error && <Alert>{error}</Alert>}
        <Field label="Email" htmlFor="email" error={errors.email}>
          <Input id="email" type="email" autoComplete="email" value={email} aria-invalid={!!errors.email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password}>
          <Input id="password" type="password" autoComplete="current-password" value={password} aria-invalid={!!errors.password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
        <p className="text-center text-sm text-slate-500">
          No account?{' '}
          <Link to="/register" className="text-indigo-600 hover:underline">
            Create one
          </Link>
        </p>
      </form>
    </div>
  )
}
