package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.model.Field;
import cn.anayoo.sweetpotato.model.Table;
import cn.anayoo.sweetpotato.util.JavassistUtil;
import javassist.*;
import javassist.bytecode.ParameterAnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by anayoo on 19-05-13
 * @author anayoo
 */
public class RestServiceCreater {
    // 先用slf4j调试, 框架正式打包的时候改用jul
    private Logger logger = Logger.getLogger(RestServiceCreater.class.getName());

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
        logger.log(Level.INFO, "创建实体类中...");
        xmlLoader.getTables().forEach((name, table) -> {
            // 创建实体类
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
                // model类可以不写到磁盘，因为每次调用http接口不需要重新加载model的class
                //obj.writeFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
                obj.toClass(classLoader, null);
                logger.log(Level.FINE, "已创建实体类: {}", fullName);
            } catch (CannotCompileException e) {
                e.printStackTrace();
            }

            // 创建实体分页类
            var pageFullName = fullName + "Page";
            var objPage = classPool.makeClass(pageFullName);
            // 构造方法
            var objConstructorPage = new CtConstructor(new CtClass[] {}, objPage);
            objConstructorPage.setModifiers(Modifier.PUBLIC);
            try {
                objConstructorPage.setBody("{}");
                objPage.addConstructor(objConstructorPage);
            } catch (CannotCompileException e) {
                // 新创建的类设置构造方法应该不会出错2333
                // forEach里不能抛出异常, 只能装做处理过了
                e.printStackTrace();
            }
            // 属性字段
            try {
                var f1 = new CtField(JavassistUtil.getCtClass(classPool, "java.util.List"), "data", objPage);
                f1.setModifiers(Modifier.PRIVATE);
                objPage.addField(f1);
                objPage.addMethod(CtNewMethod.setter("setData", f1));
                objPage.addMethod(CtNewMethod.getter("getData", f1));
                var f2 = new CtField(JavassistUtil.getCtClass(classPool, "cn.anayoo.sweetpotato.model.Setting"), "setting", objPage);
                f2.setModifiers(Modifier.PRIVATE);
                objPage.addField(f2);
                objPage.addMethod(CtNewMethod.setter("setSetting", f2));
                objPage.addMethod(CtNewMethod.getter("getSetting", f2));
                objPage.toClass(classLoader, null);
                logger.log(Level.FINE, "已创建实体类: {}", pageFullName);
            } catch (CannotCompileException | NotFoundException e) {
                // 报错我也没辙, xml自己没配好, 可以管但没必要, 打印异常就完了
                e.printStackTrace();
            }
        });
        logger.log(Level.INFO, "创建实体类: ok");
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
        JavassistUtil.addAnnotation(getterServiceFile, getterServiceConst, new String[] {"javax.ws.rs.Path", "org.springframework.stereotype.Component"}, new String[][]{{"value"}, {"value"}}, new MemberValue[][] {{new StringMemberValue("/", getterServiceConst)}, {new StringMemberValue("", getterServiceConst)}});

        // 创建obj对应的get gets方法
        xmlLoader.getTables().forEach((name, table) -> {
            var className = table.getUrl().substring(0, 1).toUpperCase() + table.getUrl().substring(1);
            var fullPath = xmlLoader.getModelPackage() + "." + className;
            var pageFullName = xmlLoader.getModelPackage() + "." + table.getName().substring(0, 1).toUpperCase() + table.getName().substring(1) + "Page";
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
            getObjBodyBuilder.append("   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();");
            getObjBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            var objArgs = fields.stream().map(Field::getValue).collect(Collectors.toList()).toString();
            objArgs = objArgs.substring(1, objArgs.length() - 1);
            getObjBodyBuilder.append("   java.lang.StringBuilder where = new java.lang.StringBuilder();");
            getObjBodyBuilder.append("   boolean isNull = true;");

            for (int i = 0; i < fields.size(); i ++) {
                switch (fields.get(i).getType()) {
                    case "int" :     getObjBodyBuilder.append("   if ($").append(i + 1).append(" != null) {");
                                     getObjBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                                     getObjBodyBuilder.append("      where.append(\"").append(fields.get(i).getValue()).append("=?\");");
                                     getObjBodyBuilder.append("      isNull = false;}");
                                     break;
                    case "String"  : getObjBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {");
                                     getObjBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                                     getObjBodyBuilder.append("      where.append(\"").append(fields.get(i).getValue()).append("=?\");");
                                     getObjBodyBuilder.append("      isNull = false;}");
                                     break;
                }
            }
            getObjBodyBuilder.append("   if (isNull) return null;");
            getObjBodyBuilder.append("   java.lang.String prepareSQL = \"select ").append(objArgs).append(" from ").append(table.getValue()).append(" where \" + ").append("where.toString()").append(" + \";\";");
            getObjBodyBuilder.append("   System.out.println(prepareSQL);");
            getObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            getObjBodyBuilder.append("   int i = 1;");

            for (int i = 0; i < fields.size(); i ++) {
                switch (fields.get(i).getType()) {
                    case "int" :     getObjBodyBuilder.append("   if ($").append(i + 1).append(" != null) {stmt.setInt(i, $").append(i + 1).append(".intValue());").append("   i ++;}"); break;
                    case "String"  : getObjBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {stmt.setString(i, $").append(i + 1).append(");").append("   i ++;}"); break;
                }
            }
            getObjBodyBuilder.append("   java.sql.ResultSet rs = stmt.executeQuery();");
            getObjBodyBuilder.append("   ").append(fullPath).append(" arg = new ").append(fullPath).append("();");
            // 如果存在多个结果(查询中未使用主键，那么返回最后一条记录)
            getObjBodyBuilder.append("   rs.last();");
            getObjBodyBuilder.append("   if (rs.getRow() > 0) {");

            for (int i = 0; i < fields.size(); i ++) {
                var field = fields.get(i);
                switch (field.getType()) {
                    case "int" :     getObjBodyBuilder.append("      arg.").append(field.getSetterName()).append("(Integer.valueOf(rs.getInt(\"").append(field.getValue()).append("\")));"); break;
                    case "String" :  getObjBodyBuilder.append("      arg.").append(field.getSetterName()).append("(rs.getString(\"").append(field.getValue()).append("\"));"); break;
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
                // 添加sql属性 pageSize page order orderType count
                args.addAll(List.of(classPool.get("int"), classPool.get("int"), classPool.get("java.lang.String"), classPool.get("java.lang.String"), classPool.get("boolean")));
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
            var getObjsBodyBuilder = new StringBuilder();
            getObjsBodyBuilder.append("{");
            getObjsBodyBuilder.append("   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();");
            getObjsBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            var objsArgs = fields.stream().map(Field::getValue).collect(Collectors.toList()).toString();
            objsArgs = objsArgs.substring(1, objsArgs.length() - 1);
            getObjsBodyBuilder.append("   java.lang.StringBuilder where = new java.lang.StringBuilder();");
            getObjsBodyBuilder.append("   boolean isNull = true;");

            for (int i = 0; i < fields.size(); i ++) {
                switch (fields.get(i).getType()) {
                    case "int" : getObjsBodyBuilder.append("   if ($").append(i + 1).append(" != null) {");
                        getObjsBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                        getObjsBodyBuilder.append("      where.append(\"").append(fields.get(i).getValue()).append("=?\");");
                        getObjsBodyBuilder.append("      isNull = false;}");
                        break;
                    case "String"  : getObjsBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {");
                        getObjsBodyBuilder.append("      if (!isNull) where.append(\" and \");");
                        getObjsBodyBuilder.append("      where.append(\"").append(fields.get(i).getValue()).append("=?\");");
                        getObjsBodyBuilder.append("      isNull = false;}");
                        break;
                }
            }
            getObjsBodyBuilder.append("   java.lang.String whereStr = isNull ? \"\" : \" where \" + where.toString();");
            getObjsBodyBuilder.append("   int limitStart = $").append(fields.size() + 1).append(" * ($").append(fields.size() + 2).append(" - 1);");
            getObjsBodyBuilder.append("   java.lang.String prepareSQL = \"select ").append(objsArgs).append(" from ").append(table.getValue()).append("\" + ").append("whereStr").append(" + \" order by \" + ").append("$").append(fields.size() + 3).append(" + \" \" + $").append(fields.size() + 4).append(" + \" limit \" + limitStart + \",\" + $").append(fields.size() + 1).append(" + \";\";");
            getObjsBodyBuilder.append("   System.out.println(prepareSQL);");
            getObjsBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            getObjsBodyBuilder.append("   int i = 1;");

            for (int i = 0; i < fields.size(); i ++) {
                switch (fields.get(i).getType()) {
                    case "int" :     getObjsBodyBuilder.append("   if ($").append(i + 1).append(" != null) {stmt.setInt(i, $").append(i + 1).append(".intValue());").append("   i ++;}"); break;
                    case "String"  : getObjsBodyBuilder.append("   if (!$").append(i + 1).append(".equals(\"\")) {stmt.setString(i, $").append(i + 1).append(");").append("   i ++;}"); break;
                }
            }
            getObjsBodyBuilder.append("   java.sql.ResultSet rs = stmt.executeQuery();");
            getObjsBodyBuilder.append("   java.util.List args = new java.util.ArrayList();");
            getObjsBodyBuilder.append("   while(rs.next()) {");
            getObjsBodyBuilder.append("      ").append(fullPath).append(" arg = new ").append(fullPath).append("();");

            for (int i = 0; i < fields.size(); i ++) {
                var field = fields.get(i);
                switch (field.getType()) {
                    case "int" :    getObjsBodyBuilder.append("      arg.").append(field.getSetterName()).append("(Integer.valueOf(rs.getInt(\"").append(field.getValue()).append("\")));"); break;
                    case "String" : getObjsBodyBuilder.append("      arg.").append(field.getSetterName()).append("(rs.getString(\"").append(field.getValue()).append("\"));"); break;
                }
            }
            getObjsBodyBuilder.append("      args.add(arg);");
            getObjsBodyBuilder.append("   }");
            getObjsBodyBuilder.append("   ").append(pageFullName).append(" page = new ").append(pageFullName).append("();");
            getObjsBodyBuilder.append("   page.setData(args);");
            getObjsBodyBuilder.append("   cn.anayoo.sweetpotato.model.Setting setting = new cn.anayoo.sweetpotato.model.Setting();");
            getObjsBodyBuilder.append("   setting.setPageSize($").append(fields.size() + 1).append(");");
            getObjsBodyBuilder.append("   setting.setPage($").append(fields.size() + 2).append(");");
            getObjsBodyBuilder.append("   setting.setOrder($").append(fields.size() + 3).append(");");
            getObjsBodyBuilder.append("   setting.setOrderType($").append(fields.size() + 4).append(");");
            getObjsBodyBuilder.append("   if ($").append(fields.size() + 5).append(") {");
            getObjsBodyBuilder.append("      prepareSQL = \"select count(1) from ").append(table.getValue()).append("\" + ").append("whereStr").append(" + \";\";");
            getObjsBodyBuilder.append("      System.out.println(prepareSQL);");
            getObjsBodyBuilder.append("      java.sql.PreparedStatement stmt2 = conn.prepareStatement(prepareSQL);");
            getObjsBodyBuilder.append("      i = 1;");

            for (int i = 0; i < fields.size(); i ++) {
                switch (fields.get(i).getType()) {
                    case "int" :     getObjsBodyBuilder.append("      if ($").append(i + 1).append(" != null) {stmt2.setInt(i, $").append(i + 1).append(".intValue());").append("   i ++;}"); break;
                    case "String"  : getObjsBodyBuilder.append("      if (!$").append(i + 1).append(".equals(\"\")) {stmt2.setString(i, $").append(i + 1).append(");").append("   i ++;}"); break;
                }
            }
            getObjsBodyBuilder.append("      java.sql.ResultSet rs2 = stmt2.executeQuery();");
            getObjsBodyBuilder.append("      if(rs2.next()) {");
            getObjsBodyBuilder.append("         setting.setCount(java.lang.Integer.valueOf(rs2.getInt(1)));");
            getObjsBodyBuilder.append("      }");
            getObjsBodyBuilder.append("   }");
            getObjsBodyBuilder.append("   conn.close();");
            getObjsBodyBuilder.append("   page.setSetting(setting);");
            getObjsBodyBuilder.append("   return page;}");
            //System.out.println(getObjsBodyBuilder.toString().replaceAll("   ", "\n   "));
            var getObjsBody = getObjsBodyBuilder.toString();
            try {
                var arg = new CtClass[args.size()];
                args.toArray(arg);
                this.createGetterMethod(classPool.get(pageFullName), table.getGets(), arg, getterService, getObjsBody, table);
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        });
        this.writeClassFile(getterService, getterClassName + ".class");
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
        JavassistUtil.addAnnotation(posterServiceFile, posterServiceConst, new String[] {"javax.ws.rs.Path", "org.springframework.stereotype.Component"}, new String[][]{{"value"}, {"value"}}, new MemberValue[][] {{new StringMemberValue("/", posterServiceConst)}, {new StringMemberValue("", posterServiceConst)}});

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
                if (!field.isAllowNone()) {
                    postObjBodyBuilder.append("   if ($1.").append(field.getGetterName()).append("() == null) { return javax.ws.rs.core.Response.status(400).entity(\"\\\"属性").append(field.getValue()).append("不能为空\\\"\").build(); }");
                }
                if (field.getType().equals("String")) {
                    postObjBodyBuilder.append("   if ($1.").append(field.getGetterName()).append("() != null && !java.util.regex.Pattern.compile(\"").append(field.getRegex().replaceAll("\\\\", "\\\\\\\\")).append("\").matcher($1.").append(field.getGetterName()).append("()).find()) {");
                    postObjBodyBuilder.append("      return javax.ws.rs.core.Response.status(400).entity(\"\\\"参数'").append(field.getValue()).append("'校验错误!\\\"\").build();}");
                }
            }
            postObjBodyBuilder.append("   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();");
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
            postObjBodyBuilder.append("   System.out.println(prepareSQL);");
            postObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            // stmt赋值
            var index = 1;
            for (Field field : fields) {
                if (!field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : postObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $1.").append(field.getGetterName()).append("().intValue());"); break;
                        case "String" : postObjBodyBuilder.append("   stmt.setString(").append(index).append(", $1.").append(field.getGetterName()).append("());"); break;
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
        JavassistUtil.addAnnotation(putterServiceFile, putterServiceConst, new String[] {"javax.ws.rs.Path", "org.springframework.stereotype.Component"}, new String[][]{{"value"}, {"value"}}, new MemberValue[][] {{new StringMemberValue("/", putterServiceConst)}, {new StringMemberValue("", putterServiceConst)}});

        // 创建obj对应的put方法
        xmlLoader.getTables().forEach((name, table) -> {
            var className = table.getUrl().substring(0, 1).toUpperCase() + table.getUrl().substring(1);
            var fullPath = xmlLoader.getModelPackage() + "." + className;
            var fields = table.getFields();

            // 添加putObj方法
            var putObjBodyBuilder = new StringBuilder();
            putObjBodyBuilder.append("{");
            // 参数校验
            putObjBodyBuilder.append("   if ($1 == null) { return javax.ws.rs.core.Response.status(400).entity(\"\\\"未指明主键\\\"\").build(); }");
            for (Field field : fields) {
                if (!field.isAllowNone()) {
                    putObjBodyBuilder.append("   if ($2.").append(field.getGetterName()).append("() == null) { return javax.ws.rs.core.Response.status(400).entity(\"\\\"属性").append(field.getValue()).append("不能为空\\\"\").build(); }");
                }
                if (field.getType().equals("String")) {
                    putObjBodyBuilder.append("   if ($2.").append(field.getGetterName()).append("() != null && !java.util.regex.Pattern.compile(\"").append(field.getRegex().replaceAll("\\\\", "\\\\\\\\")).append("\").matcher($2.").append(field.getGetterName()).append("()).find()) {");
                    putObjBodyBuilder.append("      return javax.ws.rs.core.Response.status(400).entity(\"\\\"参数'").append(field.getValue()).append("'校验错误!\\\"\").build();}");
                }
            }
            putObjBodyBuilder.append("   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();");
            putObjBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            // selectByKey
            putObjBodyBuilder.append("   java.lang.String prepareSQL = \"select 1 from ").append(table.getValue()).append(" where ");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    putObjBodyBuilder.append(field.getValue()).append("=?;\";");
                }
            }
            putObjBodyBuilder.append("   System.out.println(prepareSQL);");
            putObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(1, $1.intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(1, $1);"); break;
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
            putObjBodyBuilder.append("   System.out.println(prepareSQL);");
            putObjBodyBuilder.append("   stmt = conn.prepareStatement(prepareSQL);");
            // stmt赋值
            var index = 1;
            for (Field field : fields) {
                if (!field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $2.").append(field.getGetterName()).append("().intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(").append(index).append(", $2.").append(field.getGetterName()).append("());"); break;
                    }
                    index = index + 1;
                }
            }
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $1.intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(").append(index).append(", $1);"); break;
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
            putObjBodyBuilder.append("   System.out.println(prepareSQL);");
            putObjBodyBuilder.append("   stmt = conn.prepareStatement(prepareSQL);");
            // stmt赋值
            index = 1;
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $1.intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(").append(index).append(", $1);"); break;
                    }
                } else {
                    switch (field.getType()) {
                        case "int" : putObjBodyBuilder.append("   stmt.setInt(").append(index).append(", $2.").append(field.getGetterName()).append("().intValue());"); break;
                        case "String" : putObjBodyBuilder.append("   stmt.setString(").append(index).append(", $2.").append(field.getGetterName()).append("());"); break;
                    }
                }
                index = index + 1;
            }
            putObjBodyBuilder.append("}");
            putObjBodyBuilder.append("   java.lang.String number = \"\" + stmt.executeUpdate();");
            putObjBodyBuilder.append("   conn.close();");
            putObjBodyBuilder.append("   return javax.ws.rs.core.Response.status(200).entity(number).build();}");

            var putObjBody = putObjBodyBuilder.toString();
            //System.out.println(putObjBody.replaceAll("   ", "\n   "));
            // 方法入参
            var args = new ArrayList<CtClass>();
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    try {
                        args.add(JavassistUtil.getCtClass(classPool, field.getType()));
                    } catch (NotFoundException e) {
                        // xml里配错了我也没啥办法，不是么
                        e.printStackTrace();
                    }
                }
            }
            try {
                args.add(classPool.get(fullPath));
                CtClass[] arg = new CtClass[args.size()];
                args.toArray(arg);
                this.createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl(), arg, putterService, putObjBody, "PUT");
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        });
        this.writeClassFile(putterService, putterClassName + ".class");
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
        JavassistUtil.addAnnotation(deleterServiceFile, deleterServiceConst, new String[] {"javax.ws.rs.Path", "org.springframework.stereotype.Component"}, new String[][]{{"value"}, {"value"}}, new MemberValue[][] {{new StringMemberValue("/", deleterServiceConst)}, {new StringMemberValue("", deleterServiceConst)}});

        // 创建obj对应的delete方法
        xmlLoader.getTables().forEach((name, table) -> {
            var className = table.getUrl().substring(0, 1).toUpperCase() + table.getUrl().substring(1);
            var fullPath = xmlLoader.getModelPackage() + "." + className;
            var fields = table.getFields();

            // 添加deleteObj方法
            var deleteObjBodyBuilder = new StringBuilder();
            deleteObjBodyBuilder.append("{");
            // 参数校验
            deleteObjBodyBuilder.append("   if ($1 == null) { return javax.ws.rs.core.Response.status(400).entity(\"\\\"未指明主键\\\"\").build(); }");
            deleteObjBodyBuilder.append("   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();");
            deleteObjBodyBuilder.append("   java.sql.Connection conn = pool.getConn(\"").append(table.getDatasource()).append("\");");
            // delete语句
            deleteObjBodyBuilder.append("   java.lang.String prepareSQL = \"delete from ").append(table.getValue()).append(" where ");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    deleteObjBodyBuilder.append(field.getValue()).append("=?;\";");
                }
            }
            deleteObjBodyBuilder.append("   System.out.println(prepareSQL);");
            deleteObjBodyBuilder.append("   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);");
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    switch (field.getType()) {
                        case "int" : deleteObjBodyBuilder.append("   stmt.setInt(1, $1.intValue());"); break;
                        case "String" : deleteObjBodyBuilder.append("   stmt.setString(1, $1);"); break;
                    }
                }
            }
            deleteObjBodyBuilder.append("   java.lang.String number = \"\" + stmt.executeUpdate();");
            deleteObjBodyBuilder.append("   conn.close();");
            deleteObjBodyBuilder.append("   return javax.ws.rs.core.Response.status(200).entity(number).build();}");

            var deleteObjBody = deleteObjBodyBuilder.toString();
            //System.out.println(deleteObjBody.replaceAll("   ", "\n   "));
            // 方法入参
            var args = new ArrayList<CtClass>();
            for (Field field : fields) {
                if (field.getValue().equals(table.getKey())) {
                    try {
                        args.add(JavassistUtil.getCtClass(classPool, field.getType()));
                    } catch (NotFoundException e) {
                        // xml里配错了我也没啥办法，不是么
                        e.printStackTrace();
                    }
                }
            }
            try {
                CtClass[] arg = new CtClass[args.size()];
                args.toArray(arg);
                this.createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl(), arg, deleterService, deleteObjBody, "DELETE");
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        });
        this.writeClassFile(deleterService, deleterClassName + ".class");
    }

    private void createGetterMethod(CtClass returnType, String url, CtClass[] parameters, CtClass declaring, String body, Table table) throws NotFoundException {
        var getterServiceFile = declaring.getClassFile();
        var getterServiceConst = getterServiceFile.getConstPool();
        CtMethod m = null;
        try {
            var mname = "get" + url.substring(0, 1).toUpperCase() + url.substring(1);
            m = new CtMethod(returnType, mname, parameters, declaring);
            m.setModifiers(Modifier.PUBLIC);
            // 方法内的处理逻辑
            //System.out.println(body.replaceAll("   ", "\n   "));
            m.setBody(body);
            declaring.addMethod(m);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }

        // 给参数增加注解 @QueryParam @DefaultValue    参考：https://www.cnblogs.com/coshaho/p/5105545.html
        // 涉及到tables， 暂时不封装到JavassistUtil了 = =
        var parameterAtrribute = new ParameterAnnotationsAttribute(getterServiceConst, ParameterAnnotationsAttribute.visibleTag);
        var paramArrays = new Annotation[parameters.length][2];
        var sqlArrays = new String[]{"pageSize", "page", "order", "orderType", "count"};
        var sqlDefaultArrays = new String[]{"" + table.getPageSize(), "1", table.getOrder(), table.getOrderType(), "false"};
        var pageFullName = xmlLoader.getModelPackage() + "." + table.getName().substring(0, 1).toUpperCase() + table.getName().substring(1) + "Page";
        for (int i = 0; i < parameters.length; i ++) {
            // 根据 parameters 和 table.fields 的长度判断是否为SQL分页查询属性
            if (i < table.getFields().size()) {
                var field = table.getFields().get(i);
                if (!returnType.equals(classPool.get(pageFullName)) && field.getValue().equals(table.getKey())) {
                    // @QueryParam
                    var f1Annot1 = new Annotation("javax.ws.rs.PathParam", getterServiceConst);
                    f1Annot1.addMemberValue("value", new StringMemberValue("key", getterServiceConst));
                    paramArrays[i][1] = f1Annot1;
                    // @DefaultValue
                    var f1Annot2 = new Annotation("javax.ws.rs.DefaultValue", getterServiceConst);
                    f1Annot2.addMemberValue("value", new StringMemberValue("", getterServiceConst));
                    paramArrays[i][0] = f1Annot2;
                } else {
                    // @QueryParam
                    var f1Annot1 = new Annotation("javax.ws.rs.QueryParam", getterServiceConst);
                    f1Annot1.addMemberValue("value", new StringMemberValue(field.getValue(), getterServiceConst));
                    paramArrays[i][1] = f1Annot1;
                    // @DefaultValue
                    var f1Annot2 = new Annotation("javax.ws.rs.DefaultValue", getterServiceConst);
                    f1Annot2.addMemberValue("value", new StringMemberValue("", getterServiceConst));
                    paramArrays[i][0] = f1Annot2;
                }
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
        jsonArrayMemberValue.setValue(new MemberValue[]{new StringMemberValue("application/json;charset=utf-8", getterServiceConst)});
        var memberValues = new MemberValue[][] {
                {}, {returnType.equals(classPool.get(pageFullName)) ? new StringMemberValue("/" + url, getterServiceConst) : new StringMemberValue("/" + url + "/{key:[A-Za-z0-9]+}", getterServiceConst)}, {jsonArrayMemberValue}, {jsonArrayMemberValue}
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

        // 给参数增加注解 @QueryParam @DefaultValue    参考：https://www.cnblogs.com/coshaho/p/5105545.html
        // 涉及到tables， 暂时不封装到JavassistUtil了 = =
        if (method.equals("DELETE")) {
            var parameterAtrribute = new ParameterAnnotationsAttribute(serviceConst, ParameterAnnotationsAttribute.visibleTag);
            var paramArrays = new Annotation[parameters.length][1];
            var annot1 = new Annotation("javax.ws.rs.PathParam", serviceConst);
            annot1.addMemberValue("value", new StringMemberValue("key", serviceConst));
            paramArrays[0][0] = annot1;
            parameterAtrribute.setAnnotations(paramArrays);
            if (m != null) m.getMethodInfo().addAttribute(parameterAtrribute);
        }
        if (method.equals("PUT")) {
            var parameterAtrribute = new ParameterAnnotationsAttribute(serviceConst, ParameterAnnotationsAttribute.visibleTag);
            var paramArrays = new Annotation[parameters.length][1];
            var annot1 = new Annotation("javax.ws.rs.PathParam", serviceConst);
            annot1.addMemberValue("value", new StringMemberValue("key", serviceConst));
            paramArrays[0][0] = annot1;
            var annot2 = new Annotation("javax.ws.rs.DefaultValue", serviceConst);
            annot2.addMemberValue("value", new StringMemberValue("", serviceConst));
            paramArrays[1][0] = annot2;
            parameterAtrribute.setAnnotations(paramArrays);
            if (m != null) m.getMethodInfo().addAttribute(parameterAtrribute);
        }


        // 给方法增加注解@${method} @Path("/${url}") @Consumes({"application/json"}) @Produces({"application/json"})
        var annotationClasses = new String[] {
                "javax.ws.rs." + method, "javax.ws.rs.Path", "javax.ws.rs.Consumes", "javax.ws.rs.Produces"
        };
        var memberNames = new String[][] {
                {}, {"value"}, {"value"}, {"value"}
        };
        var jsonArrayMemberValue = new ArrayMemberValue(new StringMemberValue("", serviceConst), serviceConst);
        jsonArrayMemberValue.setValue(new MemberValue[]{new StringMemberValue("application/json;charset=utf-8", serviceConst)});
        var memberValues = new MemberValue[][] {
                {}, {method.equals("POST") ? new StringMemberValue("/" + url, serviceConst) : new StringMemberValue("/" + url + "/{key:[A-Za-z0-9]+}", serviceConst)}, {jsonArrayMemberValue}, {jsonArrayMemberValue}
        };
        JavassistUtil.addAnnotation(serviceConst, m, annotationClasses, memberNames, memberValues);
    }

    private void writeClassFile(CtClass clazz, String className) throws CannotCompileException, IOException {
        var classFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath() + className);
        if (classFile.exists()) classFile.delete();
        clazz.writeFile(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
    }
}
