# TaskMaster API

TaskMaster is the backend of a collaborative task tracker. I built it with Spring Boot and PostgreSQL. People
register and log in with JWTs, create personal tasks, form teams by invitation, and assign team tasks to
each other. They discuss tasks in comments, attach files, and get notified live (Server-Sent Events) when
something that concerns them changes. The optional AI endpoints draft task descriptions and summarise
tasks through the Claude API.

| | |
|---|---|
| Stack | Java 21, Spring Boot 4.1, Spring Security (OAuth2 resource server, JWT), Spring Data JPA, Flyway |
| Database | PostgreSQL |
| Docs | Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`, Postman collection in [`postman/`](postman/) |
| Tests | 56 integration tests against a real PostgreSQL database |

## Running it

**You need:** JDK 21 or newer and PostgreSQL 14 or newer. You don't need Maven, because the wrapper (`mvnw`) downloads it.

**1. Create the databases** (one for the app, one for the tests):

```bash
createdb -U postgres taskmaster
createdb -U postgres taskmaster_test
```

On Windows `createdb` is usually not on the PATH. Use the full path, e.g.
`"C:\Program Files\PostgreSQL\18\bin\createdb.exe" -U postgres taskmaster`, or run
`CREATE DATABASE taskmaster;` in pgAdmin. Flyway creates all the tables on first start.

**2. Start the API:**

```bash
./mvnw spring-boot:run
```

(`mvnw.cmd spring-boot:run` in a Windows command prompt.) It listens on http://localhost:8080; open
http://localhost:8080/swagger-ui.html, call `POST /api/auth/login`, and paste the token into **Authorize**.

**Configuration.** The defaults work on a local machine with the `postgres`/`postgres` account. Everything can be
overridden with environment variables:

| Variable | Default | What it is |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/taskmaster`, `postgres`, `postgres` | Database connection |
| `JWT_SECRET` | a development value | HS256 signing key, at least 32 characters. **Set this anywhere but your own machine.** |
| `ANTHROPIC_API_KEY` | empty | Turns on the AI endpoints. Without it they answer 503 and everything else works |
| `ATTACHMENTS_DIR` | `./uploads` | Where uploaded files are stored |
| `SERVER_PORT` | `8080` | HTTP port |

## Tests

```bash
./mvnw test
```

The tests use the `taskmaster_test` database and empty it before each test, so every test starts clean.
They cover every user story as well as the rules in between, for example: a logged-out token stops working,
outsiders get 404 on a team's tasks, a search for `100%` matches only that literal text, a removed member's
tasks are unassigned, and an open SSE stream receives a notification. The AI client is replaced by a fake,
so the tests need no API key or network.

## Postman

Import [`postman/TaskMaster.postman_collection.json`](postman/TaskMaster.postman_collection.json). It has 48
requests in the order of the user stories, with checks on each one. It registers two users (Asha and Ravi)
with new emails every run and saves tokens and ids into collection variables, so the Collection Runner
can play it from top to bottom. The one manual step is the upload request: pick any file for its `file`
field first.

## User stories → endpoints

| # | Story | Endpoint |
|---|---|---|
| 1 | Create an account | `POST /api/auth/register` |
| 2 | Log in securely | `POST /api/auth/login` → bearer token |
| 3 | View and update my profile | `GET`, `PATCH /api/users/me`, `PUT /api/users/me/password` |
| 4 | Create a task with title, description, due date | `POST /api/tasks` |
| 5 | See the tasks assigned to me | `GET /api/tasks?assignedToMe=true` |
| 6 | Mark a task completed | `PATCH /api/tasks/{id}/status` `{"status":"COMPLETED"}` |
| 7 | Assign a task to a team member | `PUT /api/tasks/{id}/assignee` `{"assigneeId":5}` |
| 8 | Filter by status | `GET /api/tasks?status=OPEN` (repeatable: `&status=IN_PROGRESS`) |
| 9 | Search by title or description | `GET /api/tasks?q=login` |
| 10 | Comments and attachments | `/api/tasks/{id}/comments`, `/api/tasks/{id}/attachments` |
| 11 | Create a team and invite members | `POST /api/teams`, `POST /api/teams/{id}/invitations`, `POST /api/invitations/{id}/accept` |
| 12 | Log out | `POST /api/auth/logout` |
| 13 | Notifications (optional) | `GET /api/notifications`, live stream `GET /api/notifications/stream` (SSE) |
| 14 | AI descriptions and summaries (optional) | `POST /api/ai/task-description`, `POST /api/ai/tasks/{id}/summary` |

