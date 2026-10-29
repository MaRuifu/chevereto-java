-- =============================================================================
-- Chevereto 数据表结构 —— H2 (MODE=MySQL) 版本：29 张表
-- =============================================================================
-- 由 tools/gen_schema.py 从生产 dump 自动生成，请勿手改。
-- 列名 / 类型 / 默认值与生产库逐列一致（生成时自动校验）。
--
-- 仅做 H2 必需的语法降级（不改变列定义与语义）：
--   1. FULLTEXT KEY        -> 移除（H2 不支持；搜索走 LIKE，不依赖全文索引）
--   2. 前缀索引 (col(255)) -> (col)（H2 不支持前缀长度）
--   3. ENGINE/CHARSET/COLLATE/AUTO_INCREMENT=n -> 移除
--   4. 反引号标识符 -> 裸标识符（H2 对引号内名称大小写敏感）
--   5. 保留字索引名 key -> asset_key（索引名不参与任何查询）
-- 全部 CREATE TABLE IF NOT EXISTS：已存在的库零改动。
-- =============================================================================



-- ---------------------------------------------------------------------------
-- chv_users：用户
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_users (
  user_id bigint NOT NULL AUTO_INCREMENT,
  user_name varchar(255) DEFAULT NULL,
  user_username varchar(255) NOT NULL,
  user_date datetime NOT NULL,
  user_date_gmt datetime NOT NULL,
  user_email varchar(255) DEFAULT NULL,
  user_avatar_filename varchar(255) DEFAULT NULL,
  user_facebook_username varchar(255) DEFAULT NULL,
  user_twitter_username varchar(255) DEFAULT NULL,
  user_website varchar(255) DEFAULT NULL,
  user_background_filename varchar(255) DEFAULT NULL,
  user_bio varchar(255) DEFAULT NULL,
  user_timezone varchar(255) NOT NULL,
  user_language varchar(255) DEFAULT NULL,
  user_status enum('valid','awaiting-confirmation','awaiting-email','banned') NOT NULL,
  user_is_admin tinyint(1) NOT NULL DEFAULT '0',
  user_is_manager tinyint(1) NOT NULL DEFAULT '0',
  user_is_private tinyint(1) NOT NULL DEFAULT '0',
  user_palette_id int NOT NULL DEFAULT '0',
  user_newsletter_subscribe tinyint(1) NOT NULL DEFAULT '1',
  user_show_nsfw_listings tinyint(1) NOT NULL DEFAULT '0',
  user_image_count bigint NOT NULL DEFAULT '0',
  user_album_count bigint NOT NULL DEFAULT '0',
  user_image_keep_exif tinyint(1) NOT NULL DEFAULT '1',
  user_image_expiration varchar(255) DEFAULT NULL,
  user_registration_ip varchar(255) NOT NULL,
  user_likes bigint NOT NULL DEFAULT '0' COMMENT 'Likes made to content owned by this user',
  user_liked bigint NOT NULL DEFAULT '0' COMMENT 'Likes made by this user',
  user_following bigint NOT NULL DEFAULT '0',
  user_followers bigint NOT NULL DEFAULT '0',
  user_content_views bigint NOT NULL DEFAULT '0',
  user_notifications_unread bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (user_id),
  UNIQUE KEY username (user_username),
  UNIQUE KEY email (user_email),
  KEY user_date_gmt (user_date_gmt),
  KEY user_status (user_status),
  KEY user_is_admin (user_is_admin),
  KEY user_is_manager (user_is_manager),
  KEY user_is_private (user_is_private),
  KEY user_palette_id (user_palette_id),
  KEY user_newsletter_subscribe (user_newsletter_subscribe),
  KEY user_show_nsfw_listings (user_show_nsfw_listings),
  KEY user_image_count (user_image_count),
  KEY user_album_count (user_album_count),
  KEY user_image_keep_exif (user_image_keep_exif),
  KEY user_image_expiration (user_image_expiration),
  KEY user_registration_ip (user_registration_ip),
  KEY user_likes (user_likes),
  KEY user_following (user_following),
  KEY user_followers (user_followers),
  KEY user_liked (user_liked),
  KEY user_content_views (user_content_views)
);

