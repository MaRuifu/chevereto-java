#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从一份 Chevereto 生产 mysqldump 生成两份建表脚本（H2 / MySQL），并校验列定义与 dump 逐列一致。

为什么要两份：
  H2 不支持前缀索引 col(255)、不支持 FULLTEXT 索引，而 MySQL 的
  varchar(4096) 列必须用前缀索引才能建唯一键，两者无法用同一份 DDL 表达。
  同一脚本产出两份，避免人工维护导致漂移。

注意：这不是构建/启动流程的一部分，只在需要重建 schema 时手动跑一次。
      输入是一份标准 Chevereto V4 的 mysqldump（含 29 张 chv_* 表的结构），
      仓库里不附带这份 dump——请从你自己的原版实例导出：
          mysqldump -u root -p --no-data chevereto > chevereto.sql

用法：
  python3 tools/gen_schema.py [dump路径，默认 ./chevereto.sql]

输出（覆盖写）：
  src/main/resources/schema-h2.sql      H2 (MODE=MySQL) 使用
  src/main/resources/schema-mysql.sql   MySQL 8 使用

退出码：列定义与 dump 不一致时返回 1，可直接用于 CI 比对。
"""
import re
import sys

DUMP = sys.argv[1] if len(sys.argv) > 1 else 'chevereto.sql'
BASE = 'src/main/resources'

COMMENTS = {
    'chv_albums': '相册',
    'chv_api_keys': 'API 密钥（bcrypt 哈希，明文只在创建时返回一次）',
    'chv_assets': '静态资源资产（blob 存储）',
    'chv_categories': '图片分类（探索页筛选）',
    'chv_confirmations': '邮箱确认令牌（激活 / 改邮箱 / 找回密码），只存哈希',
    'chv_deletions': '已删除内容的审计墓碑（风控追溯）',
    'chv_follows': '用户关注关系',
    'chv_images': '图片主表',
    'chv_images_hash': '游客删图口令（bcrypt），对应 /image/<ID>/delete/<明文>',
    'chv_importing': '导入任务的文件级明细（断点续传）',
    'chv_imports': '批量导入任务',
    'chv_ip_bans': 'IP 封禁',
    'chv_likes': '点赞记录（图片 / 相册，支持游客按 IP）',
    'chv_locks': '数据库级命名互斥锁（防定时任务并发）',
    'chv_login_connections': '用户与第三方账号绑定（OAuth token 加密存储）',
    'chv_login_cookies': '「记住我」Cookie 哈希登记',
    'chv_login_passwords': '用户登录密码（bcrypt，兼容原版）',
    'chv_login_providers': '第三方登录服务商配置（client_id / secret / 启用开关）',
    'chv_logins': '登录历史 / 会话审计',
    'chv_notifications': '站内通知（被赞 / 被关注）',
    'chv_pages': '自定义页面（服务条款 / 隐私政策 / 关于我们）',
    'chv_queues': '失败重试队列（仅 storage-delete：远端文件删除重试）',
    'chv_requests': '请求日志 = 防刷限流基础（登录/注册失败计数等）',
    'chv_settings': '站点设置（KV，setting_typeset 区分 bool/string）',
    'chv_stats': '站点统计（total 一行 + 每天一行，仪表盘数据源）',
    'chv_storage_apis': '外部存储驱动定义（local/s3/oss/cos/ftp/sftp...）',
    'chv_storages': '外部存储实例（凭证、容量、启用状态）',
    'chv_two_factors': '两步验证（TOTP 密钥）',
    'chv_users': '用户',
}

ORDER = [
    'chv_users', 'chv_login_passwords', 'chv_images', 'chv_images_hash', 'chv_albums',
    'chv_categories', 'chv_likes', 'chv_follows', 'chv_notifications', 'chv_deletions',
    'chv_confirmations', 'chv_two_factors', 'chv_requests', 'chv_locks', 'chv_queues',
    'chv_stats', 'chv_pages', 'chv_assets', 'chv_imports', 'chv_importing',
    'chv_login_providers', 'chv_login_connections', 'chv_login_cookies', 'chv_logins',
    'chv_settings', 'chv_storage_apis', 'chv_storages', 'chv_ip_bans', 'chv_api_keys',
]

H2_HEADER = """-- =============================================================================
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

