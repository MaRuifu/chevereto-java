# tools

一次性 / 运维用脚本，**不参与构建，也不参与应用运行时**。

`mvn package` 不会编译这里（源码目录是 `src/main/java`），Docker 镜像也不会带上它们。
需要时手动执行即可。

| 文件 | 用途 | 什么时候用 |
|---|---|---|
| `gen_schema.py` | 从一份原版 Chevereto 的 `mysqldump` 生成 `schema-h2.sql` + `schema-mysql.sql`，并逐列校验一致性 | 需要重建 schema、或升级到新版本对齐表结构时 |
| `ImportMissingImages.java` | 把原版 `images/` 目录里「数据库没有记录」的图片补录进 `chv_images`（含缺失缩略图生成） | 从原版 PHP 版迁移，磁盘上有图但库里的记录不全时 |

## gen_schema.py

需要一份**原版 Chevereto V4** 的 dump（仓库里不附带）：

```bash
mysqldump -u root -p --no-data chevereto > chevereto.sql
python3 tools/gen_schema.py chevereto.sql
```

脚本会写 `src/main/resources/schema-h2.sql` 与 `schema-mysql.sql`，并逐表比对列定义；
有任何一列与 dump 不一致就以退出码 1 结束。改完记得 review `git diff`。

为什么是两份：H2 不支持 `col(255)` 前缀索引和 `FULLTEXT`，而 MySQL 的
`varchar(4096)` 建唯一键必须用前缀索引，同一份 DDL 表达不了。

## ImportMissingImages.java

这是一次性迁移脚本（无 package、只依赖 JDK + 对应数据库的 JDBC 驱动）。

> **最重要的一条前提**：`--root` 必须等于应用实际使用的图片根目录
> （`app.local-storage-root`，默认 `./data/uploads`）。
> 导入时日期目录的图片会把 `image_path` 写成 NULL，由 `image_date` 推导出
> `Y/m/d/文件名` 去磁盘找 —— 如果 `--root` 和存储根不是同一个目录，
> 记录插进去了但图片全 404。迁移原版数据时，应用也要用
> `--app.local-storage-root=/path/to/chevereto/images` 启动。

MySQL 实例：

```bash
javac -cp mysql-connector-j-8.x.x.jar tools/ImportMissingImages.java

java -cp .:mysql-connector-j-8.x.x.jar ImportMissingImages \
     --root=/path/to/chevereto/images --user=2 --dry-run

java -cp .:mysql-connector-j-8.x.x.jar ImportMissingImages \
     --root=/path/to/chevereto/images --user=2 \
     --url='jdbc:mysql://127.0.0.1:3306/chevereto?...&useSSL=false' \
     --db-user=root --db-pass=你的密码
```

内置 H2 实例（URL 抄 `data/instance.properties` 里那条，驱动换成 H2 的 jar）：

```bash
java -cp .:h2-2.2.224.jar ImportMissingImages \
     --root=/path/to/chevereto/images --user=1 --dry-run \
     --url='jdbc:h2:file:./data/db/chevereto;MODE=MySQL;AUTO_SERVER=TRUE' \
     --db-user=sa --db-pass=
```

H2 带 `AUTO_SERVER=TRUE` 时应用运行中也能连；`--db-pass=` 留空表示空密码。

`--user` 是图片归属者的 `chv_users.user_id`，别猜——先查：

```sql
SELECT user_id, user_username FROM chv_users;
```

| 参数 | 说明 |
|---|---|
| `--root=` | 原版 `images/` 目录（必填，且应等于 `app.local-storage-root`） |
| `--user=` | 图片归属用户的 `chv_users.user_id`（必填，避免误挂到别人账号下） |
| `--dry-run` | 只扫描比对，不写库、不生成缩略图（**建议先跑**） |
| `--no-thumbs` | 不补生成缺失的 `.th.` 缩略图（只是比对时可加速） |
| `--thumb-size=` | 缩略图长边，默认 320 |
| `--limit=N` | 只处理前 N 个（分批试跑用） |
| `--url=` / `--db-user=` / `--db-pass=` | 数据库连接，也可用 `DB_URL` / `DB_USER` / `DB_PASSWORD` |

行为要点：

- 文件名与扩展名一律取**磁盘真实值**（有 15 个文件的扩展名与内容不符，按文件头判定真实格式后再写库）
- `Y/m/d` 形式的日期目录：写 `image_path = NULL`，由 `image_date` 推导路径（与原版一致）
- 其它目录（如 `blog/blogimg`）：把真实相对路径写进 `image_path`
- 跳过 `.th.` / `.md.` 等派生物和非图片文件；原图不动，只补缩略图
- webp 不生成缩略图（JDK 自带解码器不支持）
- **只增不删**：不做去重判断（不去比对 md5），也不会清理库里多出来的记录；
  跑之前先 `--dry-run` 确认「需要补录」的条数符合预期
- `image_uploader_ip` 统一写 `127.0.0.1`，`image_album_id` / `image_category_id` 留空
- 导入后不会自动更新 `chv_users.user_image_count`，需要的话手动补一条 UPDATE
