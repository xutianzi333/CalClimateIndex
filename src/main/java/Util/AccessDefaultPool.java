package Util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;

/**
 * 连接池
 * 获取外部配置信息进行初始化
 * 加载数据驱动
 */
public class AccessDefaultPool implements IAccessPool {
    //AccessDefaultPool持有一个管道集合，基于多线程的考虑，这里使用了Vector。
    private Vector<AccessPooledConnection> accessPooledConnectionVector = new Vector<AccessPooledConnection>();

    // 数据库驱动
    private String jdbcDriver;

    // 数据库访问地址
    private String jdbcURL;

    // 数据库连接池连接数初始化大小
    private int initCount;

    // 连接池中连接数不足时增长的步进数
    private int step;

    // 连接池中最大连接数
    private int maxCount;

    // 构造函数：数据库连接池需要根据外部配置文件完成数据库驱动加载以及初始化管道的建立。
    public AccessDefaultPool(String jdbcDriver, String jdbcURL, int initCount, int step, int maxCount) {
        // 初始化数据库连接池配置
        this.jdbcDriver = jdbcDriver;
        this.jdbcURL = jdbcURL;
        this.initCount = initCount;
        this.step = step;
        this.maxCount = maxCount;

        // 加载数据库驱动程序
        try {
            // 加载MYSQL JDBC驱动程序
            Class.forName(this.jdbcDriver);
            System.out.println("Success loading Accesssql Driver!");
        } catch (ClassNotFoundException e) {
            System.out.print("Error loading Accesssql Driver!");
            e.printStackTrace();
        }

        // 初始化数据库连接池管道
        createAccessPooledConnection(initCount);

    }

    //如果得不到操作管道，需要去创建管道！
    @Override
    public AccessPooledConnection getAccessPooledConnection() {
        if (accessPooledConnectionVector.size() < 1) {
            throw new RuntimeException("连接池初始化参数错误");
        }

        AccessPooledConnection accessPooledConnection = null;
        try {
            accessPooledConnection = getRealConnectionFromPool();

            while (accessPooledConnection == null) {
                createAccessPooledConnection(step);
                accessPooledConnection = getRealConnectionFromPool();
                return accessPooledConnection;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return accessPooledConnection;
    }

    /**
     * 数据库连接池在创建管道时，应该去看一下是否达到上限，如果没有，则可以创建。
     * <p>
     * 不仅仅要创建出来，还要标示每一个管道的isBusy标志。
     *
     * @param count
     */
    @Override
    public void createAccessPooledConnection(int count) {
        if (accessPooledConnectionVector.size() > maxCount || accessPooledConnectionVector.size() + count > maxCount) {
            throw new RuntimeException("连接池已满");
        }

        for (int i = 0; i < count; ++i) {
            try {
                Connection connection = DriverManager.getConnection(jdbcURL);
                AccessPooledConnection accessPooledConnection = new AccessPooledConnection(connection, false);
                accessPooledConnectionVector.add(accessPooledConnection);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 第一，这里使用了synchronized，就是为了避免多线程下产生问题。
     * <p>
     * 第二，要知道Connection是有超时机制的，如果我们得到的管道的Connection已经超时了怎么办呢？
     * <p>
     * 第三，得到管道后，一定注意isBusy的设置。
     *
     * @return
     * @throws SQLException
     */
    private synchronized AccessPooledConnection getRealConnectionFromPool() throws SQLException {
        AccessPooledConnection accessPooledConnection = null;

        for (AccessPooledConnection connection : accessPooledConnectionVector) {
            if (!connection.isBusy()) {
                if (connection.getConnection().isValid(3000)) {
                    connection.setBusy(true);
                    accessPooledConnection = connection;
                } else {
                    try {
                        Connection con = DriverManager.getConnection(jdbcURL);                        connection.setConnection(con);
                        connection.setBusy(true);
                        accessPooledConnection = connection;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return accessPooledConnection;
    }
}
