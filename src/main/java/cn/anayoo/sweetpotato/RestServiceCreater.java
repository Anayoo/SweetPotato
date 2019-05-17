package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.util.JavassistUtil;
import javassist.*;
import javassist.bytecode.ParameterAnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by anayoo on 19-05-13
 * @author anayoo
 */
public class RestServiceCreater {
    // 先用slf4j调试, 框架正式打包的时候改用jul
    private Logger logger = LoggerFactory.getLogger(RestServiceCreater.class);

    private ClassPool classPool = new ClassPool(true);
    private ClassLoader classLoader;
    private XmlLoader xmlLoader;

    public RestServiceCreater(XmlLoader xmlLoader) {
        this.xmlLoader = xmlLoader;
    }

    /**
     *  创建标准RESTFUL API接口
     */
    public void createRestfulApi() {
        classLoader = RestServiceCreater.class.getClassLoader();
        try {
            this.createModel();
            this.createGetter();
            this.createPutter();
            this.createPoster();
            this.createDeleter();
        } catch (CannotCompileException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据XML配置，创建对应的实体类
     */
    private void createModel() {
        logger.debug("创建实体类中...");
        xmlLoader.getTables().forEach((name, table) -> {
            // 类名首字母大写
            var fullName = xmlLoader.getModelPackage() + "." + name.substring(0, 1).toUpperCase() + name.substring(1);
            var obj = classPool.makeClass(fullName);
            // 构造方法
            var objConstructor = new CtConstructor(new CtClass[] {}, obj);
            objConstructor.setModifiers(Modifier.PUBLIC);
            try {
                objConstructor.setBody("{}");
                obj.addConstructor(objConstructor);
            } catch (CannotCompileException e) {
                // 新创建的类设置构造方法应该不会出错2333
                // forEach里不能抛出异常, 只能装做处理过了
                e.printStackTrace();
            }
            // 属性字段
            table.getFields().forEach(f -> {
                try {
                    var f1 = new CtField(JavassistUtil.getCtClass(classPool, f.getType()), f.getValue(), obj);
                    f1.setModifiers(Modifier.PRIVATE);
                    obj.addField(f1);
                    obj.addMethod(CtNewMethod.setter("set" + f.getValue().substring(0, 1).toUpperCase() + f.getValue().substring(1), f1));
                    obj.addMethod(CtNewMethod.getter("get" + f.getValue().substring(0, 1).toUpperCase() + f.getValue().substring(1), f1));
                } catch (CannotCompileException | NotFoundException e) {
                    // 报错我也没辙, xml自己没配好, 可以管但没必要, 打印异常就完了
                    e.printStackTrace();
                }

            });
            try {
                // 写写写class 如果日志级别为debug XDDDD
                if (logger.isDebugEnabled()) obj.writeFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
                obj.toClass(classLoader, null);
//                obj.detach();
                logger.debug("已创建实体类: {}", fullName);
            } catch (CannotCompileException | IOException e) {
                e.printStackTrace();
            }
        });
        logger.debug("创建实体类: ok");
    }

    /**
     * 根据XML配置，创建对应的GET/GETS方法
     * GET方法应该是安全且幂等的，用于查询资源
     * @throws CannotCompileException
     * @throws IOException
     */
    private void createGetter() throws CannotCompileException, IOException {
        var getterClassName = "GetterService";
        // 创建一个GetterService类
        var getterService = classPool.makeClass(xmlLoader.getServicePackage() + "." + getterClassName);
        // 添加无参的构造体
        getterService.addConstructor(JavassistUtil.createConstructor(getterService, new CtClass[] {}, Modifier.PUBLIC, "{}"));

        var getterServiceFile = getterService.getClassFile();
        var getterServiceConst = getterServiceFile.getConstPool();
        // 给GetterService增加注解@Path("/")
        JavassistUtil.addAnnotation(getterServiceFile, getterServiceConst, new String[] {"javax.ws.rs.Path"}, new String[][]{{"value"}}, new MemberValue[][] {{new StringMemberValue("/", getterServiceConst)}});

        // 创建obj对应的get gets方法
        xmlLoader.getTables().forEach((name, table) -> {
            var className = table.getUrl().substring(0, 1).toUpperCase() + table.getUrl().substring(1);
            var fullPath = xmlLoader.getModelPackage() + "." + className;
            var fields = table.getFields();

            // 方法入参
            var args = new ArrayList<CtClass>();
            for (Field field : fields) {
                try {
                    args.add(JavassistUtil.getCtClass(classPool, field.getType()));
                } catch (NotFoundException e) {
                    // xml里配错了我也没啥办法，不是么
                    e.printStackTrace();
                }

            }
            // 添加getObj方法
            var getObjBodyBuilder = new StringBuilder();
            getObjBodyBuilder.append("{");
            getObjBodyBuilder.append("   cn.anayoo.sweetPotato.db.DatabasePool pool = cn.anayoo.sweetPotato.db.DatabasePool.getInstance();");
            getObjBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            var objArgs = table.getFields().stream().map(Field::getValue).collect(Collectors.toList()).toString();
            objArgs = objArgs.substring(1, objArgs.length() - 1);
            getObjBodyBuilder.append("   java.lang.StringBuilder where = new java.lang.StringBuilder();");
            getObjBodyBuilder.append("   boolean isNull = true;");
            //IntStream.range(0, fields.size()).mapToObj(i -> fields.get(i).getType().equals("Integer") ? getObjBodyBuilder.append("   if ($").append(i + 1).append(" != null) {").append("      if (!isNull) where.append(\" and \");").append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");").append("      isNull = false;}") : getObjBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {").append("      if (!isNull) where.append(\" and \");").append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");").append("      isNull = false;}"));
            for (int i = 0; i < table.getFields().size(); i ++) {
                switch (table.getFields().get(i).getType()) {
                    case "int" :     getObjBodyBuilder.append("   if ($").append(i + 1).append(" != null) {");
                                     getObjBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                                     getObjBodyBuilder.append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");");
                                     getObjBodyBuilder.append("      isNull = false;}");
                                     break;
                    case "String"  : getObjBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {");
                                     getObjBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                                     getObjBodyBuilder.append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");");
                                     getObjBodyBuilder.append("      isNull = false;}");
                                     break;
                }
            }
            getObjBodyBuilder.append("   if (isNull) return null;");
            getObjBodyBuilder.append("   java.lang.String prepareSQL = \"select ").append(objArgs).append(" from ").append(table.getValue()).append(" where \" + ").append("where.toString()").append(" + \";\";");
            getObjBodyBuilder.append("   System.out.println(prepareSQL);");
            getObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            getObjBodyBuilder.append("   int i = 1;");
            //IntStream.range(0, fields.size()).mapToObj(i -> fields.get(i).getType().equals("Integer") ? getObjBodyBuilder.append("   if ($").append(i + 1).append(" != null) {stmt.setInt(i, $").append(i + 1).append(".intValue());}").append("   i ++;") : getObjBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {stmt.setString(i, $").append(i + 1).append(");}").append("   i ++;"));
            for (int i = 0; i < table.getFields().size(); i ++) {
                switch (table.getFields().get(i).getType()) {
                    case "int" :     getObjBodyBuilder.append("   if ($").append(i + 1).append(" != null) {stmt.setInt(i, $").append(i + 1).append(".intValue());").append("   i ++;}"); break;
                    case "String"  : getObjBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {stmt.setString(i, $").append(i + 1).append(");").append("   i ++;}"); break;
                }
            }
            getObjBodyBuilder.append("   java.sql.ResultSet rs = stmt.executeQuery();");
            getObjBodyBuilder.append("   ").append(fullPath).append(" arg = new ").append(fullPath).append("();");
            // 如果存在多个结果(查询中未使用主键，那么返回最后一条记录)
            getObjBodyBuilder.append("   rs.last();");
            getObjBodyBuilder.append("   if (rs.getRow() > 0) {");
            //IntStream.range(0, fields.size()).mapToObj(i -> fields.get(i).getType().equals("Integer") ? getObjBodyBuilder.append("      arg.set").append(fields.get(i).getValue().substring(0, 1).toUpperCase()).append(fields.get(i).getValue().substring(1)).append("(Integer.valueOf(rs.getInt(\"").append(fields.get(i).getValue()).append("\")));") : getObjBodyBuilder.append("      arg.set").append(fields.get(i).getValue().substring(0, 1).toUpperCase()).append(fields.get(i).getValue().substring(1)).append("(rs.getString(\"").append(fields.get(i).getValue()).append("\"));"));
            for (int i = 0; i < table.getFields().size(); i ++) {
                var field = table.getFields().get(i);
                switch (field.getType()) {
                    case "int" :     getObjBodyBuilder.append("      arg.set").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("(Integer.valueOf(rs.getInt(\"").append(field.getValue()).append("\")));"); break;
                    case "String" :  getObjBodyBuilder.append("      arg.set").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("(rs.getString(\"").append(field.getValue()).append("\"));"); break;
                }
            }
            getObjBodyBuilder.append("   } else arg = null;");
            getObjBodyBuilder.append("   conn.close();");
            getObjBodyBuilder.append("   return arg;");
            getObjBodyBuilder.append("}");
            var getObjBody = getObjBodyBuilder.toString();
            //System.out.println(getObjBody.replaceAll("   ", "\n   "));
            try {
                var arg = new CtClass[args.size()];
                args.toArray(arg);
                this.createGetterMethod(classPool.get(fullPath), table.getUrl(), arg, getterService, getObjBody, table);
            } catch (NotFoundException e) {
                e.printStackTrace();
            }

            // 添加getObjs方法
            try {
                // 添加sql属性 pageSize page order orderType
                args.addAll(List.of(classPool.get("int"), classPool.get("int"), classPool.get("java.lang.String"), classPool.get("java.lang.String")));
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
            var getObjsBodyBuilder = new StringBuilder();
            getObjsBodyBuilder.append("{");
            getObjsBodyBuilder.append("   cn.anayoo.sweetPotato.db.DatabasePool pool = cn.anayoo.sweetPotato.db.DatabasePool.getInstance();");
            getObjsBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            var objsArgs = table.getFields().stream().map(Field::getValue).collect(Collectors.toList()).toString();
            objsArgs = objsArgs.substring(1, objsArgs.length() - 1);
            getObjsBodyBuilder.append("   java.lang.StringBuilder where = new java.lang.StringBuilder();");
            getObjsBodyBuilder.append("   boolean isNull = true;");
            //IntStream.range(0, fields.size()).mapToObj(i -> fields.get(i).getType().equals("Integer") ? getObjsBodyBuilder.append("   if ($").append(i + 1).append(" != null) {").append("      if (!isNull) where.append(\" and \");").append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");").append("      isNull = false;}") : getObjsBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {").append("      if (!isNull) where.append(\" and \");").append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");").append("      isNull = false;}"));
            for (int i = 0; i < table.getFields().size(); i ++) {
                switch (table.getFields().get(i).getType()) {
                    case "int" : getObjsBodyBuilder.append("   if ($").append(i + 1).append(" != null) {");
                        getObjsBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                        getObjsBodyBuilder.append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");");
                        getObjsBodyBuilder.append("      isNull = false;}");
                        break;
                    case "String"  : getObjsBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {");
                        getObjsBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                        getObjsBodyBuilder.append("      where.append(\"").append(table.getFields().get(i).getValue()).append("=?\");");
                        getObjsBodyBuilder.append("      isNull = false;}");
                        break;
                }
            }
            getObjsBodyBuilder.append("   java.lang.String whereStr = isNull ? \"\" : \" where \" + where.toString();");
            getObjsBodyBuilder.append("   int limitStart = $").append(table.getFields().size() + 1).append(" * ($").append(table.getFields().size() + 2).append(" - 1);");
            getObjsBodyBuilder.append("   java.lang.String prepareSQL = \"select ").append(objsArgs).append(" from ").append(table.getValue()).append("\" + ").append("whereStr").append(" + \" order by \" + ").append("$").append(table.getFields().size() + 3).append(" + \" \" + $").append(table.getFields().size() + 4).append(" + \" limit \" + limitStart + \",\" + $").append(table.getFields().size() + 1).append(" + \";\";");
            //getObjsBodyBuilder.append("   System.out.println(prepareSQL);");
            getObjsBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            getObjsBodyBuilder.append("   int i = 1;");
            //IntStream.range(0, fields.size()).mapToObj(i -> fields.get(i).getType().equals("Integer") ? getObjsBodyBuilder.append("   if ($").append(i + 1).append(" != null) {stmt.setInt(i, $").append(i + 1).append(".intValue());}").append("   i ++;") : getObjsBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {stmt.setString(i, $").append(i + 1).append(");}").append("   i ++;"));
            for (int i = 0; i < table.getFields().size(); i ++) {
                switch (table.getFields().get(i).getType()) {
                    case "int" :     getObjsBodyBuilder.append("   if ($").append(i + 1).append(" != null) {stmt.setInt(i, $").append(i + 1).append(".intValue());").append("   i ++;}"); break;
                    case "String"  : getObjsBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {stmt.setString(i, $").append(i + 1).append(");").append("   i ++;}"); break;
                }
            }
            getObjsBodyBuilder.append("   java.sql.ResultSet rs = stmt.executeQuery();");
            getObjsBodyBuilder.append("   java.util.List args = new java.util.ArrayList();");
            getObjsBodyBuilder.append("   while(rs.next()) {");
            getObjsBodyBuilder.append("      ").append(fullPath).append(" arg = new ").append(fullPath).append("();");
            //IntStream.range(0, fields.size()).mapToObj(i -> fields.get(i).getType().equals("Integer") ? getObjsBodyBuilder.append("      arg.set").append(fields.get(i).getValue().substring(0, 1).toUpperCase()).append(fields.get(i).getValue().substring(1)).append("(Integer.valueOf(rs.getInt(\"").append(fields.get(i).getValue()).append("\")));") : getObjsBodyBuilder.append("      arg.set").append(fields.get(i).getValue().substring(0, 1).toUpperCase()).append(fields.get(i).getValue().substring(1)).append("(rs.getString(\"").append(fields.get(i).getValue()).append("\"));"));
            for (int i = 0; i < table.getFields().size(); i ++) {
                var field = table.getFields().get(i);
                switch (field.getType()) {
                    case "int" :    getObjsBodyBuilder.append("      arg.set").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("(Integer.valueOf(rs.getInt(\"").append(field.getValue()).append("\")));"); break;
                    case "String" : getObjsBodyBuilder.append("      arg.set").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("(rs.getString(\"").append(field.getValue()).append("\"));"); break;
                }
            }
            getObjsBodyBuilder.append("      args.add(arg);");
            getObjsBodyBuilder.append("   }");
            getObjsBodyBuilder.append("   conn.close();");
            getObjsBodyBuilder.append("   return args;}");
            //System.out.println(getObjsBodyBuilder.toString().replaceAll("   ", "\n   "));
            var getObjsBody = getObjsBodyBuilder.toString();
            try {
                var arg = new CtClass[args.size()];
                args.toArray(arg);
                this.createGetterMethod(classPool.get("java.util.List"), table.getGets(), arg, getterService, getObjsBody, table);
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        });
        this.writeClassFile(getterService, getterClassName + ".class");
//        var classFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath() + "GetterService.class");
//        if (classFile.exists()) classFile.delete();
//        getterService.writeFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
    }

    /**
     * 根据XML配置，创建对应的POST方法
     * POST方法应该是不安全且不幂等的，用于创建资源
     */
    private void createPoster() throws CannotCompileException, IOException {
        var posterClassName = "PosterService";
        // 创建一个PosterService类
        var posterService = classPool.makeClass(xmlLoader.getServicePackage() + "." + posterClassName);
        // 添加无参的构造体
        posterService.addConstructor(JavassistUtil.createConstructor(posterService, new CtClass[] {}, Modifier.PUBLIC, "{}"));

        var posterServiceFile = posterService.getClassFile();
        var posterServiceConst = posterServiceFile.getConstPool();
        // 给PosterService增加注解@Path("/")
        JavassistUtil.addAnnotation(posterServiceFile, posterServiceConst, new String[] {"javax.ws.rs.Path"}, new String[][]{{"value"}}, new MemberValue[][] {{new StringMemberValue("/", posterServiceConst)}});

        // 创建obj对应的post方法
        xmlLoader.getTables().forEach((name, table) -> {
            var className = table.getUrl().substring(0, 1).toUpperCase() + table.getUrl().substring(1);
            var fullPath = xmlLoader.getModelPackage() + "." + className;
            var fields = table.getFields();

            // 添加postObj方法
            var postObjBodyBuilder = new StringBuilder();
            postObjBodyBuilder.append("{");
            // 参数校验
            for (Field field : fields) {
                if (field.getType().equals("String")) {
                    postObjBodyBuilder.append("   if (!java.util.regex.Pattern.compile(\"").append(field.getRegex().replaceAll("\\\\", "\\\\\\\\")).append("\").matcher($1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("()).find()) {");
                    postObjBodyBuilder.append("      return javax.ws.rs.core.Response.status(400).entity(\"\\\"参数'").append(field.getValue()).append("'校验错误!\\\"\").build();}");
                }
            }
            postObjBodyBuilder.append("   cn.anayoo.sweetPotato.db.DatabasePool pool = cn.anayoo.sweetPotato.db.DatabasePool.getInstance();");
            postObjBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            // insert语句
            postObjBodyBuilder.append("   java.lang.String prepareSQL = \"insert into ").append(table.getValue()).append(" (");
            var isFirst = true;
            for (Field field : fields) {
                if (!field.getValue().equals(table.getKey())) {
                    if (!isFirst) postObjBodyBuilder.append(", ");
                    postObjBodyBuilder.append(field.getValue());
                    isFirst = false;
                }
            }
            postObjBodyBuilder.append(") values (");
            isFirst = true;
            for (Field field : fields) {
                if (!field.getValue().equals(table.getKey())) {
                    if (!isFirst) postObjBodyBuilder.append(", ");
                    postObjBodyBuilder.append("?");
                    isFirst = false;
                }
            }
            postObjBodyBuilder.append(");\";");
            postObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            // stmt赋值
            var index = 1;
            for (Field field : fields) {
                if (!field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : postObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("().intValue());"); break;
                        case "String" : postObjBodyBuilder.append("   stmt.setString(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("());"); break;
                    }
                    index = index + 1;
                }
            }
            postObjBodyBuilder.append("   java.lang.String number = \"\" + stmt.executeUpdate();");
            postObjBodyBuilder.append("   conn.close();");
            postObjBodyBuilder.append("   return javax.ws.rs.core.Response.status(200).entity(number).build();}");

            var postObjBody = postObjBodyBuilder.toString();
            //System.out.println(postObjBody.replaceAll("   ", "\n   "));
            try {
                this.createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl(), new CtClass[]{classPool.get(fullPath)}, posterService, postObjBody, "POST");
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        });
        this.writeClassFile(posterService, posterClassName + ".class");
//        var classFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath() + "PosterService.class");
//        if (classFile.exists()) classFile.delete();
//        posterService.writeFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
    }


    /**
     * 根据XML配置，创建对应的PUT方法
     * PUT方法应该是不安全但幂等的，用于更新/创建资源
     */
    private void createPutter() throws CannotCompileException, IOException {
        var putterClassName = "PutterService";
        // 创建一个PosterService类
        var putterService = classPool.makeClass(xmlLoader.getServicePackage() + "." + putterClassName);
        // 添加无参的构造体
        putterService.addConstructor(JavassistUtil.createConstructor(putterService, new CtClass[] {}, Modifier.PUBLIC, "{}"));

        var putterServiceFile = putterService.getClassFile();
        var putterServiceConst = putterServiceFile.getConstPool();
        // 给PutterService增加注解@Path("/")
        JavassistUtil.addAnnotation(putterServiceFile, putterServiceConst, new String[] {"javax.ws.rs.Path"}, new String[][]{{"value"}}, new MemberValue[][] {{new StringMemberValue("/", putterServiceConst)}});

        // 创建obj对应的put方法
        xmlLoader.getTables().forEach((name, table) -> {
            var className = table.getUrl().substring(0, 1).toUpperCase() + table.getUrl().substring(1);
            var fullPath = xmlLoader.getModelPackage() + "." + className;
            var fields = table.getFields();

            // 添加putObj方法
            var putObjBodyBuilder = new StringBuilder();
            putObjBodyBuilder.append("{");
            // 参数校验
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    putObjBodyBuilder.append("   if ($1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("() == null) { return javax.ws.rs.core.Response.status(400).entity(\"\\\"未指明主键\\\"\").build(); }");
                } else if (field.getType().equals("String")) {
                    putObjBodyBuilder.append("   if (!java.util.regex.Pattern.compile(\"").append(field.getRegex().replaceAll("\\\\", "\\\\\\\\")).append("\").matcher($1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("()).find()) {");
                    putObjBodyBuilder.append("      return javax.ws.rs.core.Response.status(400).entity(\"\\\"参数'").append(field.getValue()).append("'校验错误!\\\"\").build();}");
                }
            }
            putObjBodyBuilder.append("   cn.anayoo.sweetPotato.db.DatabasePool pool = cn.anayoo.sweetPotato.db.DatabasePool.getInstance();");
            putObjBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            // selectByKey
            putObjBodyBuilder.append("   java.lang.String prepareSQL = \"select 1 from ").append(table.getValue()).append(" where ");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    putObjBodyBuilder.append(field.getValue()).append("=?;\";");
                }
            }
            putObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(1, $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("().intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(1, $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("());"); break;
                    }
                }
            }
            putObjBodyBuilder.append("   java.sql.ResultSet rs = stmt.executeQuery();");
            // 如果存在多个结果(查询中未使用主键，那么返回最后一条记录)
            putObjBodyBuilder.append("   rs.last();");
            putObjBodyBuilder.append("   int number = rs.getRow();");

