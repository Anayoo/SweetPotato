package cn.anayoo.sweetpotato.model;

import java.util.List;

public class Query {

    private String query;
    private List<String> values;
    private String connecter;

    public Query() {
    }

    public Query(String query, List<String> values, String connecter) {
        this.query = query;
        this.values = values;
        this.connecter = connecter;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public String getConnecter() {
        return connecter;
    }

    public void setConnecter(String connecter) {
        this.connecter = connecter;
    }

    @Override
    public String toString() {
        return "Query{" +
                "query='" + query + '\'' +
                ", values=" + values +
                ", connecter='" + connecter + '\'' +
                '}';
    }
}