-- ---------------------------------------------------------------------------
-- chv_login_passwords：用户登录密码（bcrypt，兼容原版）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_login_passwords (
  login_password_id bigint NOT NULL AUTO_INCREMENT,
  login_password_user_id bigint NOT NULL,
  login_password_date_gmt datetime NOT NULL,
  login_password_hash mediumtext NOT NULL,
  PRIMARY KEY (login_password_id),
  UNIQUE KEY login_password_user_id (login_password_user_id)
);

-- ---------------------------------------------------------------------------
-- chv_images：图片主表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_images (
  image_id bigint NOT NULL AUTO_INCREMENT,
  image_name varchar(255) NOT NULL,
  image_extension varchar(255) NOT NULL,
  image_size int NOT NULL,
  image_width int NOT NULL,
  image_height int NOT NULL,
  image_date datetime NOT NULL,
  image_date_gmt datetime NOT NULL,
  image_title varchar(100) DEFAULT NULL,
  image_description mediumtext,
  image_nsfw tinyint(1) NOT NULL DEFAULT '0',
  image_user_id bigint DEFAULT NULL,
  image_album_id bigint DEFAULT NULL,
  image_uploader_ip varchar(255) NOT NULL,
  image_storage_mode enum('datefolder','direct','old','path') NOT NULL DEFAULT 'datefolder',
  image_path varchar(4096) DEFAULT NULL,
  image_storage_id bigint DEFAULT NULL,
  image_md5 varchar(32) NOT NULL,
  image_source_md5 varchar(32) DEFAULT NULL,
  image_original_filename varchar(255) NOT NULL,
  image_original_exifdata longtext,
  image_views bigint NOT NULL DEFAULT '0',
  image_category_id bigint DEFAULT NULL,
  image_chain tinyint NOT NULL,
  image_thumb_size int NOT NULL,
  image_medium_size int NOT NULL DEFAULT '0',
  image_expiration_date_gmt datetime DEFAULT NULL,
  image_likes bigint NOT NULL DEFAULT '0',
  image_is_animated tinyint(1) NOT NULL DEFAULT '0',
  image_is_approved tinyint(1) NOT NULL DEFAULT '1',
  image_is_360 tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (image_id),
  KEY image_name (image_name),
  KEY image_extension (image_extension),
  KEY image_size (image_size),
  KEY image_width (image_width),
  KEY image_height (image_height),
  KEY image_date_gmt (image_date_gmt),
  KEY image_nsfw (image_nsfw),
  KEY image_user_id (image_user_id),
  KEY image_album_id (image_album_id),
  KEY image_uploader_ip (image_uploader_ip),
  KEY image_storage_mode (image_storage_mode),
  KEY image_path (image_path),
  KEY image_storage_id (image_storage_id),
  KEY image_md5 (image_md5),
  KEY image_source_md5 (image_source_md5),
  KEY image_views (image_views),
  KEY image_category_id (image_category_id),
  KEY image_chain (image_chain),
  KEY image_expiration_date_gmt (image_expiration_date_gmt),
  KEY image_likes (image_likes),
  KEY image_is_animated (image_is_animated),
  KEY image_is_approved (image_is_approved),
  KEY image_is_360 (image_is_360),
  KEY image_album_id_image_id (image_album_id,image_id)
);

-- ---------------------------------------------------------------------------
-- chv_images_hash：游客删图口令（bcrypt），对应 /image/<ID>/delete/<明文>
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_images_hash (
  image_hash_image_id bigint NOT NULL,
  image_hash_hash mediumtext NOT NULL,
  PRIMARY KEY (image_hash_image_id)
);

