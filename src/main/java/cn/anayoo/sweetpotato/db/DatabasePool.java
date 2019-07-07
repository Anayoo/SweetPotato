package cn.anayoo.sweetpotato.db;

import cn.anayoo.sweetpotato.XmlLoader;
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
     * @param datasource  数据源名称
     * @return      Connection
     */
    public Connection getConn(String datasource) throws SQLException {
        if (dataSourceHashtable.keySet().contains(datasource))
            return dataSourceHashtable.get(datasource).getConnection();
        else
            throw new SQLException("没找到数据源:" + datasource);
    }

    /**
     * 获取数据库类型
     * @param datasource  数据源名称
     * @return      Type
     */
    public String getDatasourceType(String datasource) throws SQLException {
        if (dataSourceHashtable.keySet().contains(datasource)) {
            var driverClassName = dataSourceHashtable.get(datasource).getDriverClassName();
            switch (driverClassName) {
                case "oracle.jdbc.driver.OracleDriver" : return "oracle";
                case "com.mysql.jdbc.Driver" : return "mysql";
                case "com.microsoft.jdbc.sqlserver.SQLServerDriver" : return "sqlserver";
                case "com.ibm.db2.jdbc.app.DB2Driver" : return "db2";
                default: throw new SQLException("未识别的数据库:" + driverClassName);
            }

        } else
            throw new SQLException("没找到数据源:" + datasource);
    }
}
