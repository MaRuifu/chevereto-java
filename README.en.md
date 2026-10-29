# chevereto-java

**A Java rewrite of Chevereto V4** — one `java -jar` gives you a complete image hosting site. No PHP, no Nginx, no Redis, no Node build step.

<p>
  <a href="LICENSE"><img alt="License: AGPL-3.0" src="https://img.shields.io/badge/license-AGPL--3.0-blue.svg"></a>
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-orange.svg">
  <img alt="Spring Boot 3.3" src="https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg">
  <img alt="Docker ready" src="https://img.shields.io/badge/docker-ready-2496ED.svg">
</p>

[Features](#features) · [Quick start](#quick-start) · [Configuration](#configuration) · [API](#api) · [Migration](#migrating-from-php-chevereto) · [中文](README.md)

---

## What is this

[Chevereto](https://chevereto.com/) is a popular self-hosted image hosting platform, written in PHP (roughly 19k PHP files, requiring PHP-FPM + Nginx + MySQL).

This project reimplements its core in **Java 17 / Spring Boot 3**, deliberately keeping the original's **data model and URL scheme**:

- **One jar, whole site** — embedded Tomcat, embedded H2 file database by default, zero external dependencies
- **Structurally identical** — all 29 tables match the original production schema column by column; image URLs `/images/**` and short IDs `/image/CeK` use the same algorithm
- **Can take over an existing install** — point it at a PHP Chevereto database and `images` folder and everything keeps working: old links, old password hashes
- **Server-side rendered** — Thymeleaf templates, no Node toolchain to restyle the UI

<!--
  Drop 2–3 screenshots here once you have them (put them in docs/, then uncomment):

  <p align="center">
    <img src="docs/screenshot-home.png" width="45%" alt="Upload page">
    <img src="docs/screenshot-image.png" width="45%" alt="Image page">
  </p>
-->

## Features

| Area | What you get |
|------|--------------|
| **Upload** | Drag & drop, multi-file with progress, URL fetch, base64, guest uploads toggle, size limit, magic-byte format detection, duplicate blocking per IP within 24h (configurable, admins exempt) |
| **Images** | Direct links `/images/**`, `.th` thumbnails, embed codes (HTML / Markdown / BBCode / direct), NSFW blur, edit, delete with storage and counter cleanup |
| **Guest delete** | A one-time delete URL per upload — password stored as bcrypt, plaintext shown once, deleting requires no account |
| **Albums** | Four privacy levels (public / password / private / link-only), password protection, cover, image counters |
| **Users** | Registration and login (BCrypt, compatible with PHP `$2y$` hashes), profiles, settings, API keys, registration flood control |
| **Watermark** | Position, area ratio, opacity, margin, min dimensions, per-role toggles; GIF and animated images skipped; algorithm ported from the original `Image::watermark()` |
| **Storage** | Local disk / S3 (incl. MinIO, Cloudflare R2) / Aliyun OSS / Tencent COS / FTP / SFTP, with key prefix and custom domain |
| **Admin** | Dashboard, site settings, SMTP, NSFW content, flood protection, users, storages, categories, IP bans |
| **Stats** | `chv_stats` totals plus a 30-day chart, rebuildable from live data |
| **API** | Chevereto v1.1 compatible: `/api/1.1/upload`, `/get`, `/delete` |
| **Schema** | 29 tables identical to upstream; both scripts use `CREATE TABLE IF NOT EXISTS`, so existing databases are never altered destructively |

## Quick start

### Option 1 — Docker (recommended)

```bash
git clone https://github.com/your-org/chevereto-java.git
cd chevereto-java
docker compose up -d --build
```

Open http://localhost:8080 — you will be redirected to the setup wizard at `/setup`.

> The first build downloads Maven dependencies inside the container, usually 3–5 minutes; later builds are incremental and fast.

Want an external MySQL too:

```bash
cp .env.example .env      # change the default passwords first
docker compose --profile mysql up -d
```

Then in `/setup` choose MySQL, host `mysql`, port `3306`, with the credentials from `.env`.

**All state lives in `/app/data`:**

```
/app/data/
├── instance.properties   # which database is selected
├── db/                   # embedded H2 files (absent when using MySQL)
└── uploads/              # images stored locally
```

```bash
# backup / restore
docker compose cp app:/app/data ./backup-data
docker compose cp ./backup-data/. app:/app/data

# upgrade: rebuild after pulling, the volume is untouched
git pull && docker compose up -d --build
```

### Option 2 — Run the jar

```bash
# requires Java 17+
java -jar chevereto-java.jar
```

Open http://localhost:8080/setup and pick a database (embedded H2 or MySQL).

### Option 3 — Build locally

```bash
mvn package -DskipTests
java -jar target/chevereto-java.jar --server.port=8080
```

### What the installer does

1. Choose **embedded H2** (no configuration) or **external MySQL** (connection is tested first)
2. The choice is persisted to `./data/instance.properties` and reused on every restart
3. Only an **empty** database gets seeded: settings, default admin, 6 storage drivers, local storage, 5 categories, stats row
4. A database that already has data is **left untouched** — no admin is created, no setting is overwritten

> Default admin is `admin / admin123`. Change it immediately after logging in.

## Configuration

### Flags and environment variables

| Flag | Env var | Default | Description |
|------|---------|---------|-------------|
| `--server.port` | `SERVER_PORT` | `8080` | HTTP port |
| `--spring.datasource.url` | `SPRING_DATASOURCE_URL` | empty | Adopted and persisted to `instance.properties` |
| `--spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | empty | Same |
| `--spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | empty | Same |
| `--app.local-storage-root` | `APP_LOCAL_STORAGE_ROOT` | `./data/uploads` | Root directory for local images |
| `--app.instance-config` | `APP_INSTANCE_CONFIG` | `./data/instance.properties` | Where the DB config lives |
| — | `CHEVERETO_ENCRYPTION_KEY` | empty | Decrypts SMTP passwords from a PHP install |
| — | `JAVA_TOOL_OPTIONS` | — | JVM options, e.g. `-Xmx512m` |
| — | `TZ` | `Asia/Shanghai` | Time zone |

The datasource is **not** hardcoded in `application.yml`; it comes from `./data/instance.properties`. Delete that file to re-run the wizard.

### Storage

Configured under **Admin → Storages**. New uploads go to the first active storage; switching the default does not affect existing files.

| Driver | Required | Notes |
|--------|----------|-------|
| LOCAL | root directory | Served through `/images/**` |
| S3 | bucket, access key, secret | Works with AWS, MinIO, R2; set Endpoint for self-hosted |
| OSS | bucket, endpoint, AK/SK | Aliyun; public URL derived automatically |
| COS | bucket (with APPID), region, secret id/key | Tencent Cloud |
| FTP / SFTP | host, port, credentials, root | Read-only remotes fail loudly instead of silently dropping files |

## API

Chevereto v1.1 compatible. Generate a key under **Settings → API**.

```bash
# file upload
curl -F "source=@photo.jpg" -F "key=YOUR_KEY" http://localhost:8080/api/1.1/upload

# direct URL
curl -F "source_url=https://example.com/a.png" -F "key=YOUR_KEY" http://localhost:8080/api/1.1/upload

# base64
curl -F "source_base64=$(base64 -w0 a.png)" -F "key=YOUR_KEY" http://localhost:8080/api/1.1/upload
```

```json
{
  "status_code": 200,
  "success": { "code": 200, "message": "image uploaded", "name": "photo.jpg" },
  "image": {
    "id": 1234,
    "id_encoded": "CeK",
    "url": "/images/2026/10/29/random12.png",
    "thumb": { "url": "/images/2026/10/29/random12.th.png" },
    "width": 512, "height": 512, "size": 62178, "md5": "..."
  },
  "delete_url": "http://localhost:8080/image/CeK/delete/xxxxx"
}
```

- Lookup: `POST /api/1.1/get` with `id_encoded` / `id` / `md5`
- Delete: `POST /api/1.1/delete` with `id_encoded` + `key` (owner or admin only)

## Migrating from PHP Chevereto

No database migration, no image copying, no data import — just point it at the existing database and image folder:

```bash
java -jar chevereto-java.jar \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/chevereto?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" \
  --spring.datasource.username=chevereto \
  --spring.datasource.password='your-password' \
  --app.local-storage-root=/path/to/chevereto/images
```

Or skip the flags and enter the same values in `/setup`.

What stays compatible:

| Item | How |
|------|-----|
| Image links | `/images/{image_path}` unchanged; the existing folder is served as-is |
| Image and user pages | `/image/{id}` and `/{username}`, decoded with the original `crypt_salt` + `id_padding` |
| Logins | PHP `$2y$` bcrypt hashes are accepted directly |
| Settings | The whole `chv_settings` table is reused (watermark, email, NSFW, flood, `enable_duplicate_uploads`, …) |
| Stats | `chv_stats` is read as-is; rebuild from live data if it drifted |
| New uploads | Still land in `images/{Y/m/d}/{filename}`, so PHP and Java can share one folder |
| Bootstrap | Detected existing users/storages/`chevereto_version_installed` skip initialization entirely |

Missing columns (old forks, partial upgrades) are added on startup by `SchemaMigrator`. It only adds: column types, indexes and existing columns are never modified or dropped.

## Tech stack

Java 17 · Spring Boot 3.3 · MyBatis (annotation-based SQL, no XML) · Thymeleaf · H2 / MySQL 8 · Thumbnailator · metadata-extractor · AWS SDK v2 · aliyun-sdk-oss · cos_api · JSch · commons-net

No full Spring Security — only its `BCryptPasswordEncoder`. Authorization is a hand-written session interceptor.

## Project layout

```
src/main/java/com/chevereto/java/
├── config/      bootstrap, switchable datasource, schema migration, startup banner
├── entity/      one entity per table (29)
├── repository/  MyBatis annotation mappers
├── service/     upload / album / user / settings / watermark / flood / stats / mail / delete tokens
├── storage/     local · S3 · OSS · COS · FTP · SFTP drivers
└── web/         page controllers + API v1.1 + image serving

src/main/resources/
├── schema-mysql.sql / schema-h2.sql   the 29 tables
├── templates/   Thymeleaf pages
└── static/      CSS / JS / icons / default watermark

tools/           ops scripts (not part of the build): schema generator, legacy image importer
```

## Roadmap

- [ ] Email confirmation (`require_user_email_confirmation`)
- [ ] ModerateContent moderation and a pending-approval queue
- [ ] Likes and follows (tables already exist)
- [ ] i18n, social login, two-factor auth
- [ ] Lightbox navigation and keyboard shortcuts

## Contributing

Issues and pull requests are welcome. Before submitting:

1. `mvn package` has to pass
2. Schema changes must be applied to **both** `schema-mysql.sql` and `schema-h2.sql`
3. Behaviour that mirrors the original should reference the upstream file/function in the PR

## License

[AGPL-3.0](LICENSE), matching the upstream Chevereto V4 license.

You are free to use, modify and sell it. If you run a modified version as a public service, you must publish your modified source.

## Credits

- [Chevereto](https://github.com/chevereto/chevereto) — the feature blueprint; schema, URL scheme and some algorithms (ID encoding, watermark) derive from its AGPL-3.0 code
- Icons by [Font Awesome](https://fontawesome.com/), image resizing by [Thumbnailator](https://github.com/coobird/thumbnailator)

This is an independent implementation. It is not affiliated with or endorsed by the Chevereto project.