-- ---------------------------------------------------------------------------
-- chv_albums：相册
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_albums (
  album_id bigint NOT NULL AUTO_INCREMENT,
  album_name varchar(100) NOT NULL,
  album_user_id bigint DEFAULT NULL,
  album_date datetime NOT NULL,
  album_date_gmt datetime NOT NULL,
  album_creation_ip varchar(255) NOT NULL,
  album_privacy enum('public','password','private','private_but_link','custom') DEFAULT 'public',
  album_privacy_extra mediumtext,
  album_password mediumtext,
  album_image_count bigint NOT NULL DEFAULT '0',
  album_description mediumtext,
  album_likes bigint NOT NULL DEFAULT '0',
  album_views bigint NOT NULL DEFAULT '0',
  album_cover_id bigint DEFAULT NULL,
  album_parent_id bigint DEFAULT NULL,
  PRIMARY KEY (album_id),
  KEY album_name (album_name),
  KEY album_user_id (album_user_id),
  KEY album_date_gmt (album_date_gmt),
  KEY album_privacy (album_privacy),
  KEY album_image_count (album_image_count),
  KEY album_creation_ip (album_creation_ip)
);

-- ---------------------------------------------------------------------------
-- chv_categories：图片分类（探索页筛选）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_categories (
  category_id bigint NOT NULL AUTO_INCREMENT,
  category_name varchar(32) NOT NULL,
  category_url_key varchar(32) NOT NULL,
  category_description mediumtext,
  PRIMARY KEY (category_id),
  UNIQUE KEY url_key (category_url_key)
);

-- ---------------------------------------------------------------------------
-- chv_likes：点赞记录（图片 / 相册，支持游客按 IP）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_likes (
  like_id bigint NOT NULL AUTO_INCREMENT,
  like_date datetime NOT NULL,
  like_date_gmt datetime NOT NULL,
  like_user_id bigint DEFAULT NULL,
  like_content_type enum('image','album') DEFAULT NULL,
  like_content_id bigint NOT NULL,
  like_content_user_id bigint DEFAULT NULL,
  like_ip varchar(255) NOT NULL,
  PRIMARY KEY (like_id),
  KEY like_date_gmt (like_date_gmt),
  KEY like_user_id (like_user_id),
  KEY like_content_type (like_content_type),
  KEY like_content_id (like_content_id),
  KEY like_content_user_id (like_content_user_id),
  KEY like_ip (like_ip)
);

-- ---------------------------------------------------------------------------
-- chv_follows：用户关注关系
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_follows (
  follow_id bigint NOT NULL AUTO_INCREMENT,
  follow_date datetime NOT NULL,
  follow_date_gmt datetime NOT NULL,
  follow_user_id bigint NOT NULL,
  follow_followed_user_id bigint NOT NULL,
  follow_ip varchar(255) NOT NULL,
  PRIMARY KEY (follow_id),
  KEY follow_user_id (follow_user_id),
  KEY follow_followed_user_id (follow_followed_user_id)
);

-- ---------------------------------------------------------------------------
-- chv_notifications：站内通知（被赞 / 被关注）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_notifications (
  notification_id bigint NOT NULL AUTO_INCREMENT,
  notification_date_gmt datetime NOT NULL,
  notification_user_id bigint NOT NULL,
  notification_trigger_user_id bigint DEFAULT NULL,
  notification_type enum('follow','like') NOT NULL,
  notification_content_type enum('user','image','album') NOT NULL,
  notification_type_id bigint NOT NULL COMMENT 'type_id based on action (type) table',
  notification_is_read tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (notification_id),
  KEY notification_date_gmt (notification_date_gmt),
  KEY notification_user_id (notification_user_id),
  KEY notification_trigger_user_id (notification_trigger_user_id),
  KEY notification_type (notification_type),
  KEY notification_content_type (notification_content_type),
  KEY notification_type_id (notification_type_id),
  KEY notification_is_read (notification_is_read)
);

