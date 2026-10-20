import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button, RoleBadge } from './ui'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700 hover:bg-slate-100'}`

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { orgId } = useParams()
  const membership = user?.memberships.find((m) => String(m.organizationId) === orgId)
  const base = orgId ? `/orgs/${orgId}` : ''

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-6">
            <NavLink to="/orgs" className="text-lg font-semibold text-indigo-700">
              ServiceDesk
            </NavLink>
            {membership && (
              <nav className="flex items-center gap-1">
                <NavLink to={`${base}/tickets`} className={linkClass}>
                  Tickets
                </NavLink>
                {membership.role !== 'REQUESTER' && (
                  <NavLink to={`${base}/dashboard`} className={linkClass}>
                    Dashboard
                  </NavLink>
                )}
                <NavLink to={`${base}/members`} className={linkClass}>
                  Members
                </NavLink>
              </nav>
            )}
          </div>
          <div className="flex items-center gap-3 text-sm">
            {membership && (
              <span className="hidden items-center gap-2 text-slate-600 sm:flex">
                <NavLink to="/orgs" className="hover:underline">
                  {membership.organizationName}
                </NavLink>
                <RoleBadge role={membership.role} />
              </span>
            )}
            <span className="text-slate-700" data-testid="current-user">
              {user?.fullName}
            </span>
            <Button
              variant="ghost"
              onClick={async () => {
                await logout()
                navigate('/login')
              }}
            >
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
