package cn.anayoo.sweetpotato.util;

import cn.anayoo.sweetpotato.model.Query;
import cn.anayoo.sweetpotato.model.QueryForm;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;

public class QueryUtil {

    /**
     * 将框架自动拆分的queryparam格式化为List<Query>
     * @param map       queryparam
     * @param filter    过滤项
     * @return
     */
    public static QueryForm formatQuery(MultivaluedMap<String, String> map, List<String> filter) {
        var res = new ArrayList<Query>();
        var queryForm = new QueryForm();
        map.keySet().forEach(key -> {
            for (String value : map.get(key)) {
                // 排除掉 key 或 key= 这种无value形式的查询
                if (value.equals("") && key.indexOf('<') == -1 && key.indexOf('>') == -1) continue;
                // 根据有无value还原原始查询语句
                var query = value.equals("") ? key : key + "=" + value;

                var res1 = new ArrayList<Query>();
                // 先判断或的关系
                if (query.contains("|")) {
                    var arg = query.split("[|]");
                    for (int i = 0; i < arg.length; i ++) {
                        // 排除掉 key 或 key= 这种无value形式的查询
                        if (arg[i].indexOf('=') == -1 && arg[i].indexOf('<') == -1 && arg[i].indexOf('>') == -1) continue;
                        // 判断query是否符合表的字段过滤条件
                        var k = "";
                        if (arg[i].indexOf('<') != -1) k = arg[i].substring(0, arg[i].indexOf('<'));
                        if ("".equals(k) && arg[i].indexOf('>') != -1) k = arg[i].substring(0, arg[i].indexOf('>'));
                        if ("".equals(k) && arg[i].indexOf('!') != -1) k = arg[i].substring(0, arg[i].indexOf('!'));
                        if ("".equals(k) && arg[i].indexOf('=') != -1) k = arg[i].substring(0, arg[i].indexOf('='));
                        if (!filter.contains(k)) continue;
                        // 如果第二个字符为=，则截2个字符当作计算符，否则截1个字符
                        var calc = arg[i].substring(k.length() + 1, k.length() + 2).equals("=") ? arg[i].substring(k.length(), k.length() + 2) : arg[i].substring(k.length(), k.length() + 1);
                        var v = arg[i].substring(k.length() + calc.length());
                        // 如果value中包含,，则认为列表查询
                        if (v.indexOf(',') != -1) {
                            if (calc.equals("=")) calc = "in";
                            else if (calc.equals("!=")) calc = "not in";
                            else continue;
                            var sql = k + " " + calc + " (";
                            var values = new ArrayList<String>();
                            var vs = v.split(",");
                            for (int j = 0; j < vs.length; j ++) {
                                sql = sql + "?";
                                values.add(vs[j]);
                                if (j < vs.length - 1) sql = sql + ", ";
                                else sql = sql + ")";
                            }
                            res1.add(new Query(sql, values, "or"));
                        }
                        // 如果value中包含%，则认为模糊查询
                        else if (v.indexOf('%') != -1) {
                            var sql = "";
                            if (calc.equals("=")) sql = k +  " like ?";
                            else if (calc.equals("!=")) sql = k + " not like ?";
                            else continue;
                            res1.add(new Query(sql, List.of(v), "or"));
                        }
                        // 其它情况的查询
                        else {
                            var sql = k + " " + calc + " ?";
                            res1.add(new Query(sql, List.of(v), "or"));
                        }
                    }
                    // 遍历res1, 将最后一个的connector改为and并添加到res中
                    for (int i = 0; i < res1.size(); i ++) {
                        if (i == res1.size() - 1) {
                            var q = res1.get(i);
                            q.setConnecter("and");
                            res.add(q);
                        } else res.add(res1.get(i));
                    }
                } else {
                    // 非或关系的查询
                    // 判断query是否符合表的字段过滤条件
                    var k = "";
                    if (query.indexOf('<') != -1) k = query.substring(0, query.indexOf('<'));
                    if ("".equals(k) && query.indexOf('>') != -1) k = query.substring(0, query.indexOf('>'));
                    if ("".equals(k) && query.indexOf('!') != -1) k = query.substring(0, query.indexOf('!'));
                    if ("".equals(k) && query.indexOf('=') != -1) k = query.substring(0, query.indexOf('='));
                    if (query.startsWith("page=") && canParseInt(query.substring(5))) queryForm.setPage(query.substring(5));
                    if (query.startsWith("pageSize=") && canParseInt(query.substring(9))) queryForm.setPageSize(query.substring(9));
                    if (query.startsWith("order=")) {
                        var orders = query.substring(6).split(",");
                        var newOrders = new ArrayList<String>();
                        for (String ord : orders) {
                            if (filter.contains(ord)) newOrders.add(ord);
                        }
                        var newOrdersArray = new String[newOrders.size()];
                        newOrders.toArray(newOrdersArray);
                        queryForm.setOrder(newOrdersArray);
                    }
                    if (query.startsWith("orderType=")) {
                        var orderTypes = query.substring(10).split(",");
                        var newOrderTypes = new String[orderTypes.length];
                        for (int i = 0; i < orderTypes.length; i ++) {
                            newOrderTypes[i] = orderTypes[i].trim().equals("desc") ? "desc" : "asc";
                        }
                        queryForm.setOrderType(newOrderTypes);
                    }
                    if (query.startsWith("count=") && (query.substring(6).equals("true") || query.substring(6).equals("false"))) queryForm.setCount(query.substring(6));

                    if (!filter.contains(k)) continue;
                    // 如果第二个字符为=，则截2个字符当作计算符，否则截1个字符
                    if (query.length() == k.length() + 1) continue;
                    var calc = query.substring(k.length() + 1, k.length() + 2).equals("=") ? query.substring(k.length(), k.length() + 2) : query.substring(k.length(), k.length() + 1);
                    var v = query.substring(k.length() + calc.length());
                    // 如果value中包含,，则认为列表查询
                    if (v.indexOf(',') != -1) {
                        if (calc.equals("=")) calc = "in";
                        else if (calc.equals("!=")) calc = "not in";
                        else continue;
                        var sql = k + " " + calc + " (";
                        var values = new ArrayList<String>();
                        var vs = v.split(",");
                        for (int j = 0; j < vs.length; j ++) {
                            sql = sql + "?";
                            values.add(vs[j]);
                            if (j < vs.length - 1) sql = sql + ", ";
                            else sql = sql + ")";
                        }
                        res.add(new Query(sql, values, "and"));
                    }
                    // 如果value中包含%，则认为模糊查询
                    else if (v.indexOf('%') != -1) {
                        var sql = "";
                        if (calc.equals("=")) sql = k +  " like ?";
                        else if (calc.equals("!=")) sql = k + " not like ?";
                        else continue;
                        res.add(new Query(sql, List.of(v), "and"));
                    }
                    // 其它情况的查询
                    else {
                        var sql = k + " " + calc + " ?";
                        res.add(new Query(sql, List.of(v), "and"));
                    }
                }
            }
        });
        queryForm.setQueryList(res);
        return queryForm;
    }

    private static boolean canParseInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