-- ---------------------------------------------------------------------------
-- chv_deletions：已删除内容的审计墓碑（风控追溯）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_deletions (
  deleted_id bigint NOT NULL AUTO_INCREMENT,
  deleted_date_gmt datetime NOT NULL,
  deleted_content_id bigint NOT NULL,
  deleted_content_date_gmt datetime NOT NULL,
  deleted_content_user_id bigint DEFAULT NULL,
  deleted_content_ip varchar(255) NOT NULL,
  deleted_content_md5 varchar(32) DEFAULT NULL,
  deleted_content_original_filename varchar(255) DEFAULT NULL,
  deleted_content_views bigint NOT NULL DEFAULT '0',
  deleted_content_likes bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (deleted_id),
  KEY deleted_content_id (deleted_content_id),
  KEY deleted_content_user_id (deleted_content_user_id),
  KEY deleted_content_ip (deleted_content_ip),
  KEY deleted_content_md5 (deleted_content_md5),
  KEY deleted_content_views (deleted_content_views),
  KEY deleted_content_likes (deleted_content_likes)
);

-- ---------------------------------------------------------------------------
-- chv_confirmations：邮箱确认令牌（激活 / 改邮箱 / 找回密码），只存哈希
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_confirmations (
  confirmation_id bigint NOT NULL AUTO_INCREMENT,
  confirmation_user_id bigint NOT NULL,
  confirmation_type enum('account-activate','account-change-email','account-password-forgot') NOT NULL,
  confirmation_date datetime NOT NULL,
  confirmation_date_gmt datetime NOT NULL,
  confirmation_token_hash varchar(255) NOT NULL,
  confirmation_status enum('active','valid','invalid') NOT NULL,
  confirmation_extra mediumtext,
  PRIMARY KEY (confirmation_id)
);

-- ---------------------------------------------------------------------------
-- chv_two_factors：两步验证（TOTP 密钥）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_two_factors (
  two_factor_id bigint NOT NULL AUTO_INCREMENT,
  two_factor_user_id bigint DEFAULT NULL,
  two_factor_date_gmt datetime NOT NULL,
  two_factor_secret mediumtext NOT NULL,
  PRIMARY KEY (two_factor_id),
  KEY two_factor_user_id (two_factor_user_id),
  KEY two_factor_date_gmt (two_factor_date_gmt)
);

-- ---------------------------------------------------------------------------
-- chv_requests：请求日志 = 防刷限流基础（登录/注册失败计数等）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_requests (
  request_id bigint NOT NULL AUTO_INCREMENT,
  request_type enum('upload','signup','account-edit','account-password-forgot','account-password-reset','account-resend-activation','account-email-needed','account-change-email','account-activate','login','content-password','account-two-factor') NOT NULL,
  request_user_id bigint DEFAULT NULL,
  request_content_id bigint DEFAULT NULL,
  request_ip varchar(255) NOT NULL,
  request_date datetime NOT NULL,
  request_date_gmt datetime NOT NULL,
  request_result enum('success','fail') NOT NULL,
  PRIMARY KEY (request_id),
  KEY request_type (request_type),
  KEY request_user_id (request_user_id),
  KEY request_content_id (request_content_id),
  KEY request_ip (request_ip),
  KEY request_date_gmt (request_date_gmt),
  KEY request_result (request_result)
);

-- ---------------------------------------------------------------------------
-- chv_locks：数据库级命名互斥锁（防定时任务并发）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_locks (
  lock_id bigint NOT NULL AUTO_INCREMENT,
  lock_name varchar(255) NOT NULL,
  lock_date_gmt datetime NOT NULL,
  lock_expires_gmt datetime DEFAULT NULL,
  PRIMARY KEY (lock_id),
  UNIQUE KEY lock_name (lock_name),
  KEY lock_date_gmt (lock_date_gmt),
  KEY lock_expires_gmt (lock_expires_gmt)
);

