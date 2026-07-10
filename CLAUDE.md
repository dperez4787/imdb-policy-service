# imdb-policy-service

Spring Boot 3.5.x / Java 21 / MongoDB service: the governance system of record
for the IMDb federated graph. Serves the policy bundle the cosmo-router
`fieldauth` module enforces, ingests `@governed` registrations from
imdb-federation subgraphs, mints persona JWTs, publishes JWKS.

## Commands

- Build + all tests: `./mvnw -B verify` (`PolicyServiceIT` needs Docker;
  failsafe runs `*IT`)
- Run locally: `./mvnw spring-boot:run` against `mongodb://localhost:27017`

## Rules

- The bundle JSON (`PolicyBundle`) is the contract with cosmo-router's
  `fieldauth` module — additive changes only, never rename/remove fields.
- Every mutation that can change the compiled bundle MUST bump
  `RevisionService` and write an `AuditEntry`; the ETag/304 poll protocol
  depends on the revision being monotonic.
- `governed_fields` is written ONLY by subgraph registration (and the demo
  seed) — the admin API must never create coordinates, only grant roles to
  declared ones (404 otherwise). Registrations orphan, never delete.
- Posture is allow-unless-governed; a governed field without an enabled
  policy denies everyone. Don't add a "default allow" policy path.
- Keep the same collections split: `governed_fields` (what), `policies`
  (who), `personas` (identities), `audit_log` (receipts), `bundle_meta`
  (revision counter).
- Local Docker Desktop 29 needs `src/test/resources/docker-java.properties`
  (`api.version=1.44`) or Testcontainers gets HTTP 400 — don't delete it.
- Mirrors imdb-federation conventions (group `org.perez_f_daniel`, Boot
  parent, Testcontainers pin, CDS Dockerfile) — keep them in sync.
