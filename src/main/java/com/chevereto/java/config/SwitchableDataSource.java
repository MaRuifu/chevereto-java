package com.chevereto.java.config;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * 可热切换的数据源代理。
 *
 * <p>用途：首次启动向导里用户可能选择 MySQL，此时连接池必须在不重启的情况下替换。
 * MyBatis 的 SqlSessionFactory 在启动时绑定的就是这个代理对象，
 * 因此只要替换内部 target，所有 Mapper 立刻改走新库，无需重启进程。
 */
public class SwitchableDataSource implements DataSource {

    private volatile DataSource target;
    private volatile DataSource previous;

    public SwitchableDataSource(DataSource target) {
        this.target = target;
    }

    /** 切换目标库；旧连接池保留引用，关闭动作由调用方在切换成功后触发 */
    public void switchTo(DataSource newTarget) {
        DataSource old = this.target;
        this.target = newTarget;
        this.previous = old;
    }

    /** 切换完成后调用，释放旧池 */
    public void closePrevious() {
        DataSource old = this.previous;
        this.previous = null;
        if (old instanceof AutoCloseable closeable && old != target) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 旧池关闭失败不影响新库可用
            }
        }
    }

    public DataSource current() {
        return target;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return target.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return target.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return target.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        target.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        target.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return target.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return target.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return target.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return target.isWrapperFor(iface);
    }
}