-- ---------------------------------------------------------------------------
-- chv_queues：失败重试队列（仅 storage-delete：远端文件删除重试）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_queues (
  queue_id bigint NOT NULL AUTO_INCREMENT,
  queue_type enum('storage-delete') NOT NULL,
  queue_date_gmt datetime NOT NULL,
  queue_args longtext NOT NULL,
  queue_join bigint NOT NULL,
  queue_attempts varchar(255) DEFAULT '0',
  queue_status enum('pending','failed') NOT NULL DEFAULT 'pending',
  PRIMARY KEY (queue_id)
);

-- ---------------------------------------------------------------------------
-- chv_stats：站点统计（total 一行 + 每天一行，仪表盘数据源）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_stats (
  stat_id bigint NOT NULL AUTO_INCREMENT,
  stat_type enum('total','date') NOT NULL,
  stat_date_gmt date DEFAULT NULL,
  stat_users bigint NOT NULL DEFAULT '0',
  stat_images bigint NOT NULL DEFAULT '0',
  stat_albums bigint NOT NULL DEFAULT '0',
  stat_image_views bigint NOT NULL DEFAULT '0',
  stat_album_views bigint NOT NULL DEFAULT '0',
  stat_image_likes bigint NOT NULL DEFAULT '0',
  stat_album_likes bigint NOT NULL DEFAULT '0',
  stat_disk_used bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (stat_id),
  UNIQUE KEY stat_date_gmt (stat_date_gmt),
  KEY stat_type (stat_type)
);

-- ---------------------------------------------------------------------------
-- chv_pages：自定义页面（服务条款 / 隐私政策 / 关于我们）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_pages (
  page_id bigint NOT NULL AUTO_INCREMENT,
  page_url_key varchar(32) DEFAULT NULL,
  page_type enum('internal','link') NOT NULL DEFAULT 'internal',
  page_file_path varchar(255) DEFAULT NULL,
  page_link_url mediumtext,
  page_icon varchar(255) DEFAULT NULL,
  page_title varchar(255) NOT NULL,
  page_description mediumtext,
  page_keywords mediumtext,
  page_is_active tinyint(1) NOT NULL DEFAULT '1',
  page_is_link_visible tinyint(1) NOT NULL DEFAULT '1',
  page_attr_target enum('_self','_blank') DEFAULT '_self',
  page_attr_rel varchar(255) DEFAULT NULL,
  page_sort_display int DEFAULT NULL,
  page_internal varchar(255) DEFAULT NULL,
  page_code mediumtext,
  PRIMARY KEY (page_id),
  UNIQUE KEY page_internal (page_internal),
  KEY page_url_key (page_url_key),
  KEY page_type (page_type),
  KEY page_is_active (page_is_active),
  KEY page_is_link_visible (page_is_link_visible),
  KEY page_sort_display (page_sort_display)
);

-- ---------------------------------------------------------------------------
-- chv_assets：静态资源资产（blob 存储）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_assets (
  asset_id bigint NOT NULL AUTO_INCREMENT,
  asset_key varchar(255) NOT NULL,
  asset_md5 varchar(32) NOT NULL,
  asset_filename varchar(255) NOT NULL,
  asset_file_path varchar(255) NOT NULL,
  asset_blob blob,
  PRIMARY KEY (asset_id),
  UNIQUE KEY asset_key (asset_key),
  KEY md5 (asset_md5),
  KEY filename (asset_filename),
  KEY file_path (asset_file_path)
);

