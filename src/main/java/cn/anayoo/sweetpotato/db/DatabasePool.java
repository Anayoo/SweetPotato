package cn.anayoo.sweetPotato.db;

import cn.anayoo.sweetPotato.XmlLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by anayoo on 19-05-14
 * @author anayoo
 */
public class DatabasePool {
    private static final Logger logger = Logger.getLogger(DatabasePool.class.getName());

    private static volatile DatabasePool instance;

    private static Hashtable<String, HikariDataSource> dataSourceHashtable = new Hashtable<>();

    private DatabasePool(Hashtable<String, HikariConfig> hikariConfigs) {
        logger.log(Level.INFO, "数据库资源池初始化...");
        hikariConfigs.forEach((name, config) -> dataSourceHashtable.put(name, new HikariDataSource(config)));
        logger.log(Level.INFO, "数据库资源池: ok.");
    }

    public static DatabasePool getInstance(XmlLoader xmlLoader) {
        if (instance == null) {
            synchronized (DatabasePool.class) {
                if (instance == null) {
                    instance = new DatabasePool(xmlLoader.getHikariConfigs());
                }
            }
        }
        return instance;
    }

    public static DatabasePool getInstance() {
        return instance;
    }

    /**
     * 获取数据库连接
     * @param name  数据源名称
     * @return      Connection
     * @throws SQLException
     */
    public Connection getConn(String name) throws SQLException {
        if (dataSourceHashtable.keySet().contains(name))
            return dataSourceHashtable.get(name).getConnection();
        else
            throw new SQLException("没找到数据源:" + name);
    }
}
