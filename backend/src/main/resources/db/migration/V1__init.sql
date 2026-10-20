-- ServiceDesk schema. Every tenant-scoped table carries organization_id (directly or
-- through its ticket) so that authorization checks and indexes can be organization-first.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE organizations (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    slug       VARCHAR(60)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);

-- Many-to-many between users and organizations with its own attribute (role).
CREATE TABLE memberships (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    organization_id BIGINT      NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_memberships_user_org UNIQUE (user_id, organization_id),
    CONSTRAINT ck_memberships_role CHECK (role IN ('REQUESTER', 'TECHNICIAN', 'ADMIN'))
);
CREATE INDEX ix_memberships_org ON memberships (organization_id);

CREATE TABLE tickets (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT        NOT NULL REFERENCES organizations (id),
    title           VARCHAR(160)  NOT NULL,
    description     TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    priority        VARCHAR(20)   NOT NULL,
    requester_id    BIGINT        NOT NULL REFERENCES users (id),
    assignee_id     BIGINT        REFERENCES users (id),
    version         BIGINT        NOT NULL DEFAULT 0,
    reopen_count    INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    CONSTRAINT ck_tickets_status   CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);
-- Driven by the real queries: list by status, workload per technician, "my tickets", newest first.
CREATE INDEX ix_tickets_org_status      ON tickets (organization_id, status);
CREATE INDEX ix_tickets_org_assignee    ON tickets (organization_id, assignee_id);
CREATE INDEX ix_tickets_org_requester   ON tickets (organization_id, requester_id);
CREATE INDEX ix_tickets_org_created_at  ON tickets (organization_id, created_at DESC);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_comments_ticket ON comments (ticket_id, created_at);

-- Append-only audit trail. Written in the same transaction as the change it describes.
CREATE TABLE ticket_events (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    actor_id   BIGINT      NOT NULL REFERENCES users (id),
    type       VARCHAR(30) NOT NULL,
    old_value  VARCHAR(255),
    new_value  VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_events_ticket ON ticket_events (ticket_id, created_at);