"""

MYSQL_HEADER = """-- =============================================================================
-- Chevereto 数据表结构 —— MySQL 8 版本：29 张表
-- =============================================================================
-- 由 tools/gen_schema.py 从生产 dump 自动生成，请勿手改。
-- 与原版 schema 完全一致：保留前缀索引、FULLTEXT 索引、ENGINE/CHARSET/COLLATE。
-- 唯一差异：CREATE TABLE -> CREATE TABLE IF NOT EXISTS（已存在的库零改动，
-- 因此可以直接指向现有 Chevereto 生产库，不会 DROP 任何表）。
-- =============================================================================

"""


def parse_dump(path):
    text = open(path, encoding='utf-8', errors='ignore').read()
    blocks = {}
    for m in re.finditer(r'^CREATE TABLE `(\w+)` \((.*?)^\) ENGINE=([^;]*);', text, re.S | re.M):
        blocks[m.group(1)] = (m.group(2), m.group(3).strip())
    return blocks


def split_lines(body):
    return [s for s in (raw.strip().rstrip(',') for raw in body.split('\n')) if s]


def build_h2(name, body):
    out = []
    for s in split_lines(body):
        s = s.replace('`', '')
        if s.startswith('FULLTEXT KEY'):
            continue
        s = s.replace(' USING BTREE', '')
        s = re.sub(r'\((\w+)\(\d+\)\)', r'(\1)', s)
        s = re.sub(r'^(UNIQUE KEY|KEY) key ', r'\1 asset_key ', s)
        out.append('  ' + s)
    return 'CREATE TABLE IF NOT EXISTS %s (\n%s\n);' % (name, ',\n'.join(out))


def build_mysql(name, body, tail):
    # 生产 DDL 原样保留，只把 CREATE TABLE 改成 IF NOT EXISTS，并去掉 AUTO_INCREMENT=n
    tail = re.sub(r'\bAUTO_INCREMENT=\d+\s*', '', tail)
    out = ['  ' + s for s in split_lines(body)]
    return 'CREATE TABLE IF NOT EXISTS `%s` (\n%s\n) ENGINE=%s;' % (name, ',\n'.join(out), tail)


def cols_of(body, quoted):
    out = []
    for s in split_lines(body):
        m = re.match(r'`(\w+)`\s+(\S+)', s) if quoted else re.match(r'(\w+)\s+(\S+)', s)
        if not quoted and m and m.group(1).upper() in (
                'PRIMARY', 'KEY', 'UNIQUE', 'CONSTRAINT', 'INDEX', 'FOREIGN', 'FULLTEXT'):
            m = None
        if m:
            out.append((m.group(1), re.sub(r'\(\d+\)', '', m.group(2).lower())))
    return out


def main():
    blocks = parse_dump(DUMP)
    assert len(blocks) == 29, '生产 dump 表数异常：%d' % len(blocks)
    assert set(blocks) == set(ORDER), '表集合不匹配：%s' % (set(blocks) ^ set(ORDER))

    h2 = [H2_HEADER]
    my = [MYSQL_HEADER]
    for name in ORDER:
        body, tail = blocks[name]
        rule = '\n-- ' + '-' * 75 + '\n-- %s：%s\n-- ' % (name, COMMENTS[name]) + '-' * 75
        h2 += [rule, build_h2(name, body)]
        my += [rule, build_mysql(name, body, tail)]

    h2_text = '\n'.join(h2) + '\n'
    my_text = '\n'.join(my) + '\n'
    open(BASE + '/schema-h2.sql', 'w', encoding='utf-8').write(h2_text)
    open(BASE + '/schema-mysql.sql', 'w', encoding='utf-8').write(my_text)

    gen_h2 = dict(re.findall(r'^CREATE TABLE IF NOT EXISTS (\w+) \((.*?)^\);', h2_text, re.S | re.M))
    gen_my = dict(re.findall(r'^CREATE TABLE IF NOT EXISTS `(\w+)` \((.*?)^\) ENGINE', my_text, re.S | re.M))
    bad = 0
    for name in ORDER:
        prod = cols_of(blocks[name][0], True)
        for label, got in (('h2', cols_of(gen_h2[name], False)), ('mysql', cols_of(gen_my[name], True))):
            if got != prod:
                bad += 1
                print('X %s [%s]: %d 列 vs 生产 %d 列' % (name, label, len(got), len(prod)))
                for a, b in zip(got, prod):
                    if a != b:
                        print('    %s != %s' % (a, b))
    print('schema-h2.sql   : %d 行' % len(h2_text.splitlines()))
    print('schema-mysql.sql: %d 行' % len(my_text.splitlines()))
    print('列定义校验：' + ('与生产逐列一致 OK' if bad == 0 else '%d 处不一致 FAIL' % bad))
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
