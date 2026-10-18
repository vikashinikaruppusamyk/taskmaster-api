-- TaskMaster schema: users, teams, tasks and the collaboration around them.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    -- Stored lower-cased, so the unique constraint is case-insensitive in practice.
    email         VARCHAR(254) NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    bio           VARCHAR(500),
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- Tokens that were logged out before they expired. A row only needs to live until
-- the token's own expiry; after that the signature check rejects it anyway.
CREATE TABLE revoked_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens (expires_at);

CREATE TABLE teams (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    owner_id    BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE team_members (
    id        BIGSERIAL PRIMARY KEY,
    team_id   BIGINT      NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users (id),
    role      VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_team_members UNIQUE (team_id, user_id)
);
CREATE INDEX idx_team_members_user ON team_members (user_id);

CREATE TABLE team_invitations (
    id              BIGSERIAL PRIMARY KEY,
    team_id         BIGINT      NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    invited_user_id BIGINT      NOT NULL REFERENCES users (id),
    invited_by_id   BIGINT      NOT NULL REFERENCES users (id),
    status          VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    created_at      TIMESTAMPTZ NOT NULL,
    responded_at    TIMESTAMPTZ
);
-- At most one open invitation per person per team.
CREATE UNIQUE INDEX uq_team_invitations_pending
    ON team_invitations (team_id, invited_user_id) WHERE status = 'PENDING';
CREATE INDEX idx_team_invitations_invited_user ON team_invitations (invited_user_id, status);

CREATE TABLE tasks (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    description  VARCHAR(5000),
    due_date     DATE,
    status       VARCHAR(15)  NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED')),
    -- 1 = LOW, 2 = MEDIUM, 3 = HIGH. A number so that sorting by priority is meaningful.
    priority     SMALLINT     NOT NULL CHECK (priority BETWEEN 1 AND 3),
    -- NULL for a personal task.
    team_id      BIGINT REFERENCES teams (id) ON DELETE CASCADE,
    created_by   BIGINT       NOT NULL REFERENCES users (id),
    assignee_id  BIGINT REFERENCES users (id),
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_tasks_assignee ON tasks (assignee_id);
CREATE INDEX idx_tasks_created_by ON tasks (created_by);
CREATE INDEX idx_tasks_team ON tasks (team_id);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT        NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    author_id  BIGINT        NOT NULL REFERENCES users (id),
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_comments_task ON comments (task_id, created_at);

CREATE TABLE attachments (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT       NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    uploaded_by   BIGINT       NOT NULL REFERENCES users (id),
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(255) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    -- Name of the file inside the storage directory; never derived from user input.
    storage_key   VARCHAR(64)  NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_attachments_task ON attachments (task_id);

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES users (id),
    type         VARCHAR(30)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    -- Plain ids rather than foreign keys: a notification outlives the task it mentions.
    task_id      BIGINT,
    team_id      BIGINT,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
