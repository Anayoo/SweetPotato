package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.model.Field;
import cn.anayoo.sweetpotato.model.Table;
import com.zaxxer.hikari.HikariConfig;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

    private int defaultPageSize = 10;
    // 默认连接超时时间1s
    private int defaultTimeout = 1000;

    synchronized XmlLoader read(String src) throws DocumentException {
        var s = src.startsWith("/") ? src : Objects.requireNonNull(this.getClass().getClassLoader().getResource("")).getPath() + src;
        logger.debug("加载配置文件: {}", s);
        var doc = new SAXReader().read(new File(s));
        var root =  doc.getRootElement();
        // 读全局配置
        var config = root.element("config");
        defaultPageSize = config == null ? defaultPageSize : config.elementText("pageSize") == null && !this.canPraseInt(config.elementText("pageSize")) ? defaultPageSize : Integer.parseInt(config.elementText("pageSize"));
        modelPackage = config == null ? modelPackage : config.elementText("modelPackage") == null ? modelPackage : config.elementText("modelPackage");
        servicePackage = config == null ? servicePackage : config.elementText("servicePackage") == null ? servicePackage : config.elementText("servicePackage");

        // 读数据源
        root.elementIterator("datasource").forEachRemaining(datasource -> {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(datasource.elementText("url"));
            hikariConfig.setUsername(datasource.elementText("username"));
            hikariConfig.setPassword(datasource.elementText("password"));
            hikariConfig.setConnectionTimeout(datasource.elementText("connectionTimeout") == null && !this.canPraseInt(datasource.elementText("connectionTimeout")) ? this.defaultTimeout : Integer.parseInt(datasource.elementText("connectionTimeout")));
            // 写死了，目前仅支持mysql。
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
        return this;
    }

    /**
     * 尝试性转换，用以保持函数式代码风格
     * @param str
     * @return
     */
    private boolean canPraseInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (Exception e) {
            return false;
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