## API overview

Everything except register, login and the docs needs `Authorization: Bearer <token>`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register` · `POST /api/auth/login` · `POST /api/auth/logout` |
| Profile | `GET /api/users/me` · `PATCH /api/users/me` · `PUT /api/users/me/password` |
| Teams | `POST /api/teams` · `GET /api/teams` · `GET` / `PATCH /api/teams/{id}` · `POST` / `GET /api/teams/{id}/invitations` · `DELETE /api/teams/{id}/members/{userId}` |
| Invitations | `GET /api/invitations` · `POST /api/invitations/{id}/accept` · `POST /api/invitations/{id}/decline` |
| Tasks | `POST /api/tasks` · `GET /api/tasks` · `GET` / `PUT` / `DELETE /api/tasks/{id}` · `PATCH /api/tasks/{id}/status` · `PUT /api/tasks/{id}/assignee` |
| Comments | `GET` / `POST /api/tasks/{id}/comments` · `PUT` / `DELETE /api/tasks/{id}/comments/{commentId}` |
| Attachments | `GET` / `POST /api/tasks/{id}/attachments` · `GET .../{attachmentId}/download` · `DELETE .../{attachmentId}` |
| Notifications | `GET /api/notifications` · `GET /api/notifications/unread-count` · `PATCH /api/notifications/{id}/read` · `POST /api/notifications/read-all` · `GET /api/notifications/stream` |
| AI | `POST /api/ai/task-description` · `POST /api/ai/tasks/{id}/summary` |

**Listing tasks.** `GET /api/tasks` returns only tasks you can see, and every filter is optional:
`status` (repeatable), `priority`, `teamId`, `assigneeId`, `assignedToMe`, `unassigned`, `q`, `dueFrom`,
`dueTo`. Results are paged (`page`, `size` up to 100) and sorted with `sort=field,direction`, using
`dueDate`, `priority`, `status`, `title`, `createdAt` or `updatedAt`. The default is newest first.

```
GET /api/tasks?status=OPEN&priority=HIGH&q=deploy&sort=dueDate,asc&size=10
```

**Errors** all have the same RFC 7807 shape. Validation errors also name each field:

```json
{ "title": "Bad Request", "status": 400, "detail": "Request validation failed",
  "instance": "/api/tasks", "errors": { "title": "is required" } }
```

### A short walk-through

```bash
# Register and log in
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
     -d '{"email":"asha@example.com","name":"Asha","password":"correct-horse-battery"}'
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"email":"asha@example.com","password":"correct-horse-battery"}'
# → {"accessToken":"eyJ...","tokenType":"Bearer","expiresAt":"..."}

# Create a task, then complete it
curl -X POST localhost:8080/api/tasks -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"title":"Write the README","dueDate":"2030-01-15","priority":"HIGH"}'
curl -X PATCH localhost:8080/api/tasks/1/status -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" -d '{"status":"COMPLETED"}'