            putObjBodyBuilder.append("   if (number > 0) {");
            putObjBodyBuilder.append("      prepareSQL = \"update ").append(table.getValue()).append(" set ");
            var isFirst = true;
            for (Field field : fields) {
                if (!field.getValue().equals(table.getKey())) {
                    if (!isFirst) putObjBodyBuilder.append(", ");
                    putObjBodyBuilder.append(field.getValue()).append("=?");
                    isFirst = false;
                }
            }
            putObjBodyBuilder.append(" where ");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    putObjBodyBuilder.append(field.getValue()).append("=?;\";");
                }
            }
            putObjBodyBuilder.append("   stmt = conn.prepareStatement(prepareSQL);");
            // stmt赋值
            var index = 1;
            for (Field field : fields) {
                if (!field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("().intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("());"); break;
                    }
                    index = index + 1;
                }
            }
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("().intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("());"); break;
                    }
                    index = index + 1;
                }
            }
            putObjBodyBuilder.append("   } else {");
            putObjBodyBuilder.append("      prepareSQL = \"insert into ").append(table.getValue()).append(" (");
            isFirst = true;
            for (Field field : fields) {
                if (!isFirst) putObjBodyBuilder.append(", ");
                putObjBodyBuilder.append(field.getValue());
                isFirst = false;
            }
            putObjBodyBuilder.append(") values (");
            isFirst = true;
            for (Field field : fields) {
                if (!isFirst) putObjBodyBuilder.append(", ");
                putObjBodyBuilder.append("?");
                isFirst = false;
            }
            putObjBodyBuilder.append(");\";");
            putObjBodyBuilder.append("   stmt = conn.prepareStatement(prepareSQL);");
            // stmt赋值
            index = 1;
            for (Field field : fields) {
                switch (field.getType()) {
                    case "int" : putObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("().intValue());"); break;
                    case "String" : putObjBodyBuilder.append("   stmt.setString(").append(index).append(", $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("());"); break;
                }
                index = index + 1;
            }
            putObjBodyBuilder.append("}");
            putObjBodyBuilder.append("   java.lang.String number = \"\" + stmt.executeUpdate();");
            putObjBodyBuilder.append("   conn.close();");
            putObjBodyBuilder.append("   return javax.ws.rs.core.Response.status(200).entity(number).build();}");

            var putObjBody = putObjBodyBuilder.toString();
            //System.out.println(putObjBody.replaceAll("   ", "\n   "));
            try {
                this.createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl(), new CtClass[]{classPool.get(fullPath)}, putterService, putObjBody, "PUT");
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        });
        this.writeClassFile(putterService, putterClassName + ".class");
