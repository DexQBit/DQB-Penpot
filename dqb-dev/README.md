# DexQBit Penpot — How to Run

Step-by-step guide to run this Penpot fork with Docker Compose (local machine or Hetzner VPS).

For CI image builds, TLS extras, upgrades, and admin backfill details, see [DEPLOY.md](DEPLOY.md).

---

## What you need

- Docker Engine + Docker Compose v2
- A Google Cloud OAuth **Web** client (Workspace `@dexqbit.com`)
- Access to the fork’s GHCR images (`ghcr.io/<owner>/penpot-*`), or build them first ([DEPLOY.md](DEPLOY.md#1-build--push-images-ci))

---

## Step 1 — Create a run directory

Do this on the host that will run Penpot (laptop for a smoke test, or `/opt/penpot` on the VPS).

```bash
mkdir -p /opt/penpot
cd /opt/penpot
```

Copy these files from this repo’s `dev-docs/` folder:

| Copy from repo | Into run directory as |
|----------------|------------------------|
| `dev-docs/docker-compose.yml` | `docker-compose.yml` |
| `dev-docs/env.example` | `.env` |

```bash
# From the repo root (adjust paths if you use scp to a remote host):
cp dev-docs/docker-compose.yml /opt/penpot/docker-compose.yml
cp dev-docs/env.example /opt/penpot/.env
```

Do **not** put the full source tree on a production VPS — only compose + `.env`.

---

## Step 2 — Create Google OAuth credentials

1. Open [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials.
2. Create an OAuth client ID → Application type **Web application**.
3. Add authorized redirect URI (must match your public URL):

   ```
   https://YOUR_DOMAIN/api/auth/oidc/callback
   ```

   For a local HTTP test only:

   ```
   http://localhost:9001/api/auth/oidc/callback
   ```

4. Copy the **Client ID** and **Client secret**.

---

## Step 3 — Fill in `.env`

Edit `/opt/penpot/.env`:

```bash
# Public URL users open in the browser (no trailing slash)
PENPOT_PUBLIC_URI=https://YOUR_DOMAIN

# Generate a secret:
#   python3 -c "import secrets; print(secrets.token_urlsafe(64))"
PENPOT_SECRET_KEY=...

# Host port → container port (frontend)
PENPOT_HTTP_PORT=9001
PENPOT_HTTP_CONTAINER_PORT=8080

# Images from GHCR (change owner/tag after your CI run)
PENPOT_IMAGE_FRONTEND=ghcr.io/dexqbit/penpot-frontend:latest
PENPOT_IMAGE_BACKEND=ghcr.io/dexqbit/penpot-backend:latest
PENPOT_IMAGE_EXPORTER=ghcr.io/dexqbit/penpot-exporter:latest

PENPOT_DATABASE_PASSWORD=choose-a-strong-password

PENPOT_GOOGLE_CLIENT_ID=...
PENPOT_GOOGLE_CLIENT_SECRET=...

# Platform admins (must be @dexqbit.com; they log in once, then backfill — see Step 7)
PENPOT_ADMINS=you@dexqbit.com

# SMTP (required for invites / some emails in production)
PENPOT_SMTP_DEFAULT_FROM=Penpot <noreply@dexqbit.com>
PENPOT_SMTP_DEFAULT_REPLY_TO=Penpot <noreply@dexqbit.com>
PENPOT_SMTP_HOST=...
PENPOT_SMTP_PORT=587
PENPOT_SMTP_USERNAME=...
PENPOT_SMTP_PASSWORD=...
PENPOT_SMTP_TLS=true
PENPOT_SMTP_SSL=false
```

Compose already enables:

- `disable-registration`
- `enable-login-with-google`
- `enable-oidc-registration`
- domain lock to `dexqbit.com`

---

## Step 4 — Log in to GHCR (if packages are private)

```bash
# Create a GitHub PAT with read:packages (and write:packages if you push images)
echo "$GHCR_PAT" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

---

## Step 5 — Start the stack

```bash
cd /opt/penpot
docker compose pull
docker compose up -d
```

Check that containers are healthy:

```bash
docker compose ps
docker compose logs -f penpot-backend
```

Wait until the backend finishes migrations and stays up (no crash loop).

Open:

```
http://localhost:9001
```

(or `https://YOUR_DOMAIN` once TLS is in front — Step 6).

---

## Step 6 — Put TLS in front (production)

Point DNS A/AAAA records at the server. Example **Caddy** reverse proxy to the compose HTTP port:

```
YOUR_DOMAIN {
  reverse_proxy localhost:9001
}
```

Use the same host port as `PENPOT_HTTP_PORT` in `.env`.

Then set `PENPOT_PUBLIC_URI=https://YOUR_DOMAIN`, update the Google redirect URI, and restart:

```bash
docker compose up -d
```

---

## Step 7 — First login and platform admins

1. Open the app → **Continue with Google** with a `@dexqbit.com` account.
2. Non-`@dexqbit.com` Google accounts are rejected.
3. For each email in `PENPOT_ADMINS`, log in once so a profile exists.
4. Backfill admins onto **existing** teams (new teams get them automatically):

   See [DEPLOY.md § Platform admins backfill](DEPLOY.md#6-platform-admins-backfill) and run:

   ```clojure
   (backfill-platform-admins!)
   ```

---

## Step 8 — Everyday commands

```bash
cd /opt/penpot

# Status
docker compose ps

# Logs
docker compose logs -f penpot-frontend
docker compose logs -f penpot-backend

# Restart after .env change
docker compose up -d

# Stop
docker compose down

# Upgrade images after a new CI build
docker compose pull
docker compose up -d
```

---

## Smoke checklist

- [ ] App loads at `PENPOT_PUBLIC_URI`
- [ ] `@dexqbit.com` Google login works
- [ ] Other Google accounts cannot sign in
- [ ] Platform admin sees teams after backfill
- [ ] Non-admin cannot delete files/projects
- [ ] Team admin can create an expiring share link; client view hides members

---

## Customizations (summary)

| Feature | Behavior |
|---------|----------|
| Login | Google Workspace `@dexqbit.com` only |
| Platform admins | `PENPOT_ADMINS` — auto-added as owners on every team; only they can delete files/projects |
| Share links | Team admin/owner only; optional expiry; anonymous viewer does not see members |
| Invites | Outside `dexqbit.com` rejected |

---

## Files in this folder

| File | Purpose |
|------|---------|
| [README.md](README.md) | This run guide |
| [DEPLOY.md](DEPLOY.md) | CI/GHCR, Hetzner, TLS, upgrades, admin backfill |
| [docker-compose.yml](docker-compose.yml) | Production-style compose (copy to the host) |
| [env.example](env.example) | `.env` template |
