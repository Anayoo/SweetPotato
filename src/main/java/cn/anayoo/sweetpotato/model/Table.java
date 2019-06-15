package cn.anayoo.sweetpotato.model;

import java.util.List;

public class Table {

    private String name;
    private String datasource;
    private String value;
    private String url;
    private String gets;
    private String key;
    private int pageSize;
    private String order;
    private String orderType;
    private List<Field> fields;

    public Table() {
    }

    public Table(String name, String datasource, String value, String url, String gets, String key, int pageSize, String order, String orderType, List<Field> fields) {
        this.name = name;
        this.datasource = datasource;
        this.value = value;
        this.url = url;
        this.gets = gets;
        this.key = key;
        this.pageSize = pageSize;
        this.order = order;
        this.orderType = orderType;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDatasource() {
        return datasource;
    }

    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getGets() {
        return gets;
    }

    public void setGets(String gets) {
        this.gets = gets;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    @Override
    public String toString() {
        return "{" +
                "\"name\":\"" + this.name + "\", " +
                "\"datasource\":\"" + this.datasource + "\", " +
                "\"value\":\"" + this.value + "\", " +
                "\"url\":\"" + this.url + "\", " +
                "\"gets\":\"" + this.gets + "\", " +
                "\"key\":\"" + this.key + "\", " +
                "\"pageSize\":" + this.pageSize + ", " +
                "\"order\":\"" + this.order + "\", " +
                "\"orderType\":\"" + this.orderType + "\", " +
                "\"fields\":" + fields.toString() +
                "}";
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
