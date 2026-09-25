# Project Design and Implementation Guidelines

These rules apply to every project in this repository. Project-specific instructions may add stricter requirements.

## Workflow

- Inspect the relevant requirements, code, conventions, and tests before proposing changes.
- Before editing, present the approach and expected files to change; wait for explicit approval.
- Do not create files, change dependencies, or perform unrelated refactoring without approval.
- Preserve user changes and stay within the approved scope.
- Find and update every affected caller, implementation, contract, configuration, test, and document.
- State material assumptions. Ask only when ambiguity affects behavior, data, security, or architecture.
- On completion, report changes, checks run, results, and remaining risks. Never claim unperformed verification.

## Architecture

For layered applications, use:

`Routes/Transport -> Service/Application -> Repository/Gateway -> Models/Storage`

- Routes handle protocol input/output only; no business logic or database access.
- Services own use cases, business validation, authorization, transactions, and orchestration.
- Repositories own queries, filtering, sorting, aggregation, and persistence mapping.
- Gateways isolate external providers; vendor-specific APIs must not leak into business layers.
- Domain rules must not depend on transport, database, or vendor concerns.
- Do not skip layers, create circular dependencies, or bypass architecture checks.
- In small projects, preserve these boundaries with modules/functions rather than empty wrapper classes.

## Data and Domain

- Keep one source of truth for each business rule, state transition, schema, status, and configuration key.
- Use typed structures, enums, and constants instead of hard-coded values.
- Perform filtering, sorting, aggregation, and pagination in the database/repository, not in memory.
- Prefer unique/indexed lookups and bounded queries; avoid unbounded reads and N+1 queries.
- Use constraints and atomic operations, locking, or versioning to prevent races and duplicates.
- Define transactions around complete business operations.
- Keep migrations safe and reviewable; make seeds and rerunnable data operations idempotent.
- Prefer composition. Extract duplication only when it represents the same knowledge.

## APIs and Errors

- Validate request shape at the transport layer and business rules in the service/domain layer.
- Use opaque cursor pagination with deterministic ordering for large or mutable collections.
- Never accept system-managed fields from clients: internal IDs, ownership, timestamps, versions, computed values, audit fields, or deletion state.
- Preserve public compatibility unless a breaking change is explicitly approved.
- Return consistent errors without secrets, stack traces, database details, or provider internals.
- Use `400` malformed, `401` unauthenticated, `403` unauthorized, `404` missing/concealed, `409` conflict, `422` business violation, `429` rate limit, and `500` unexpected failure.
- Never swallow errors or report success when a required operation failed.

## Security and Tenancy

- Deny by default, use least privilege, and enforce authorization in the service layer.
- Verify both permission and resource ownership/scope; never trust client-supplied roles or ownership.
- Derive tenant identity from trusted authentication/context and scope every tenant-owned operation.
- Include tenant scope in queries, constraints, caches, jobs, and background processing.
- Resolve scoped configuration centrally from broadest to most specific, such as `Global -> Environment -> Tenant`.
- Test unauthenticated, unauthorized, wrong-owner, and cross-tenant access.
- Never commit or log credentials, tokens, private keys, production data, or sensitive information.

## Deletes, External Calls, and Async Work

- Choose hard delete, soft delete, archive, or anonymization from explicit requirements.
- If using soft delete, exclude deleted records by default; normal fetches return `404`. Administrative access must be explicit and authorized.
- Make deletes and retryable side effects idempotent. Audit sensitive or irreversible actions without recording secrets.
- Access external services through gateways with explicit timeouts.
- Retry only transient failures using bounded exponential backoff with jitter; schedule retries instead of blocking threads or workers.
- Bound workers, queues, batches, connections, fan-out, and retry attempts.
- Define terminal failure, dead-letter, cancellation, and recovery behavior when applicable.
- Persist workflow states and attempts when work must survive process failure or message redelivery.

## Generated Code and Dependencies

- Never edit generated files. Change the source specification/template, regenerate, and review the diff.
- Deliver source and generated changes together; extend generated code only through supported mechanisms.
- Prefer existing dependencies or the standard library. Add or upgrade dependencies only with approval.
- Follow lockfile and version-pinning conventions; never disable integrity or security checks.

## Testing and Completion

- Unit-test domain rules and integration-test core workflows, persistence, transactions, and contracts.
- Use contract-faithful fakes for repositories/providers; avoid mocks coupled to implementation details.
- Cover relevant success, boundary, validation, conflict, authorization, isolation, idempotency, retry, concurrency, and failure paths.
- Add a regression test for every bug fix unless technically infeasible; document any omission.
- Keep tests deterministic by controlling time, randomness, IDs, scheduling, and external I/O.
- Review the diff and verify all affected callers, contracts, schemas, configuration, tests, and documentation.
- Run relevant tests and applicable formatting, linting, type, architecture, generation, and migration checks.
- Report files changed, decisions, verification results, compatibility concerns, and remaining limitations.
