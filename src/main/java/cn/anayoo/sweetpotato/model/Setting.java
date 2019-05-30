package cn.anayoo.sweetpotato.model;

/**
 * 页面设定，在分页查询中返回
 */
public class Setting {

    private String order;
    private String orderType;
    private int page;
    private int pageSize;
    // 数据总条数
    private Integer count;

    public Setting() {
    }

    public Setting(String order, String orderType, int page, int pageSize, Integer count) {
        this.order = order;
        this.orderType = orderType;
        this.page = page;
        this.pageSize = pageSize;
        this.count = count;
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

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