-- ---------------------------------------------------------------------------
-- chv_imports：批量导入任务
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_imports (
  import_id bigint NOT NULL AUTO_INCREMENT,
  import_path varchar(4096) NOT NULL,
  import_options varchar(255) DEFAULT NULL,
  import_status enum('queued','working','paused','canceled','completed') NOT NULL,
  import_users bigint NOT NULL DEFAULT '0',
  import_images bigint NOT NULL DEFAULT '0',
  import_albums bigint NOT NULL DEFAULT '0',
  import_time_created datetime DEFAULT NULL,
  import_time_updated datetime DEFAULT NULL,
  import_errors tinyint(1) NOT NULL DEFAULT '0',
  import_started tinyint(1) NOT NULL DEFAULT '0',
  import_continuous tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (import_id),
  KEY import_path (import_path)
);

-- ---------------------------------------------------------------------------
-- chv_importing：导入任务的文件级明细（断点续传）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_importing (
  importing_id bigint NOT NULL AUTO_INCREMENT,
  importing_import_id bigint NOT NULL,
  importing_path varchar(4096) NOT NULL,
  importing_content_type enum('user','album','image') NOT NULL,
  importing_content_id bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (importing_id),
  UNIQUE KEY importing_path (importing_path)
);

-- ---------------------------------------------------------------------------
-- chv_login_providers：第三方登录服务商配置（client_id / secret / 启用开关）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_login_providers (
  login_provider_id bigint NOT NULL AUTO_INCREMENT,
  login_provider_name varchar(255) DEFAULT NULL,
  login_provider_label varchar(255) DEFAULT NULL,
  login_provider_key_id mediumtext,
  login_provider_key_secret mediumtext,
  login_provider_is_enabled tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (login_provider_id),
  UNIQUE KEY login_provider_name (login_provider_name),
  KEY login_provider_is_enabled (login_provider_is_enabled)
);

-- ---------------------------------------------------------------------------
-- chv_login_connections：用户与第三方账号绑定（OAuth token 加密存储）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_login_connections (
  login_connection_id bigint NOT NULL AUTO_INCREMENT,
  login_connection_user_id bigint NOT NULL,
  login_connection_provider_id bigint NOT NULL,
  login_connection_date_gmt datetime NOT NULL,
  login_connection_resource_id varchar(255) NOT NULL,
  login_connection_resource_name mediumtext,
  login_connection_token mediumtext NOT NULL COMMENT 'Ciphertext',
  PRIMARY KEY (login_connection_id),
  UNIQUE KEY login_connection_unique (login_connection_user_id,login_connection_provider_id),
  KEY login_connection_user_id (login_connection_user_id),
  KEY login_connection_date_gmt (login_connection_date_gmt),
  KEY login_connection_provider_id (login_connection_provider_id)
);

-- ---------------------------------------------------------------------------
-- chv_login_cookies：「记住我」Cookie 哈希登记
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_login_cookies (
  login_cookie_id bigint NOT NULL AUTO_INCREMENT,
  login_cookie_user_id bigint NOT NULL,
  login_cookie_connection_id bigint DEFAULT '0',
  login_cookie_date_gmt datetime NOT NULL,
  login_cookie_ip varchar(255) DEFAULT NULL,
  login_cookie_user_agent mediumtext NOT NULL,
  login_cookie_hash mediumtext NOT NULL,
  PRIMARY KEY (login_cookie_id),
  UNIQUE KEY login_cookie_unique (login_cookie_user_id,login_cookie_connection_id,login_cookie_date_gmt),
  KEY login_cookie_user_id_date_gmt (login_cookie_user_id,login_cookie_date_gmt),
  KEY login_cookie_user_id (login_cookie_user_id),
  KEY login_cookie_ip (login_cookie_ip),
  KEY login_cookie_connection_id (login_cookie_connection_id)
);

