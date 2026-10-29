# chevereto-java

**Chevereto V4 图床系统的 Java 重写版** —— 一个 `java -jar` 就是完整站点，不需要 PHP、Nginx、Redis，也不需要 Node 构建前端。

<p>
  <a href="LICENSE"><img alt="License: AGPL-3.0" src="https://img.shields.io/badge/license-AGPL--3.0-blue.svg"></a>
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-orange.svg">
  <img alt="Spring Boot 3.3" src="https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg">
  <img alt="Docker ready" src="https://img.shields.io/badge/docker-ready-2496ED.svg">
</p>

[特性](#特性) · [快速开始](#快速开始) · [配置](#配置) · [API](#api) · [从原版迁移](#从原版-chevereto-迁移) · [English](README.en.md)

---

## 这是什么

[Chevereto](https://chevereto.com/) 是流行的自建图床，原版用 PHP 写成（近 1.9 万个 PHP 文件，跑起来要 PHP-FPM + Nginx + MySQL）。

本项目用 **Java 17 / Spring Boot 3** 把它的核心功能重新实现了一遍，并且刻意保留它的**数据结构和 URL 规则**：

- **一个 jar 就是整站**：内置 Tomcat，默认用内嵌 H2 文件库，零外部依赖起步
- **和原版同构**：29 张表与原版生产库逐列一致，图片直链 `/images/**`、短码 ID `/image/CeK` 的算法完全照搬
- **可以接管老站**：把原版 Chevereto 的数据库和 `images` 目录直接指过来就能跑，老链接、老账号密码全部有效
- **服务端渲染**：Thymeleaf 模板，改样式不用装 Node

<!--
  建议放 2–3 张截图，开源项目有图 star 率明显更高（截图放 docs/ 目录，取消下面注释即可）：

  <p align="center">
    <img src="docs/screenshot-home.png" width="45%" alt="上传首页">
    <img src="docs/screenshot-image.png" width="45%" alt="图片详情页">
  </p>
-->

## 特性

| 模块 | 能力 |
|------|------|
| **上传** | 拖拽 / 多选 / 进度条、网页链接抓取、base64、游客上传开关、体积上限、按文件头识别真实格式（不信扩展名）、同 IP 24 小时重复上传拦截（可关、管理员豁免） |
| **图片** | 直链 `/images/**`、`.th` 缩略图、详情页嵌入代码（HTML / Markdown / BBCode / 直链一键复制）、NSFW 模糊遮罩、编辑、删除联动清理磁盘与计数 |
| **游客删图** | 上传后发一条一次性删图链接，口令 bcrypt 入库、明文只显示一次，凭链接删图无需登录（对齐原版行为） |
| **专辑** | 四档隐私（公开 / 密码 / 私有 / 仅链接可见）、密码保护、封面、图片计数 |
| **用户** | 注册登录（BCrypt，兼容 PHP `$2y$` 哈希）、个人主页、资料/密码/API Key、注册洪水拦截 |
| **水印** | 位置、面积占比、不透明度、边距、最小尺寸、按角色开关；GIF 与动图自动跳过；算法移植原版 `Image::watermark()` |
| **存储** | 本地磁盘 / S3（含 MinIO、Cloudflare R2）/ 阿里云 OSS / 腾讯云 COS / FTP / SFTP，可配 Key 前缀与自定义域名 |
| **后台** | 仪表盘、站点设置、邮箱 SMTP、内容与 NSFW、洪水保护、用户、外部存储、分类、IP 封禁 |
| **统计** | `chv_stats` 累计 + 近 30 天柱状图，支持按现有数据重建 |
| **API** | 兼容 Chevereto v1.1：`/api/1.1/upload`、`/get`、`/delete` |
| **数据** | 29 张表与原版逐列一致，两份建表脚本（MySQL / H2）都是 `CREATE TABLE IF NOT EXISTS`，对已有库零改动 |

## 快速开始

### 方式一：Docker（推荐）

```bash
git clone https://github.com/your-org/chevereto-java.git
cd chevereto-java
docker compose up -d --build
```

打开 http://localhost:8080 ，会自动跳到安装向导 `/setup`。

> 首次构建要在容器里下 Maven 依赖，通常 3–5 分钟；之后再构建只跑增量，很快。

想用外部 MySQL 一起起：

```bash
cp .env.example .env      # 先改掉里面的默认密码
docker compose --profile mysql up -d
```

然后在 `/setup` 里选 MySQL，主机填 `mysql`、端口 `3306`、库名与账号按 `.env` 填。

**数据都在 `/app/data` 里**，一个目录装下全部状态：

```
/app/data/
├── instance.properties   # 选了哪个数据库
├── db/                   # 内置 H2 文件库（用 MySQL 时没有这个目录）
└── uploads/              # 本地存储的图片
```

```bash
# 备份 / 恢复
docker compose cp app:/app/data ./backup-data
docker compose cp ./backup-data/. app:/app/data

# 升级：代码更新后重建镜像，数据卷不受影响
git pull && docker compose up -d --build
```

### 方式二：直接跑 jar

```bash
# 需要 Java 17+
java -jar chevereto-java.jar
```

首次启动打开 http://localhost:8080/setup 选择数据库（内置 H2 或 MySQL）。

### 方式三：本地构建

```bash
mvn package -DskipTests
java -jar target/chevereto-java.jar --server.port=8080
```

### 安装向导会做什么

1. 选择 **内置 H2**（零配置，单文件库）或 **外部 MySQL**（会先测连通性）
2. 选择结果写入 `./data/instance.properties`，之后启动自动沿用
3. **空库**才初始化：站点设置、默认管理员、6 个存储驱动、本地存储、5 个分类、统计累计行
4. **已有数据的库完全跳过初始化** —— 不建管理员、不覆盖任何设置，这是接管老站的关键

> 默认管理员 `admin / admin123`，登录后请立刻改密码。

## 配置

### 启动参数 / 环境变量

| 参数 | 环境变量 | 默认值 | 说明 |
|------|----------|--------|------|
| `--server.port` | `SERVER_PORT` | `8080` | HTTP 端口 |
| `--spring.datasource.url` | `SPRING_DATASOURCE_URL` | 空 | 指定即用，并写入 `instance.properties` |
| `--spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | 空 | 同上 |
| `--spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | 空 | 同上 |
| `--app.local-storage-root` | `APP_LOCAL_STORAGE_ROOT` | `./data/uploads` | 本地图片根目录 |
| `--app.instance-config` | `APP_INSTANCE_CONFIG` | `./data/instance.properties` | 数据库配置文件位置 |
| — | `CHEVERETO_ENCRYPTION_KEY` | 空 | 接管原版库时用来解密 SMTP 密码 |
| — | `JAVA_TOOL_OPTIONS` | — | JVM 参数，如 `-Xmx512m` |
| — | `TZ` | `Asia/Shanghai` | 时区 |

数据源**不在** `application.yml` 里写死，而由 `./data/instance.properties` 决定 —— 想重新走向导，删掉这个文件再启动即可。

### 存储

后台 **存储** 页面添加，新上传走「当前启用的第一个存储」，切换默认存储不影响已有文件的直链。

| 驱动 | 必填 | 说明 |
|------|------|------|
| LOCAL | 磁盘根目录 | 通过站内 `/images/**` 对外服务 |
| S3 | Bucket、Access Key、Secret Key | 兼容 AWS / MinIO / R2，自建填 Endpoint |
| OSS | Bucket、Endpoint、AK/SK | 阿里云，未配自定义域名时自动推导 |
| COS | Bucket（含 APPID）、Region、SecretId/Key | 腾讯云 |
| FTP / SFTP | 主机、端口、账号、密码/私钥、根路径 | 可读不可写时上传会明确报错 |

## API

兼容 Chevereto v1.1。先在 **设置 → API** 生成 Key。

```bash
# 文件上传
curl -F "source=@photo.jpg" -F "key=YOUR_KEY" http://localhost:8080/api/1.1/upload

# 从 URL 抓
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

- 查询：`POST /api/1.1/get`，参数 `id_encoded` / `id` / `md5`
- 删除：`POST /api/1.1/delete`，参数 `id_encoded` + `key`（仅 Key 属主或管理员）

## 从原版 Chevereto 迁移

**不改原库、不复制图片、不导数据**，直接把现有数据库和图片目录指过来：

```bash
java -jar chevereto-java.jar \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/chevereto?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" \
  --spring.datasource.username=chevereto \
  --spring.datasource.password='你的密码' \
  --app.local-storage-root=/path/to/chevereto/images
```

不传数据源参数也行，打开 `/setup` 选 MySQL 手填。

自动保持兼容的部分：

| 项 | 说明 |
|----|------|
| 图片直链 | `/images/{image_path}` 与原版一致，老图片目录直接对外服务 |
| 详情页 / 用户页 | `/image/{老短码}`、`/{username}`，短码算法读原库 `crypt_salt` + `id_padding` |
| 用户登录 | 原版 `$2y$` bcrypt 直接可用 |
| 设置项 | `chv_settings` 全量沿用（水印、邮箱、NSFW、洪水、`enable_duplicate_uploads`…） |
| 统计 | 原库 `chv_stats` 直接可读，有偏差可在后台「重建统计」 |
| 写入路径 | 新图仍按 `images/{Y/m/d}/{文件名}` 落盘，PHP 版和 Java 版可以混用同一目录 |
| 初始化 | 检测到已有用户/存储/`chevereto_version_installed` 就跳过初始化 |

老库缺列（早期版本或原版升级遗留）会在启动时由 `SchemaMigrator` 自动补上，只补不删：不改列类型、不动索引、不删列。

## 技术栈

Java 17 · Spring Boot 3.3 · MyBatis（纯注解 SQL，无 XML）· Thymeleaf · H2 / MySQL 8 · Thumbnailator · metadata-extractor · AWS SDK v2 · aliyun-sdk-oss · cos_api · JSch · commons-net

没有 Spring Security 全家桶，只用了它的 `BCryptPasswordEncoder`；鉴权是自实现的 Session 拦截器。

## 目录结构

```
src/main/java/com/chevereto/java/
├── config/      启动初始化、数据源热切换、建表与补列、启动横幅
├── entity/      29 张表对应实体
├── repository/  MyBatis 注解 Mapper
├── service/     上传 / 相册 / 用户 / 设置 / 水印 / 洪水 / 统计 / 邮件 / 删图口令
├── storage/     本地 · S3 · OSS · COS · FTP · SFTP 六个驱动
└── web/         页面控制器 + API v1.1 + 图片静态服务

src/main/resources/
├── schema-mysql.sql / schema-h2.sql   29 张表建表脚本
├── templates/   Thymeleaf 页面（首页 / 探索 / 图片 / 专辑 / 用户 / 设置 / 后台 11 页）
└── static/      CSS / JS / 图标 / 默认水印

tools/           运维脚本（不参与构建）：schema 生成器、旧图补录工具
```

## 路线图

- [ ] 邮箱激活（`require_user_email_confirmation`）
- [ ] ModerateContent 自动审核与待审队列
- [ ] 点赞与关注（表已建好）
- [ ] 多语言 i18n、社交登录、两步验证
- [ ] 灯箱轮播、键盘快捷键等交互细节

## 贡献

欢迎 issue 和 PR。提交前请确认：

1. `mvn package` 能过（不要求写单测，但别把编译搞坏）
2. 改数据库结构时，`schema-mysql.sql` 和 `schema-h2.sql` **两份都要改**
3. 涉及原版行为的改动，在 PR 里附上原版对应文件/函数名，方便对照

## 许可证

[AGPL-3.0](LICENSE) —— 与 Chevereto V4 上游许可证一致。

这意味着：你可以自由使用、修改、商用；但如果你把它改成公开的图床服务对外提供，需要一并公开你修改后的源码。

## 致谢

- [Chevereto](https://github.com/chevereto/chevereto) —— 本项目的功能蓝本，表结构、URL 规则与部分算法（ID 编码、水印）源自其 AGPL-3.0 代码
- 图标 [Font Awesome](https://fontawesome.com/)，图像处理 [Thumbnailator](https://github.com/coobird/thumbnailator)

本项目是独立实现，与 Chevereto 官方无隶属关系，也未获得其背书。