# Watch notifications arrive live
curl -N localhost:8080/api/notifications/stream -H "Authorization: Bearer $TOKEN"
```

## Design decisions

**Logout really logs out.** A JWT can't be deleted, so it normally stays valid until it expires. Every
token I issue has a unique id (`jti`). Logout stores that id in `revoked_tokens`, and the JWT decoder
rejects any token on that list. A row only has to live until its token would expire anyway, so an hourly
job deletes old rows and the table stays small. Logging out ends only the current session; other devices
stay signed in.

**Who can see what.** A task is either *personal* (no team) or belongs to a *team*. You can see and edit a
task if you created it, are assigned to it, or are in its team. Deleting is limited to the task's creator or
the team owner. A personal task can only be assigned to its creator, and a team task only to a member of
that team. These rules are all in one class, `TaskAccess`, which tasks, comments, attachments and the AI
summary all use.

**404 instead of 403 for things you can't see.** If someone asks for a task or team they have no access to,
the answer is the same as for one that doesn't exist. That way ids can't be probed to find out what
exists. 403 is only used when you *can* see something but the action isn't yours to take, for example a
member trying to delete someone else's task.

**Joining a team happens through invitations.** The owner invites a registered user by email, and that
person accepts or declines. Nobody is added to a team without saying yes, and nobody can join a team
without being asked. When a member leaves or is removed, their tasks in that team become unassigned, so
work doesn't sit with someone who can no longer see it.

**Priority is stored as a number** (1–3), so `sort=priority,desc` gives HIGH, MEDIUM, LOW instead of the
alphabetical HIGH, LOW, MEDIUM.

**Search escapes wildcards.** `q` uses a case-insensitive `LIKE` on title and description, with `%` and `_`
escaped. Searching for `100%` finds exactly that text and not everything starting with "100". Sorting only
allows a fixed list of fields, and anything else gets a 400.

**Notifications are sent only after the change is saved.** Each notification is saved in the same
transaction as the change that caused it. It's pushed to open SSE streams only after that transaction
commits, so nobody is told about a change that was then rolled back. The person who made a change is never
notified about it. A heartbeat every 25 seconds keeps idle connections open through proxies.

**Attachments are stored on disk under random names.** The uploaded file name is only stored for display
and for the download header, so a name like `../../etc/passwd` can never reach the file system. Downloads
are always sent as `attachment`, never shown inline, so an uploaded HTML file can't run in a browser under
the API's origin. The disk and the database don't share a transaction, so I keep them in sync myself. A
file saved during a request that fails is deleted again, and the files of a deleted attachment or task are
removed only after the deletion commits.

**The AI suggests text but doesn't save it.** Both endpoints return text, and the user decides whether to
put it into the task, so a poor answer never overwrites anything. Task content is sent clearly marked as
data rather than instructions. The model call happens outside any database transaction, so a slow answer
doesn't hold a connection. The model sits behind a one-method `TextGenerator` interface, which the tests
replace with a fake.

**Passwords** are hashed with BCrypt. BCrypt only reads the first 72 bytes, so I limit passwords to 72
*bytes* (not characters) instead of quietly ignoring the rest. A failed login takes about the same time
whether or not the email exists, so response times don't reveal which emails are registered.

## Known limitations and what I would do next

- **One instance only for live notifications.** SSE connections live in memory. With several instances
  behind a load balancer, I'd publish notifications through Redis pub/sub (or similar) so they reach a
  client connected to any instance. Stored notifications work either way.
- **Attachments are on the local disk.** In production I'd store them in object storage such as S3, and add
  virus scanning and per-user quotas.
- **Changing a password doesn't log out other sessions.** Adding a "tokens valid after" timestamp per user,
  checked next to the revocation list, would fix that.
- **No rate limiting on login.** I'd add it at the gateway or with a library such as Bucket4j.
- **Invitations need a registered user.** Inviting by email to someone without an account would need
  outgoing email.
- **Teams can't be deleted, and ownership can't be transferred.** Neither was asked for, and team deletion
  needs a decision about what happens to the team's tasks.
- **The SSE stream needs the `Authorization` header.** The browser's built-in `EventSource` can't send
  headers, so a web client would use a fetch-based SSE reader, or I'd add short-lived stream tickets.

## Project layout

```
src/main/java/com/vikashini/taskmaster/
  auth/          registration, login, logout, JWT issuing and revocation
  user/          profile
  team/          teams, members, invitations
  task/          tasks, filtering and search, access rules (TaskAccess)
  comment/       comments on tasks
  attachment/    file uploads and their storage
  notification/  stored notifications and the SSE stream
  ai/            Claude-backed description and summary endpoints
  common/        error handling and paging
  config/        security, OpenAPI, clock
src/main/resources/db/migration/   Flyway schema
src/test/java/                     integration tests
postman/                           Postman collection
```
