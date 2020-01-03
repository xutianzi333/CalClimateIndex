package Util;

/**
 * 连接池接口类
 * <p>
 * 对外提供数据库连接池的基本服务，比如得到一个数据库操作管道。
 */
public interface IAccessPool {
    // 获取一个连接
    AccessPooledConnection getAccessPooledConnection();

    // 新建指定数目的连接数
    void createAccessPooledConnection(int count);
}