-- ---------------------------------------------------------------------------
-- chv_logins：登录历史 / 会话审计
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_logins (
  login_id bigint NOT NULL AUTO_INCREMENT,
  login_user_id bigint NOT NULL,
  login_type enum('password','session','cookie','facebook','twitter','google','vk','cookie_facebook','cookie_twitter','cookie_google','cookie_vk') NOT NULL,
  login_ip varchar(255) DEFAULT NULL,
  login_hostname mediumtext,
  login_date datetime NOT NULL,
  login_date_gmt datetime NOT NULL,
  login_resource_id varchar(255) DEFAULT NULL,
  login_resource_name mediumtext,
  login_resource_avatar mediumtext,
  login_resource_url mediumtext,
  login_secret mediumtext COMMENT 'The secret part',
  login_token_hash mediumtext COMMENT 'Hashed complement to secret if needed',
  PRIMARY KEY (login_id),
  KEY login_user_id (login_user_id)
);

-- ---------------------------------------------------------------------------
-- chv_settings：站点设置（KV，setting_typeset 区分 bool/string）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_settings (
  setting_id int NOT NULL AUTO_INCREMENT,
  setting_name varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  setting_value mediumtext,
  setting_default mediumtext,
  setting_typeset enum('string','bool') DEFAULT 'string',
  PRIMARY KEY (setting_id),
  KEY setting_name (setting_name)
);

-- ---------------------------------------------------------------------------
-- chv_storage_apis：外部存储驱动定义（local/s3/oss/cos/ftp/sftp...）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_storage_apis (
  storage_api_id bigint NOT NULL AUTO_INCREMENT,
  storage_api_name varchar(255) NOT NULL,
  storage_api_type varchar(255) NOT NULL,
  PRIMARY KEY (storage_api_id)
);

-- ---------------------------------------------------------------------------
-- chv_storages：外部存储实例（凭证、容量、启用状态）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_storages (
  storage_id bigint NOT NULL AUTO_INCREMENT,
  storage_api_id bigint NOT NULL,
  storage_name varchar(255) NOT NULL,
  storage_service varchar(255) DEFAULT NULL,
  storage_url varchar(255) NOT NULL,
  storage_bucket varchar(255) DEFAULT NULL,
  storage_region varchar(255) DEFAULT NULL,
  storage_server varchar(255) DEFAULT NULL,
  storage_account_id varchar(255) DEFAULT NULL,
  storage_account_name varchar(255) DEFAULT NULL,
  storage_key mediumtext,
  storage_secret mediumtext,
  storage_is_https tinyint(1) NOT NULL DEFAULT '0',
  storage_is_active tinyint(1) NOT NULL DEFAULT '0',
  storage_capacity bigint DEFAULT NULL,
  storage_space_used bigint DEFAULT '0',
  PRIMARY KEY (storage_id),
  KEY storage_api_id (storage_api_id),
  KEY storage_is_active (storage_is_active)
);

-- ---------------------------------------------------------------------------
-- chv_ip_bans：IP 封禁
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_ip_bans (
  ip_ban_id bigint NOT NULL AUTO_INCREMENT,
  ip_ban_date datetime NOT NULL,
  ip_ban_date_gmt datetime NOT NULL,
  ip_ban_expires datetime DEFAULT NULL,
  ip_ban_expires_gmt datetime DEFAULT NULL,
  ip_ban_ip varchar(255) NOT NULL,
  ip_ban_message text,
  PRIMARY KEY (ip_ban_id),
  UNIQUE KEY ip_ban_ip (ip_ban_ip),
  KEY ip_ban_date_gmt (ip_ban_date_gmt)
);

-- ---------------------------------------------------------------------------
-- chv_api_keys：API 密钥（bcrypt 哈希，明文只在创建时返回一次）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chv_api_keys (
  api_key_id bigint NOT NULL AUTO_INCREMENT,
  api_key_user_id bigint DEFAULT NULL,
  api_key_name varchar(100) DEFAULT NULL,
  api_key_date_gmt datetime NOT NULL,
  api_key_hash mediumtext NOT NULL,
  PRIMARY KEY (api_key_id),
  KEY api_key_user_id (api_key_user_id),
  KEY api_key_name (api_key_name),
  KEY api_key_date_gmt (api_key_date_gmt)
);
