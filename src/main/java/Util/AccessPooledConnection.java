package Util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class AccessPooledConnection {
    private Connection connection;

    private boolean busy;

    public AccessPooledConnection(Connection connection, boolean busy) {
        this.connection = connection;
        this.busy = busy;
    }

    public void close() {
        this.busy = false;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public ResultSet query(String sql) {
        Statement statement;
        ResultSet resultSet = null;
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultSet;
    }
}
