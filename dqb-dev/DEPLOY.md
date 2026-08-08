# DexQBit Penpot — Deploy on Hetzner

> **Start here to run the app:** [README.md](README.md) (step-by-step).  
> This page covers CI image builds, TLS, upgrades, and admin backfill in more depth.

Self-hosted Penpot fork with DexQBit customizations. The VPS only runs Docker Compose against GHCR images — no source or build tooling on the host.

## Prerequisites

- Hetzner Cloud CPX31 (or similar) is enough for &lt;50 users
- Domain pointing at the VPS (A/AAAA)
- Google Cloud OAuth client (Web) with redirect:
  `https://<your-domain>/api/auth/oidc/callback`
- GitHub repo with Actions enabled and `packages: write` (workflow uses `GITHUB_TOKEN`)
- Optional: `DHI_USER` / `DHI_TOKEN` repo secrets if `dhi.io` base image pulls fail in CI

## 1. Build & push images (CI)

Workflow: [`.github/workflows/build-fork-images.yml`](../.github/workflows/build-fork-images.yml)

- Triggers on push to `main`/`master`, version tags `v*`, or manual dispatch
- Publishes:
  - `ghcr.io/<owner>/penpot-frontend:<tag>`
  - `ghcr.io/<owner>/penpot-backend:<tag>`
  - `ghcr.io/<owner>/penpot-exporter:<tag>`
- Also tags `:latest` on each push

Local alternative (needs Docker + devenv):

```bash
./manage.sh build-frontend-bundle
./manage.sh build-backend-bundle
./manage.sh build-exporter-bundle
cd docker/images
cp -r ../../bundles/frontend ./bundle-frontend
cp -r ../../bundles/backend ./bundle-backend
cp -r ../../bundles/exporter ./bundle-exporter
export PENPOT_DOCKER_NAMESPACE=ghcr.io/dexqbit/penpot
export PENPOT_BUILD_VERSION=latest
# After docker login ghcr.io:
./build.sh frontend
./build.sh backend
./build.sh exporter
```

Note: Dockerfiles pull from `dhi.io`. Authenticate there if pulls are denied.

## 2. VPS layout

On the Hetzner box, create a directory (e.g. `/opt/penpot`) with only:

| File | Source |
|------|--------|
| `docker-compose.yml` | Copy from [`dev-docs/docker-compose.yml`](docker-compose.yml) |
| `.env` | Copy from [`dev-docs/env.example`](env.example), fill secrets |

Do **not** clone this repo onto the VPS for production.

## 3. Configure `.env`

Required:

```bash
PENPOT_PUBLIC_URI=https://penpot.yourdomain.com
PENPOT_SECRET_KEY=<python3 -c "import secrets; print(secrets.token_urlsafe(64))">
PENPOT_DATABASE_PASSWORD=<strong password>
PENPOT_GOOGLE_CLIENT_ID=...
PENPOT_GOOGLE_CLIENT_SECRET=...
PENPOT_ADMINS=you@dexqbit.com
PENPOT_IMAGE_FRONTEND=ghcr.io/dexqbit/penpot-frontend:latest
PENPOT_IMAGE_BACKEND=ghcr.io/dexqbit/penpot-backend:latest
PENPOT_IMAGE_EXPORTER=ghcr.io/dexqbit/penpot-exporter:latest
```

Flags baked into compose:

- `disable-registration`
- `enable-login-with-google`
- `enable-oidc-registration` (needed so first Google login can create profiles)
- Domain lock: `PENPOT_REGISTRATION_DOMAIN_WHITELIST=dexqbit.com` + `PENPOT_GOOGLE_HOSTED_DOMAIN=dexqbit.com`

## 4. TLS (Caddy or Traefik)

Put Caddy/Traefik in front of `localhost:${PENPOT_HTTP_PORT}` (default `9001`). Example Caddyfile:

```
penpot.yourdomain.com {
  reverse_proxy localhost:9001
}
```

Or uncomment Traefik in upstream [`docker/images/docker-compose.yaml`](../docker/images/docker-compose.yaml) and adapt.

## 5. Pull & run

If GHCR packages are private:

```bash
echo "$GHCR_PAT" | docker login ghcr.io -u USERNAME --password-stdin
```

Then:

```bash
cd /opt/penpot
docker compose pull
docker compose up -d
docker compose logs -f penpot-backend
```

Migrations run on backend startup (includes `share_link.expires_at`).

## 6. Platform admins backfill

1. Set `PENPOT_ADMINS` and restart backend
2. Each admin must log in once (so a profile exists)
3. From a backend REPL / one-off container with nREPL, or by creating any new team (auto-adds admins going forward), backfill existing teams:

```clojure
;; In backend sREPL (app.srepl.main):
(backfill-platform-admins!)
```

New teams auto-add all `PENPOT_ADMINS` as owners. Platform admins cannot be removed/downgraded. Only they can delete files/projects.

## 7. Upgrade

1. Merge/rebase upstream Penpot carefully around:
   - `backend/src/app/auth/oidc.clj`
   - `backend/src/app/rpc/commands/teams.clj`
   - `backend/src/app/rpc/commands/files.clj` / `projects.clj` / `files_share.clj` / `viewer.clj`
   - `backend/src/app/binfile/common.clj`
2. Push → wait for GHCR workflow
3. On VPS: `docker compose pull && docker compose up -d`

## 8. Smoke checklist

- [ ] Non-`@dexqbit.com` Google account cannot register/login
- [ ] `@dexqbit.com` Google login works
- [ ] Platform admin sees all teams after backfill
- [ ] Non-admin cannot delete a file (UI hidden + API rejects)
- [ ] Only team admin/owner can create share link; expiry works; expired link 404s
- [ ] Anonymous share viewer does not receive member list
- [ ] Invite to non-`dexqbit.com` email is rejected
