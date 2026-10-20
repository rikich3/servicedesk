import { Navigate, Outlet, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { AppLayout } from './components/Layout'
import { Spinner } from './components/ui'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { MembersPage } from './pages/MembersPage'
import { NewTicketPage } from './pages/NewTicketPage'
import { OrganizationsPage } from './pages/OrganizationsPage'
import { RegisterPage } from './pages/RegisterPage'
import { TicketDetailPage } from './pages/TicketDetailPage'
import { TicketsPage } from './pages/TicketsPage'

function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Spinner label="Checking your session…" />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}

/** Guards /orgs/:orgId routes: the user must belong to that organization. */
function RequireMembership() {
  const { user } = useAuth()
  const { orgId } = useParams()
  if (!user?.memberships.some((m) => String(m.organizationId) === orgId)) return <Navigate to="/orgs" replace />
  return <Outlet />
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/orgs" element={<OrganizationsPage />} />
          <Route path="/orgs/:orgId" element={<RequireMembership />}>
            <Route index element={<Navigate to="tickets" replace />} />
            <Route path="tickets" element={<TicketsPage />} />
            <Route path="tickets/new" element={<NewTicketPage />} />
            <Route path="tickets/:ticketId" element={<TicketDetailPage />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="members" element={<MembersPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/orgs" replace />} />
    </Routes>
  )
}
