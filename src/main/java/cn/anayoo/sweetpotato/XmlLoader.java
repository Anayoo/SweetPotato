package cn.anayoo.sweetPotato;

import com.zaxxer.hikari.HikariConfig;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * 读取并解析XML配置文件
 * 按Java11特性更新了代码, 更像python了...
 * Created by anayoo on 19-05-13
 * @author anayoo
 */
public class XmlLoader implements Cloneable {
    private Logger logger = LoggerFactory.getLogger(XmlLoader.class);

    private Hashtable<String, HikariConfig> hikariConfigs = new Hashtable<>();
    private Hashtable<String, Table> tables = new Hashtable<>();
    private String modelPackage = "cn.anayoo.sweetPotato.run.model";
    private String servicePackage = "cn.anayoo.sweetPotato.run.service";

    synchronized XmlLoader read(String src) throws DocumentException {
        var s = src.startsWith("/") ? src : Objects.requireNonNull(this.getClass().getClassLoader().getResource("")).getPath() + src;
        logger.debug("加载配置文件: {}", s);
        var doc = new SAXReader().read(new File(s));
        var root =  doc.getRootElement();
        // 读全局配置
        var config = root.element("config");
        var defaultPageSize = config == null ? 10 : config.elementText("pageSize") == null ? 10 : Integer.parseInt(config.elementText("pageSize"));
        modelPackage = config == null ? modelPackage : config.elementText("modelPackage") == null ? modelPackage : config.elementText("modelPackage");
        servicePackage = config == null ? servicePackage : config.elementText("servicePackage") == null ? servicePackage : config.elementText("servicePackage");

        // 读数据源
        root.elementIterator("datasource").forEachRemaining(datasource -> {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(datasource.elementText("url"));
            hikariConfig.setUsername(datasource.elementText("username"));
            hikariConfig.setPassword(datasource.elementText("password"));
            // 写死了，目前仅支持mysql。
            hikariConfig.setConnectionTimeout(1000);
            hikariConfig.setDriverClassName("com.mysql.jdbc.Driver");
            hikariConfigs.put(datasource.attributeValue("name"), hikariConfig);
        });

        // 读表结构
        root.elementIterator("table").forEachRemaining(table -> {
            var name = table.attributeValue("name");
            var datasource = table.attributeValue("datasource");
            var value = table.attributeValue("value");
            var url = table.attributeValue("url");
            // gets值可以不定义，不定义时为url值加's'
            var gets = table.attributeValue("gets") == null ? url + "s" : table.attributeValue("gets");
            var pageSize = table.attributeValue("pageSize") == null ? defaultPageSize : Integer.parseInt(table.attributeValue("pageSize"));
            // order值可以不定义，不定义时为第一个field
            var order = table.attributeValue("order") == null ? table.element("field").attributeValue("value") : table.attributeValue("order");
            var orderType = table.attributeValue("orderType") == null ? "asc" : table.attributeValue("orderType");
            // key值可以不定义，不定义时为第一个field
            var key = table.attributeValue("key") == null ? table.element("field").attributeValue("value") : table.attributeValue("key");
            var fields = new ArrayList<Field>();

            table.elementIterator("field").forEachRemaining(field -> {
                var n = field.attributeValue("name");
                var v = field.attributeValue("value");
                var t = field.attributeValue("type");
                // regex值可以不声明
                var r = field.attributeValue("regex") == null ? "" : field.attributeValue("regex");
                // 默认值true
                var an = field.attributeValue("allowNone") == null || field.attributeValue("allowNone").equals("false");
                var ar = field.attributeValue("allowRepeat") == null || field.attributeValue("allowRepeat").equals("false");
                // 默认值false
                var ai = field.attributeValue("allowInc") != null && field.attributeValue("allowInc").equals("true");
                fields.add(new Field(n, v, t, r, ai, ar, an));
            });
            // 如果order不声明，取第一个field
            order = order == null ? fields.get(0).getValue() : order;
            this.tables.put(name, new Table(name, datasource, value, url, gets, key, pageSize, order, orderType, fields));
        });

        try {
            return (XmlLoader) this.clone();
        } catch (CloneNotSupportedException e) {
            // XmlLoader必然可被复制
            e.printStackTrace();
        }
        return this;
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
