// DTOs mirroring the backend contract (see docs/api.md).

export type Role = 'REQUESTER' | 'TECHNICIAN' | 'ADMIN'
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type EventType = 'CREATED' | 'ASSIGNED' | 'UNASSIGNED' | 'STATUS_CHANGED' | 'PRIORITY_CHANGED' | 'COMMENTED'

export const TICKET_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']
export const ROLES: Role[] = ['REQUESTER', 'TECHNICIAN', 'ADMIN']

export interface MembershipSummary {
  organizationId: number
  organizationName: string
  slug: string
  role: Role
}

export interface CurrentUser {
  id: number
  email: string
  fullName: string
  memberships: MembershipSummary[]
}

export interface Organization {
  id: number
  name: string
  slug: string
  role: Role
  createdAt: string
}

export interface Member {
  userId: number
  email: string
  fullName: string
  role: Role
  joinedAt: string
}

export interface UserRef {
  id: number
  fullName: string
}

export interface Ticket {
  id: number
  title: string
  description: string
  status: TicketStatus
  priority: TicketPriority
  requester: UserRef
  assignee?: UserRef
  version: number
  reopenCount: number
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  closedAt?: string
}

export interface Comment {
  id: number
  author: UserRef
  body: string
  createdAt: string
}

export interface TicketEvent {
  id: number
  actor: UserRef
  type: EventType
  oldValue?: string
  newValue?: string
  createdAt: string
}

export interface Permissions {
  allowedTransitions: TicketStatus[]
  canAssign: boolean
  canClaim: boolean
  canChangePriority: boolean
  canComment: boolean
}

export interface TicketDetail {
  ticket: Ticket
  comments: Comment[]
  events: TicketEvent[]
  permissions: Permissions
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TicketFilter {
  status?: TicketStatus | ''
  priority?: TicketPriority | ''
  assigneeId?: number | ''
  unassigned?: boolean
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface UpdateTicketRequest {
  version: number
  status?: TicketStatus
  priority?: TicketPriority
  assigneeId?: number
  unassign?: boolean
}

export interface TechnicianWorkload {
  userId: number
  fullName: string
  activeAssigned: number
  inProgress: number
  resolvedInPeriod: number
  avgResolutionHours?: number
}

export interface ReportSummary {
  from: string
  to: string
  openTickets: number
  unassignedTickets: number
  byStatus: Record<TicketStatus, number>
  byPriority: Record<TicketPriority, number>
  createdInPeriod: number
  resolvedInPeriod: number
  avgResolutionHours?: number
  reopenedTickets: number
  workload: TechnicianWorkload[]
}
