package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.db.DatabasePool;
import cn.anayoo.sweetpotato.model.Field;
import cn.anayoo.sweetpotato.model.Table;
import com.zaxxer.hikari.HikariConfig;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;

/**
 * 读取并解析XML配置文件
 * 按Java11特性更新了代码...
 * Created by anayoo on 19-05-13
 * @author anayoo
 */
public class XmlLoader {
    private Logger logger = LoggerFactory.getLogger(XmlLoader.class);

    private Hashtable<String, HikariConfig> hikariConfigs = new Hashtable<>();
    private Hashtable<String, Table> tables = new Hashtable<>();
    private String modelPackage = "cn.anayoo.sweetpotato.run.model";
    private String servicePackage = "cn.anayoo.sweetpotato.run.service";
    private DatabasePool pool = null;

    private int defaultPageSize = 10;
    // 默认连接超时时间1s
    private int defaultTimeout = 1000;

    synchronized XmlLoader read(String src) throws DocumentException {
        logger.debug("加载配置文件: {}", src);
        var baseUrl = this.getClass().getClassLoader().getResource("").toString();
        var configUrl = baseUrl + src;
        var doc = new SAXReader().read(configUrl);
        var root =  doc.getRootElement();
        // 读全局配置
        var config = root.element("config");
        defaultPageSize = config == null ? defaultPageSize : config.elementText("pageSize") == null && !this.canParseInt(config.elementText("pageSize")) ? defaultPageSize : Integer.parseInt(config.elementText("pageSize"));
        modelPackage = config == null ? modelPackage : config.elementText("modelPackage") == null ? modelPackage : config.elementText("modelPackage");
        servicePackage = config == null ? servicePackage : config.elementText("servicePackage") == null ? servicePackage : config.elementText("servicePackage");

        // 读数据源
        root.elementIterator("datasource").forEachRemaining(datasource -> {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(datasource.elementText("url"));
            hikariConfig.setUsername(datasource.elementText("username"));
            hikariConfig.setPassword(datasource.elementText("password"));
            hikariConfig.setConnectionTimeout(datasource.elementText("connectionTimeout") == null && !this.canParseInt(datasource.elementText("connectionTimeout")) ? this.defaultTimeout : Integer.parseInt(datasource.elementText("connectionTimeout")));
            // 写死了，目前仅支持mysql。
            hikariConfig.setDriverClassName("com.mysql.jdbc.Driver");
            hikariConfigs.put(datasource.attributeValue("name"), hikariConfig);
        });

        // 读表结构
        root.elementIterator("table").forEachRemaining(table -> {
            var name = table.attributeValue("name");
            var datasource = table.attributeValue("datasource");
            var value = table.attributeValue("value");
            var autoBuild = table.attributeValue("autoBuild") == null || table.attributeValue("autoBuild").toLowerCase().equals("true");
            var url = table.attributeValue("url");
            // gets值可以不定义，不定义时为url值加's'
            var gets = table.attributeValue("gets") == null ? url + "s" : table.attributeValue("gets");
            var pageSize = table.attributeValue("pageSize") == null ? defaultPageSize : Integer.parseInt(table.attributeValue("pageSize"));
            // order值可以不定义，不定义时为第一个field
            var order = table.attributeValue("order") == null ? (autoBuild ? "" : table.element("field").attributeValue("value")) : table.attributeValue("order");
            var orderType = table.attributeValue("orderType") == null ? "asc" : table.attributeValue("orderType");
            // key值可以不定义，不定义时为第一个field
            var key = table.attributeValue("key") == null ? (autoBuild ? "" : table.element("field").attributeValue("value")) : table.attributeValue("key");

            var dbFields = queryTableFields(datasource, value);
            var fields = new ArrayList<Field>();

            // 用配置文件中定义的内容覆盖从数据库检索到的表结构
            table.elementIterator("field").forEachRemaining(field -> {
                var v = field.attributeValue("value");
                var dbField = dbFields.get(v);
                var t = field.attributeValue("type") == null ? dbField.getType() : field.attributeValue("type").toLowerCase();
                // regex值可以不声明
                var r = field.attributeValue("regex") == null ? dbField.getRegex() : field.attributeValue("regex");
                var pk = field.attributeValue("isPrimaryKey") == null ? dbField.isPrimaryKey() : !field.attributeValue("isPrimaryKey").toLowerCase().equals("true");
                // 默认值true
                var an = field.attributeValue("allowNone") == null ? dbField.isAllowNone() : field.attributeValue("allowNone").toLowerCase().equals("true");
                var ar = field.attributeValue("allowRepeat") == null ? dbField.isAllowRepeat() : field.attributeValue("allowRepeat").toLowerCase().equals("true");
                fields.add(new Field(v, t, r, pk, ar, an));
                dbFields.remove(v);
            });
            if (autoBuild) {
                fields.addAll(dbFields.values());
                Collections.sort(fields);
                // 如果order和key不主动定义，取第一个field
                key = key.equals("") ? fields.get(0).getValue() : key;
                order = order.equals("") ? fields.get(0).getValue() : order;
            }
            this.tables.put(name, new Table(name, datasource, value, url, gets, key, pageSize, order, orderType, fields));
        });
        return this;
    }

    /**
     * 尝试性转换，用以保持函数式代码风格
     * @param str
     * @return
     */
    private boolean canParseInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private HashMap<String, Field> queryTableFields(String datasource, String table) {
        if (pool == null) {
            pool = DatabasePool.getInstance(this);
        }
        var fields = new HashMap<String, Field>();
        try {
            var conn = pool.getConn(datasource);
            var type = pool.getDatasourceType(datasource);
            switch (type) {
                case "mysql" :
                    var stmt = conn.createStatement();
                    var sql = "desc " + table + ";";
                    var rs = stmt.executeQuery(sql);
                    while (rs.next()) {
                        var v = rs.getString("Field");
                        var t = rs.getString("Type");
                        var allowNone = rs.getString("Null").equals("YES");
                        var pk = rs.getString("Key").equals("PRI");
                        if (t.startsWith("int") || t.startsWith("bigint") || t.startsWith("decimal") || t.startsWith("double") || t.startsWith("integer") || t.startsWith("mediumint") || t.startsWith("multipoint") || t.startsWith("smallint") || t.startsWith("tinyint"))
                            t = "number";
                        else t = "string";
                        fields.put(v, new Field(v, t, "", pk, true, allowNone));
                    }
            }
            conn.close();
            return fields;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Hashtable<String, HikariConfig> getHikariConfigs() {
        return hikariConfigs;
    }

    public Hashtable<String, Table> getTables() {
        return tables;
    }

    public String getModelPackage() {
        return modelPackage;
    }

    public void setModelPackage(String modelPackage) {
        this.modelPackage = modelPackage;
    }

    public String getServicePackage() {
        return servicePackage;
    }

    public void setServicePackage(String servicePackage) {
        this.servicePackage = servicePackage;
    }
}
