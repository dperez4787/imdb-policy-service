# imdb-policy-service

The field-level governance **system of record** for the IMDb federated graph:
which schema coordinates are governed (declared by subgraphs), who may read
them (decided here), and how that reaches the enforcement point (a compiled
policy bundle the cosmo-router polls). Also mints demo persona JWTs and
publishes the JWKS to verify them.

Part of the governance architecture spanning
[imdb-federation](https://github.com/dperez4787/imdb-federation) (declares
`@governed` fields) and
[cosmo-router](https://github.com/dperez4787/cosmo-router) (enforces the
bundle in its `fieldauth` module): **declare at the edge, decide at the
center, enforce at the gate.**

## API

| Endpoint | Consumer | Purpose |
| --- | --- | --- |
| `GET /v1/bundle` | router | Compiled policy bundle. ETag = revision; poll with `If-None-Match` for cheap 304s. |
| `PUT /v1/registrations/{subgraph}` | subgraphs | Idempotent upsert of a subgraph's `@governed` declarations. Missing coordinates are ORPHANED, never deleted. |
| `POST /v1/token` | demo clients, UI | `{"persona":"analyst"}` → short-lived RS256 JWT with a `roles` claim. |
| `GET /.well-known/jwks.json` | router | Public signing keys. |
| `GET /v1/admin/overview` | admin UI | Governed fields joined with their policies. |
| `PUT/DELETE /v1/admin/policies/{coordinate}` | admin UI | Grant/revoke roles. Only declared coordinates are accepted (404 otherwise). |
| `GET/PUT /v1/admin/personas[/{id}]` | admin UI | Personas: roles + Google identity subjects. |
| `GET /v1/admin/audit` | admin UI | Append-only change log. |
| `GET /actuator/health` | Cloud Run | Liveness. |

Semantics: posture is **allow-unless-governed** — only governed coordinates
appear in the bundle; a governed field with no (enabled) policy denies
everyone. The bundle's `principals` map (Google identity subject → roles)
lets the router resolve roles for Google/Firebase-signed tokens that carry no
roles claim; persona-minted tokens carry `roles` directly.

## Run

```sh
# Tests (integration tests need Docker)
./mvnw -B verify

# Local, against a local Mongo
docker run -d -p 27017:27017 mongo:8.0
./mvnw spring-boot:run
curl -s localhost:8080/v1/bundle | jq
```

Demo seed (`policy.seed.enabled`, default on) populates an empty database:
personas `public` and `analyst`; governed fields `Rating.numVotes` (analyst),
`Name.birthYear` (analyst), `Name.deathYear` (governed, granted to no one —
deny-by-default demo).

## Configuration

| Env var | Default | Notes |
| --- | --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017/imdb_governance` | Shared Atlas cluster in prod. |
| `MONGODB_DATABASE` | `imdb_governance` | Overrides the URI's database. |
| `POLICY_JWT_PRIVATE_KEY` | *(empty)* | PKCS#8 PEM. Empty → ephemeral key per boot (dev only). |
| `POLICY_JWT_ISSUER` | `https://imdb-policy-service` | |
| `POLICY_JWT_AUDIENCE` | `imdb-router` | Must match the router's JWKS provider audience entry. |
| `POLICY_JWT_TTL` | `15m` | |
| `POLICY_ADMIN_TOKEN` | *(empty)* | Empty → `/v1/admin/**` is OPEN (dev only; warned at boot). |
| `POLICY_SEED_ENABLED` | `true` | |

## Wiring into the estate (next steps)

- **Router (phase 2)**: add a JWKS provider entry for this service to
  cosmo-router's `authentication.jwt` config (audience `imdb-router`), and
  point the `fieldauth` module's `policy_service_url` here.
- **Subgraphs (phase 3)**: the shared `@governed` DGS component in
  imdb-federation `common/` PUTs registrations at startup.
- **Deploy**: Cloud Run, mirroring the estate (Terraform for SA/WIF/AR, CI
  `gcloud run deploy`). Not yet scaffolded — service and tests first.
