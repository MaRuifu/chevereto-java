package com.chevereto.java.service;

import java.util.Locale;
import java.util.Map;

/**
 * JdbcTemplate 查询结果取值的小工具。
 *
 * <p>存在的原因：H2 会把裸列别名转成大写返回（{@code w_day} → {@code W_DAY}），
 * 而 MySQL 保留原样（{@code w_day}）。同一段代码要同时跑两种库，就需要大小写兼容的取值。
 * 这里不做完整的元数据嗅探，只做「原样 → 大写」两级回退，覆盖本项目所有场景。
 */
final class ColumnValues {

    private ColumnValues() {
    }

    static long asLong(Map<String, Object> row, String column) {
        if (row == null) {
            return 0L;
        }
        Object v = row.get(column);
        if (v == null) {
            v = row.get(column.toUpperCase(Locale.ROOT));
        }
        return v instanceof Number n ? n.longValue() : 0L;
    }

    static Object get(Map<String, Object> row, String column) {
        if (row == null) {
            return null;
        }
        Object v = row.get(column);
        return v != null ? v : row.get(column.toUpperCase(Locale.ROOT));
    }
}