//        var classFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath() + "PutterService.class");
//        if (classFile.exists()) classFile.delete();
//        putterService.writeFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
    }


    /**
     * 根据XML配置，创建对应的DELETE方法
     * DELETE方法应该是不安全但幂等的，用于删除资源
     */
    private void createDeleter() throws CannotCompileException, IOException {
        var deleterClassName = "DeleterService";
        // 创建一个DeleterService类
        var deleterService = classPool.makeClass(xmlLoader.getServicePackage() + "." + deleterClassName);
        // 添加无参的构造体
        deleterService.addConstructor(JavassistUtil.createConstructor(deleterService, new CtClass[] {}, Modifier.PUBLIC, "{}"));

        var deleterServiceFile = deleterService.getClassFile();
        var deleterServiceConst = deleterServiceFile.getConstPool();
        // 给DeleterService增加注解@Path("/")
        JavassistUtil.addAnnotation(deleterServiceFile, deleterServiceConst, new String[] {"javax.ws.rs.Path"}, new String[][]{{"value"}}, new MemberValue[][] {{new StringMemberValue("/", deleterServiceConst)}});

        // 创建obj对应的delete方法
        xmlLoader.getTables().forEach((name, table) -> {
            var className = table.getUrl().substring(0, 1).toUpperCase() + table.getUrl().substring(1);
            var fullPath = xmlLoader.getModelPackage() + "." + className;
            var fields = table.getFields();

            // 添加deleteObj方法
            var deleteObjBodyBuilder = new StringBuilder();
            deleteObjBodyBuilder.append("{");
            // 参数校验
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    deleteObjBodyBuilder.append("   if ($1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("() == null) { return javax.ws.rs.core.Response.status(400).entity(\"\\\"未指明主键\\\"\").build(); }");
                }
            }
            deleteObjBodyBuilder.append("   cn.anayoo.sweetPotato.db.DatabasePool pool = cn.anayoo.sweetPotato.db.DatabasePool.getInstance();");
            deleteObjBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            // delete语句
            deleteObjBodyBuilder.append("   java.lang.String prepareSQL = \"delete from ").append(table.getValue()).append(" where ");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    deleteObjBodyBuilder.append(field.getValue()).append("=?;\";");
                }
            }
            deleteObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : deleteObjBodyBuilder.append("   stmt.setInt(1, $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("().intValue());"); break;
                        case "String" : deleteObjBodyBuilder.append("   stmt.setString(1, $1.get").append(field.getValue().substring(0, 1).toUpperCase()).append(field.getValue().substring(1)).append("());"); break;
                    }
                }
            }
            deleteObjBodyBuilder.append("   java.lang.String number = \"\" + stmt.executeUpdate();");
            deleteObjBodyBuilder.append("   conn.close();");
            deleteObjBodyBuilder.append("   return javax.ws.rs.core.Response.status(200).entity(number).build();}");

            var deleteObjBody = deleteObjBodyBuilder.toString();
            //System.out.println(deleteObjBody.replaceAll("   ", "\n   "));
            try {
                this.createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl(), new CtClass[]{classPool.get(fullPath)}, deleterService, deleteObjBody, "DELETE");
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        });
        this.writeClassFile(deleterService, deleterClassName + ".class");
    }

    private void createGetterMethod(CtClass returnType, String url, CtClass[] parameters, CtClass declaring, String body, Table table) {
        var getterServiceFile = declaring.getClassFile();
        var getterServiceConst = getterServiceFile.getConstPool();
        CtMethod m = null;
        try {
            var mname = "get" + url.substring(0, 1).toUpperCase() + url.substring(1);
            m = new CtMethod(returnType, mname, parameters, declaring);
            m.setModifiers(Modifier.PUBLIC);
            // 方法内的处理逻辑
            m.setBody(body);
            declaring.addMethod(m);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }

        // 给参数增加注解 @QueryParam @DefaultValue    参考：https://www.cnblogs.com/coshaho/p/5105545.html
        // 涉及到tables， 暂时不封装到JavassistUtil了 = =
        var parameterAtrribute = new ParameterAnnotationsAttribute(getterServiceConst, ParameterAnnotationsAttribute.visibleTag);
        var paramArrays = new Annotation[parameters.length][2];
        var sqlArrays = new String[]{"pageSize", "page", "order", "orderType"};
        var sqlDefaultArrays = new String[]{"" + table.getPageSize(), "1", table.getOrder(), table.getOrderType()};
        for (int i = 0; i < parameters.length; i ++) {
            // 根据 parameters 和 table.fields 的长度判断是否为SQL分页查询属性
            if (i < table.getFields().size()) {
                var field = table.getFields().get(i);
                // @QueryParam
                var f1Annot1 = new Annotation("javax.ws.rs.QueryParam", getterServiceConst);
                f1Annot1.addMemberValue("value", new StringMemberValue(field.getValue(), getterServiceConst));
                paramArrays[i][1] = f1Annot1;
                // @DefaultValue
                var f1Annot2 = new Annotation("javax.ws.rs.DefaultValue", getterServiceConst);
                f1Annot2.addMemberValue("value", new StringMemberValue("", getterServiceConst));
                paramArrays[i][0] = f1Annot2;
            } else {
                // @QueryParam
                var f1Annot1 = new Annotation("javax.ws.rs.QueryParam", getterServiceConst);
                f1Annot1.addMemberValue("value", new StringMemberValue(sqlArrays[i - table.getFields().size()], getterServiceConst));
                paramArrays[i][1] = f1Annot1;
                // @DefaultValue
                var f1Annot2 = new Annotation("javax.ws.rs.DefaultValue", getterServiceConst);
                f1Annot2.addMemberValue("value", new StringMemberValue(sqlDefaultArrays[i - table.getFields().size()], getterServiceConst));
                paramArrays[i][0] = f1Annot2;
            }
        }
        parameterAtrribute.setAnnotations(paramArrays);
        if (m != null) m.getMethodInfo().addAttribute(parameterAtrribute);

        // 给方法增加注解@GET @Path("/$url") @Consumes({"application/json"}) @Produces({"application/json"})
        var annotationClasses = new String[] {
                "javax.ws.rs.GET", "javax.ws.rs.Path", "javax.ws.rs.Consumes", "javax.ws.rs.Produces"
        };
        var memberNames = new String[][] {
                {}, {"value"}, {"value"}, {"value"}
        };
        var jsonArrayMemberValue = new ArrayMemberValue(new StringMemberValue("", getterServiceConst), getterServiceConst);
        jsonArrayMemberValue.setValue(new MemberValue[]{new StringMemberValue("application/json", getterServiceConst)});
        var memberValues = new MemberValue[][] {
                {}, {new StringMemberValue("/" + url, getterServiceConst)}, {jsonArrayMemberValue}, {jsonArrayMemberValue}
        };
        JavassistUtil.addAnnotation(getterServiceConst, m, annotationClasses, memberNames, memberValues);
    }

    private void createOtherMethod(CtClass returnType, String url, CtClass[] parameters, CtClass declaring, String body, String method) {
        var serviceFile = declaring.getClassFile();
        var serviceConst = serviceFile.getConstPool();
        CtMethod m = null;
        try {
            m = new CtMethod(returnType, method.toLowerCase() + url.substring(0, 1).toUpperCase() + url.substring(1), parameters, declaring);
            m.setModifiers(Modifier.PUBLIC);
            // 方法内的处理逻辑
            m.setBody(body);
            declaring.addMethod(m);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }

        // 给方法增加注解@${method} @Path("/${url}") @Consumes({"application/json"}) @Produces({"application/json"})
        var annotationClasses = new String[] {
                "javax.ws.rs." + method, "javax.ws.rs.Path", "javax.ws.rs.Consumes", "javax.ws.rs.Produces"
        };
        var memberNames = new String[][] {
                {}, {"value"}, {"value"}, {"value"}
        };
        var jsonArrayMemberValue = new ArrayMemberValue(new StringMemberValue("", serviceConst), serviceConst);
        jsonArrayMemberValue.setValue(new MemberValue[]{new StringMemberValue("application/json", serviceConst)});
        var memberValues = new MemberValue[][] {
                {}, {new StringMemberValue("/" + url, serviceConst)}, {jsonArrayMemberValue}, {jsonArrayMemberValue}
        };
        JavassistUtil.addAnnotation(serviceConst, m, annotationClasses, memberNames, memberValues);
    }

    private void writeClassFile(CtClass clazz, String className) throws CannotCompileException, IOException {
        var classFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath() + className);
        if (classFile.exists()) classFile.delete();
        clazz.writeFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
    }
}