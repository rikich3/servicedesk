import { api } from './client'
import type {
  Comment,
  CurrentUser,
  Member,
  Organization,
  Page,
  ReportSummary,
  Role,
  Ticket,
  TicketDetail,
  TicketFilter,
  TicketPriority,
  UpdateTicketRequest,
} from './types'

export const auth = {
  me: () => api.get<CurrentUser>('/api/auth/me'),
  login: (email: string, password: string) => api.post<CurrentUser>('/api/auth/login', { email, password }),
  register: (email: string, password: string, fullName: string) =>
    api.post<CurrentUser>('/api/auth/register', { email, password, fullName }),
  logout: () => api.post<void>('/api/auth/logout'),
}

export const organizations = {
  list: () => api.get<Organization[]>('/api/organizations'),
  create: (name: string, slug: string) => api.post<Organization>('/api/organizations', { name, slug }),
  members: (orgId: number) => api.get<Member[]>(`/api/organizations/${orgId}/members`),
  addMember: (orgId: number, email: string, role: Role) =>
    api.post<Member>(`/api/organizations/${orgId}/members`, { email, role }),
  changeRole: (orgId: number, userId: number, role: Role) =>
    api.patch<Member>(`/api/organizations/${orgId}/members/${userId}`, { role }),
  removeMember: (orgId: number, userId: number) => api.delete<void>(`/api/organizations/${orgId}/members/${userId}`),
}

export const tickets = {
  list: (orgId: number, filter: TicketFilter) =>
    api.get<Page<Ticket>>(`/api/organizations/${orgId}/tickets`, {
      status: filter.status,
      priority: filter.priority,
      assigneeId: filter.assigneeId,
      unassigned: filter.unassigned ? true : undefined,
      q: filter.q,
      page: filter.page,
      size: filter.size,
      sort: filter.sort,
    }),
  get: (orgId: number, ticketId: number) => api.get<TicketDetail>(`/api/organizations/${orgId}/tickets/${ticketId}`),
  create: (orgId: number, title: string, description: string, priority: TicketPriority) =>
    api.post<Ticket>(`/api/organizations/${orgId}/tickets`, { title, description, priority }),
  update: (orgId: number, ticketId: number, body: UpdateTicketRequest) =>
    api.patch<Ticket>(`/api/organizations/${orgId}/tickets/${ticketId}`, body),
  comment: (orgId: number, ticketId: number, body: string) =>
    api.post<Comment>(`/api/organizations/${orgId}/tickets/${ticketId}/comments`, { body }),
}

export const reports = {
  summary: (orgId: number, from?: string, to?: string) =>
    api.get<ReportSummary>(`/api/organizations/${orgId}/reports/summary`, { from, to }),
}
