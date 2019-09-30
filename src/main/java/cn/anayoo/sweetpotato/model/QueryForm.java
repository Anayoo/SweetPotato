package cn.anayoo.sweetpotato.model;

import java.util.List;

public class QueryForm {

    private String pageSize = "";
    private String page = "";
    private String orderType = "";
    private String order = "";
    private String count = "";

    private List<Query> queryList;

    public QueryForm() {
    }

    public QueryForm(String pageSize, String page, String orderType, String order, String count, List<Query> queryList) {
        this.pageSize = pageSize;
        this.page = page;
        this.orderType = orderType;
        this.order = order;
        this.count = count;
        this.queryList = queryList;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getCount() {
        return count;
    }

    public void setCount(String count) {
        this.count = count;
    }

    public List<Query> getQueryList() {
        return queryList;
    }

    public void setQueryList(List<Query> queryList) {
        this.queryList = queryList;
    }

    @Override
    public String toString() {
        return "QueryForm{" +
                "pageSize='" + pageSize + '\'' +
                ", page='" + page + '\'' +
                ", orderType='" + orderType + '\'' +
                ", order='" + order + '\'' +
                ", count='" + count + '\'' +
                ", queryList=" + queryList +
                '}';
    }
}
