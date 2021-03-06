package cn.anayoo.sweetpotato

import java.io.File
import java.util.logging.{Level, Logger}

import scala.collection.JavaConverters._
import cn.anayoo.sweetpotato.model.{Field, Table}
import cn.anayoo.sweetpotato.util.JavassistUtil
import javassist._
import javassist.bytecode.{AnnotationsAttribute, ParameterAnnotationsAttribute}
import javassist.bytecode.annotation.{Annotation, ArrayMemberValue, MemberValue, StringMemberValue}

/**
  * @author anayoo
  * @param xml
  */
class RestCreater(xml: XmlLoader) {
  private val logger = Logger.getLogger(classOf[RestCreater].getName)
  private val classPool = new ClassPool(true)
  private val classLoader = this.getClass.getClassLoader

  def createModel: RestCreater = {
    logger.log(Level.INFO, "创建实体类中...")
    xml.getTables.values().stream().forEach(table => {
      val modelName = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase + table.getName.substring(1)
      val obj = classPool.makeClass(modelName)
      // 添加无参的构造体
      obj.addConstructor(JavassistUtil.createConstructor(obj, new Array[CtClass](0), Modifier.PUBLIC, "{}"))
      val constPool = obj.getClassFile.getConstPool
      table.getFields.stream().forEach(field => {
        val f1 = new CtField(JavassistUtil.getCtClass(obj.getClassPool, field.getType), field.getValue, obj)
        f1.setModifiers(Modifier.PRIVATE)

        // 给参数增加注解@JsonProperty("${value}")
        val annotationClasses = Array[String]("com.fasterxml.jackson.annotation.JsonProperty")
        val memberNames = Array[Array[String]](Array("value"))
        val memberValues = Array[Array[MemberValue]](Array(new StringMemberValue(field.getValue, constPool)))
        JavassistUtil.addAnnotation(constPool, f1, annotationClasses, memberNames, memberValues)

        obj.addField(f1)
        obj.addMethod(CtNewMethod.setter("set" + field.getValue.substring(0, 1).toUpperCase + field.getValue.substring(1), f1))
        obj.addMethod(CtNewMethod.getter("get" + field.getValue.substring(0, 1).toUpperCase + field.getValue.substring(1), f1))
      })
      obj.toClass(classLoader, null)
      writeClassFile(obj, modelName + ".class")

      val pageFullName = modelName + "Page"
      val objPage = classPool.makeClass(pageFullName)
      objPage.addConstructor(JavassistUtil.createConstructor(objPage, new Array[CtClass](0), Modifier.PUBLIC, "{}"))
      val f1 = new CtField(JavassistUtil.getCtClass(objPage.getClassPool, "java.util.List"), "data", objPage)
      f1.setModifiers(Modifier.PRIVATE)
      objPage.addField(f1)
      objPage.addMethod(CtNewMethod.setter("setData", f1))
      objPage.addMethod(CtNewMethod.getter("getData", f1))
      val f2 = new CtField(JavassistUtil.getCtClass(objPage.getClassPool, "cn.anayoo.sweetpotato.model.Setting"), "setting", objPage)
      f2.setModifiers(Modifier.PRIVATE)
      objPage.addField(f2)
      objPage.addMethod(CtNewMethod.setter("setSetting", f2))
      objPage.addMethod(CtNewMethod.getter("getSetting", f2))
      objPage.toClass(classLoader, null)
      writeClassFile(objPage, pageFullName + ".class")
      logger.log(Level.FINE, "已创建实体类: {}", pageFullName)
    })
    logger.log(Level.INFO, "创建实体类: ok")
    this
  }

  def createGetter: RestCreater = {
    // 创建一个GetterService类
    val getterService = classPool.makeClass(xml.getServicePackage + ".GetterService")
    getterService.addConstructor(JavassistUtil.createConstructor(getterService, new Array[CtClass](0), Modifier.PUBLIC, "{}"))
    // 给GetterService增加注解@Path("/")
    JavassistUtil.addAnnotation(getterService.getClassFile,
      Array[String]("javax.ws.rs.Path", "org.springframework.stereotype.Component"),
      Array[Array[String]](Array("value"), Array("value")),
      Array[Array[MemberValue]](Array(new StringMemberValue("/basic", getterService.getClassFile.getConstPool)), Array(new StringMemberValue("", getterService.getClassFile.getConstPool))))
    xml.getTables.values().stream().forEach(table => {
      val model = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase() + table.getName.substring(1)
      val modelPage = model + "Page"
      val fields = for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)
      val parameters = table.getFields.asScala.map(field => JavassistUtil.getCtClass(classPool, field.getType)).toArray
      val args = table.getFields.asScala.map(_.getValue).mkString(", ")
      val wheres = spellWhere(fields)
      val stmts = spellStmt(fields)
      val rs = spellRs(fields)
      val body =
        s"""{
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   java.lang.String dbType = pool.getDatasourceType("${table.getDatasource}");
           |   java.lang.StringBuilder where = new java.lang.StringBuilder();
           |   boolean isNull = true;
           |   $wheres
           |   if (isNull) return null;
           |   java.lang.String prepareSQL = "";
           |   if (dbType.equals("mysql")) prepareSQL = "select $args from ${table.getValue} where " + where + ";";
           |   if (dbType.equals("oracle")) prepareSQL = "select $args from ${table.getValue} where " + where;
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   int i = 1;
           |   $stmts
           |   java.sql.ResultSet rs = stmt.executeQuery();
           |   $model arg = new $model();
           |   rs.next();
           |   if (rs.getRow() > 0) {
           |      $rs
           |   } else arg = null;
           |   conn.close();
           |   return javax.ws.rs.core.Response.status(200).entity(arg).build();
           |}
         """.stripMargin
      createGetterMethod(xml, classPool.get("javax.ws.rs.core.Response"), table.getUrl, parameters, getterService, body, table)

      // GETS
      val getsFields = table.getFields.asScala.map("  fields.add(\"" + _.getValue + "\");").mkString("\n")
      val body2 =
        s"""{
           |  java.util.List/*<String>*/ fields = new java.util.ArrayList();
           |$getsFields
           |  String order = "${table.getOrder} ${table.getOrderType}";
           |  String resOrder = "${table.getOrder}";
           |  String resOrderType = "${table.getOrderType}";
           |  int page = 1;
           |  int pageSize = ${table.getPageSize};
           |  boolean count = false;
           |  String wheres = "";
           |  cn.anayoo.sweetpotato.model.QueryForm queryForm = cn.anayoo.sweetpotato.util.QueryUtil.formatQuery($$2.getQueryParameters(), fields);
           |  if (!queryForm.getPage().equals("")) page = Integer.parseInt(queryForm.getPage());
           |  if (!queryForm.getPageSize().equals("")) pageSize = Integer.parseInt(queryForm.getPageSize());
           |  if (queryForm.getOrder().length != 0) {
           |    order = "";
           |    resOrder = "";
           |    resOrderType = "";
           |    String[] orders = queryForm.getOrder();
           |    String[] orderTypes = queryForm.getOrderType();
           |    for (int i = 0; i < orders.length; i ++) {
           |      order = order + orders[i] + " ";
           |      resOrder = resOrder + orders[i];
           |      if (orderTypes.length >= i + 1) {
           |        order = order + orderTypes[i];
           |        resOrderType = resOrderType + orderTypes[i];
           |      } else {
           |        order = order + "asc";
           |        resOrderType = resOrderType + "asc";
           |      }
           |      if (i != orders.length - 1) {
           |        order = order + ", ";
           |        resOrder = resOrder + ", ";
           |        resOrderType = resOrderType + ", ";
           |      }
           |    }
           |  } else if (queryForm.getOrderType().length != 0) {
           |    order = order.substring(0, order.indexOf(" ") + 1) + queryForm.getOrderType()[0];
           |    resOrderType = queryForm.getOrderType()[0];
           |  }
           |  if (!queryForm.getCount().equals("") && queryForm.getCount().equals("true")) count = true;
           |
           |  java.util.List/*<cn.anayoo.sweetpotato.model.Query>*/ querys = queryForm.getQueryList();
           |  java.util.List/*<String>*/ args = new java.util.ArrayList();
           |  for (int i = 0; i < querys.size(); i ++) {
           |    if (i == 0) {
           |      wheres = wheres + " where";
           |    }
           |    cn.anayoo.sweetpotato.model.Query query = (cn.anayoo.sweetpotato.model.Query) querys.get(i);
           |    if (query.getConnecter().equals("or") && i == 0) {
           |      wheres = wheres + " (" + query.getQuery();
           |    } else if (query.getConnecter().equals("and") && i == 0) {
           |      wheres = wheres + " " + query.getQuery();
           |    } else if (query.getConnecter().equals("or") && ((cn.anayoo.sweetpotato.model.Query) querys.get(i - 1)).getConnecter().equals("and")) {
           |      wheres = wheres + " " + ((cn.anayoo.sweetpotato.model.Query) querys.get(i - 1)).getConnecter() + " (" + query.getQuery();
           |    } else if (query.getConnecter().equals("and") && ((cn.anayoo.sweetpotato.model.Query) querys.get(i - 1)).getConnecter().equals("or")) {
           |      wheres = wheres + " " + ((cn.anayoo.sweetpotato.model.Query) querys.get(i - 1)).getConnecter() + " " + query.getQuery() + ")";
           |    } else {
           |      wheres = wheres + " " + ((cn.anayoo.sweetpotato.model.Query) querys.get(i - 1)).getConnecter() + " " + query.getQuery();
           |    }
           |    for (int j = 0; j < query.getValues().size(); j ++) {
           |      args.add(query.getValues().get(j));
           |    }
           |  }
           |
           |  cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |  java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |  java.lang.String dbType = pool.getDatasourceType("${table.getDatasource}");
           |  int limitStart = pageSize * (page - 1);
           |  String prepareSQL = "";
           |  if (dbType.equals("mysql")) prepareSQL = "select $args from ${table.getValue}" + wheres + " order by " + order + " limit " + limitStart + ", " + pageSize + ";";
           |  if (dbType.equals("oracle")) prepareSQL = "select $args from ${table.getValue}" + wheres + " order by " + order + " offset " + limitStart + " rows fetch next " + pageSize + " rows only";
           |  java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |  for (int i = 0; i < args.size(); i ++) {
           |    stmt.setString(i + 1, (String) args.get(i));
           |  }
           |  java.sql.ResultSet rs = stmt.executeQuery();
           |  java.util.List objArgs = new java.util.ArrayList();
           |  while(rs.next()) {
           |    $model arg = new $model();
           |    $rs
           |    objArgs.add(arg);
           |  }
           |  $modelPage modelPage = new $modelPage();
           |  modelPage.setData(objArgs);
           |  cn.anayoo.sweetpotato.model.Setting setting = new cn.anayoo.sweetpotato.model.Setting();
           |  setting.setPageSize(pageSize);
           |  setting.setPage(page);
           |  setting.setOrder(resOrder);
           |  setting.setOrderType(resOrderType);
           |  if (count) {
           |    if (dbType.equals("mysql")) prepareSQL = "select count(1) from ${table.getValue}" + wheres + ";";
           |    if (dbType.equals("oracle")) prepareSQL = "select count(1) from ${table.getValue}" + wheres;
           |    stmt = conn.prepareStatement(prepareSQL);
           |    for (int i = 0; i < args.size(); i ++) {
           |      stmt.setString(i + 1, (String) args.get(i));
           |    }
           |    rs = stmt.executeQuery();
           |    if (rs.next()) {
           |      setting.setCount(java.lang.Integer.valueOf(rs.getInt(1)));
           |    }
           |  }
           |  conn.close();
           |  modelPage.setSetting(setting);
           |  return javax.ws.rs.core.Response.status(200).entity(modelPage).build();
           |}
         """.stripMargin
//        s"""{
//           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
//           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
//           |   java.lang.String dbType = pool.getDatasourceType("${table.getDatasource}");
//           |   java.lang.StringBuilder where = new java.lang.StringBuilder();
//           |   boolean isNull = true;
//           |   $wheres
//           |   java.lang.String whereStr = isNull ? "" : " where " + where.toString();
//           |   int limitStart = $$${fields.size + 1} * ($$${fields.size + 2} - 1);
//           |   java.lang.String prepareSQL = "";
//           |   if (dbType.equals("mysql")) prepareSQL = "select $args from ${table.getValue}" + whereStr + " order by " + $$${fields.size + 3} + " " + $$${fields.size + 4} + " limit " + limitStart + ", " + $$${fields.size + 1} + ";";
//           |   if (dbType.equals("oracle")) prepareSQL = "select $args from ${table.getValue}" + whereStr + " order by " + $$${fields.size + 3} + " " + $$${fields.size + 4} + " offset " + limitStart + " rows fetch next " + $$${fields.size + 1} + " rows only";
//           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
//           |   int i = 1;
//           |   $stmts
//           |   java.sql.ResultSet rs = stmt.executeQuery();
//           |   java.util.List args = new java.util.ArrayList();
//           |   while(rs.next()) {
//           |      $model arg = new $model();
//           |      $rs
//           |      args.add(arg);
//           |   }
//           |   $modelPage page = new $modelPage();
//           |   page.setData(args);
//           |   cn.anayoo.sweetpotato.model.Setting setting = new cn.anayoo.sweetpotato.model.Setting();
//           |   setting.setPageSize($$${fields.size + 1});
//           |   setting.setPage($$${fields.size + 2});
//           |   setting.setOrder($$${fields.size + 3});
//           |   setting.setOrderType($$${fields.size + 4});
//           |   if ($$${fields.size + 5}) {
//           |      if (dbType.equals("mysql")) prepareSQL = "select count(1) from ${table.getValue} " + whereStr + ";";
//           |      if (dbType.equals("oracle")) prepareSQL = "select count(1) from ${table.getValue} " + whereStr;
//           |      stmt = conn.prepareStatement(prepareSQL);
//           |      i = 1;
//           |      $stmts
//           |      rs = stmt.executeQuery();
//           |      if (rs.next()) {
//           |         setting.setCount(java.lang.Integer.valueOf(rs.getInt(1)));
//           |      }
//           |   }
//           |   conn.close();
//           |   page.setSetting(setting);
//           |   return page;
//           |}
//         """.stripMargin
      createGetsMethod(xml, classPool.get("javax.ws.rs.core.Response"), table.getGets, getterService, body2, table)
    })
    writeClassFile(getterService, "GetterService.class")
    this
  }

  def createPoster: RestCreater = {
    val posterService = classPool.makeClass(xml.getServicePackage + ".PosterService")
    posterService.addConstructor(JavassistUtil.createConstructor(posterService, new Array[CtClass](0), Modifier.PUBLIC, "{}"))
    JavassistUtil.addAnnotation(posterService.getClassFile,
      Array[String]("javax.ws.rs.Path", "org.springframework.stereotype.Component"),
      Array[Array[String]](Array("value"), Array("value")),
      Array[Array[MemberValue]](Array(new StringMemberValue("/basic", posterService.getClassFile.getConstPool)), Array(new StringMemberValue("", posterService.getClassFile.getConstPool))))
    xml.getTables.values().stream().forEach(table => {
      val model = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase() + table.getName.substring(1)
      val fieldsWithoutKey = (for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)).filterNot(_._2.getValue.equals(table.getKey))
      val args = fieldsWithoutKey.map(_._2.getValue).mkString(", ")
      val values = fieldsWithoutKey.map(_ => "?").mkString(", ")
      val verify = spellVerify(fieldsWithoutKey, 1)
      val stmt = spellStmt2(fieldsWithoutKey, 1)
      val body =
        s"""{
           |   $verify
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   java.lang.String dbType = pool.getDatasourceType("${table.getDatasource}");
           |   java.lang.String prepareSQL = "";
           |   if (dbType.equals("mysql")) prepareSQL = "insert into ${table.getValue} ($args) values ($values);";
           |   if (dbType.equals("oracle")) prepareSQL = "insert into ${table.getValue} ($args) values ($values)";
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   $stmt
           |   java.lang.String number = "" + stmt.executeUpdate();
           |   conn.close();
           |   return javax.ws.rs.core.Response.status(200).entity(number).build();
           |}
         """.stripMargin
      createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl, Array[CtClass](classPool.get(model)), posterService, body, "POST", table)
    })
    writeClassFile(posterService, "PosterService.class")
    this
  }

  def createPutter: RestCreater = {
    val putterService = classPool.makeClass(xml.getServicePackage + ".PutterService")
    putterService.addConstructor(JavassistUtil.createConstructor(putterService, new Array[CtClass](0), Modifier.PUBLIC, "{}"))
    JavassistUtil.addAnnotation(putterService.getClassFile,
      Array[String]("javax.ws.rs.Path", "org.springframework.stereotype.Component"),
      Array[Array[String]](Array("value"), Array("value")),
      Array[Array[MemberValue]](Array(new StringMemberValue("/basic", putterService.getClassFile.getConstPool)), Array(new StringMemberValue("", putterService.getClassFile.getConstPool))))
    xml.getTables.values().stream().forEach(table => {
      val model = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase() + table.getName.substring(1)
      val fieldsWithoutKey = table.getFields.asScala.filterNot(_.isPrimaryKey).toList
      val fieldsWithoutKeySeq = for (i <- fieldsWithoutKey.indices) yield i + 1 -> fieldsWithoutKey.lift(i).get
      val fieldsWithKey = (for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)).filter(_._2.isPrimaryKey)
      val fields = fieldsWithoutKeySeq :++ fieldsWithKey.map(f => (fieldsWithoutKeySeq.size + f._1, f._2))
      val fields2 = for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)
      val args = fields.map(_._2.getValue).mkString(", ")
      val values = fields.map(_ => "?").mkString(", ")
      val nullif = spellNullIf(fieldsWithKey)
      val verify = spellVerify(fieldsWithoutKeySeq, table.getFields.size() + 1)
      val wheres = spellWhere(fields2)
      val whereWithKey = spellWhere2(fieldsWithKey)
      val setWithoutKey = spellSet(fieldsWithoutKeySeq)
      val stmtWithKey = spellStmt3(fieldsWithKey)
      val stmt = spellStmt2(fieldsWithoutKeySeq, table.getFields.size() + 1) + spellStmt3(Seq.empty :++ fieldsWithKey.map(f => {(fieldsWithoutKey.size + f._1, f._2)}))
      val stmt2 = spellStmt(fieldsWithoutKeySeq)
      val body =
        s"""{
           |   if ($nullif) return javax.ws.rs.core.Response.status(400).entity("\\"未指明主键\\"").build();
           |   $verify
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   int number = 0;
           |   try {
           |      conn.setAutoCommit(false);
           |      java.lang.String dbType = pool.getDatasourceType("${table.getDatasource}");
           |      java.lang.String prepareSQL = "";
           |      if (dbType.equals("mysql")) prepareSQL = "select 1 from ${table.getValue} where $whereWithKey for update;";
           |      if (dbType.equals("oracle")) prepareSQL = "select 1 from ${table.getValue} where $whereWithKey for update";
           |      java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |      $stmtWithKey
           |      java.sql.ResultSet rs = stmt.executeQuery();
           |      rs.next();
           |      number = rs.getRow();
           |      java.lang.StringBuilder where = new java.lang.StringBuilder();
           |      boolean isNull = true;
           |      $wheres
           |      if (dbType.equals("mysql")) if (number > 0) prepareSQL = "update ${table.getValue} set $setWithoutKey where " + where + ";"; else prepareSQL = "insert into ${table.getValue} ($args) values ($values);";
           |      if (dbType.equals("oracle")) if (number > 0) prepareSQL = "update ${table.getValue} set $setWithoutKey where " + where; else prepareSQL = "insert into ${table.getValue} ($args) values ($values)";
           |      stmt = conn.prepareStatement(prepareSQL);
           |      $stmt
           |      int i = ${fields2.size + 1};
           |      if (number > 0) {
           |         $stmt2
           |      }
           |      number = stmt.executeUpdate();
           |      conn.commit();
           |   } catch (Exception e) {
           |      e.printStackTrace();
           |      conn.rollback();
           |      number = 0;
           |   }
           |   conn.close();
           |   return javax.ws.rs.core.Response.status(200).entity("" + number).build();
           |}
         """.stripMargin
      val arg = fields2.map(_._2).map(f => JavassistUtil.getCtClass(classPool, f.getType)) :+ classPool.get(model)
      createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl, arg.toArray, putterService, body, "PUT", table)
    })
    writeClassFile(putterService, "PutterService.class")
    this
  }

  def createDeleter: RestCreater = {
    val deleterService = classPool.makeClass(xml.getServicePackage + ".DeleterService")
    deleterService.addConstructor(JavassistUtil.createConstructor(deleterService, new Array[CtClass](0), Modifier.PUBLIC, "{}"))
    JavassistUtil.addAnnotation(deleterService.getClassFile,
      Array[String]("javax.ws.rs.Path", "org.springframework.stereotype.Component"),
      Array[Array[String]](Array("value"), Array("value")),
      Array[Array[MemberValue]](Array(new StringMemberValue("/basic", deleterService.getClassFile.getConstPool)), Array(new StringMemberValue("", deleterService.getClassFile.getConstPool))))
    xml.getTables.values().stream().forEach(table => {
      val model = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase() + table.getName.substring(1)
      val fieldsWithKey = (for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)).filter(_._2.isPrimaryKey)
      val nullif = spellNullIf(fieldsWithKey)
      val whereWithKey = spellWhere2(fieldsWithKey)
      val stmtWithKey = spellStmt3(fieldsWithKey)
      val body =
        s"""{
           |   if ($nullif) return javax.ws.rs.core.Response.status(400).entity("\\"未指明主键\\"").build();
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   java.lang.String dbType = pool.getDatasourceType("${table.getDatasource}");
           |   java.lang.String prepareSQL = "";
           |   if (dbType.equals("mysql")) prepareSQL = "delete from ${table.getValue} where $whereWithKey;";
           |   if (dbType.equals("oracle")) prepareSQL = "delete from ${table.getValue} where $whereWithKey";
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   $stmtWithKey
           |   java.lang.String number = "" + stmt.executeUpdate();
           |   conn.close();
           |   return javax.ws.rs.core.Response.status(200).entity(number).build();
           |}
         """.stripMargin
      val arg = fieldsWithKey.map(_._2).map(f => JavassistUtil.getCtClass(classPool, f.getType))
      createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl, arg.toArray, deleterService, body, "DELETE", table)
    })
    writeClassFile(deleterService, "DeleterService.class")
    this
  }

  val spellNullIf: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      s"""$$${b._1 + 1} == null"""
    }).mkString(" || ")
  }

  val spellVerify: (Seq[(Int, Field)], Int) => String = (a:Seq[(Int, Field)], i: Int) => {
    a.map(b => {
      var verify = if (!b._2.isAllowNone)
        s"""if ($$$i.${b._2.getGetterName}() == null) return javax.ws.rs.core.Response.status(400).entity("\\"属性${b._2.getValue}不能为空\\"").build();
         """.stripMargin else ""
      b._2.getType match {
        case "double" => verify += ""
        case "number" => verify += ""
        case "string" => verify +=
          s"""if ($$$i.${b._2.getGetterName}() != null && !java.util.regex.Pattern.compile("${b._2.getRegex.replaceAll("\\\\", "\\\\\\\\")}").matcher($$$i.${b._2.getGetterName}()).find()) return javax.ws.rs.core.Response.status(400).entity("\\"参数${b._2.getValue}校验错误\\"").build();
           """.stripMargin
        case "binary" => verify += ""
      }
      verify
    }).mkString
  }

  val spellWhere: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      b._2.getType match {
        case "double" =>
          s"""if ($$${b._1 + 1} != null) {
             |   if (!isNull) where.append(" and ");
             |   where.append("${b._2.getValue}=?");
             |   isNull = false;
             |}
            """.stripMargin
        case "number" =>
          s"""if ($$${b._1 + 1} != null) {
             |   if (!isNull) where.append(" and ");
             |   where.append("${b._2.getValue}=?");
             |   isNull = false;
             |}
            """.stripMargin
        case "string" =>
          s"""if (!$$${b._1 + 1}.equals("")) {
             |   if (!isNull) where.append(" and ");
             |   where.append("${b._2.getValue}=?");
             |   isNull = false;
             |}
            """.stripMargin
        case "binary" =>
          s"""if (!$$${b._1 + 1}.equals("")) {
             |   if (!isNull) where.append(" and ");
             |   where.append("${b._2.getValue}=?");
             |   isNull = false;
             |}
            """.stripMargin
      }
    }).mkString
  }
  val spellWhere2: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      s"""${b._2.getValue}=?"""
    }).mkString(" and ")
  }
  val spellSet: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      s"""${b._2.getValue}=?"""
    }).mkString(", ")
  }
  val spellStmt: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    println(a)
    a.map(b => {
      b._2.getType match {
        case "double" =>
          s"""if ($$${b._2.getIndex + 1} != null) {
             |  stmt.setDouble(i, $$${b._2.getIndex + 1}.doubleValue());
             |  i ++;
             |}
             """.stripMargin
        case "number" =>
          s"""if ($$${b._2.getIndex + 1} != null) {
             |  stmt.setBigDecimal(i, $$${b._2.getIndex + 1});
             |  i ++;
             |}
             """.stripMargin
        case "string" =>
          s"""if (!$$${b._2.getIndex + 1}.equals("")) {
             |  stmt.setString(i, $$${b._2.getIndex + 1});
             |  i ++;
             |}
             """.stripMargin
        case "binary" =>
          s"""if (!$$${b._2.getIndex + 1}.equals("")) {
             |  try {
             |    stmt.setBytes(i, java.util.Base64.getDecoder().decode($$${b._2.getIndex + 1}));
             |    i ++;
             |  } catch (IllegalArgumentException e) {
             |    return javax.ws.rs.core.Response.status(400).entity("\\"错误的二进制类型\\"").build();
             |  }
             |}
             """.stripMargin
      }
    }).mkString
  }
  val spellStmt2: (Seq[(Int, Field)], Int) => String = (a:Seq[(Int, Field)], i: Int) => {
    a.map(b => {
      b._2.getType match {
        case "double" =>
          s"""if ($$$i.${b._2.getGetterName}() == null) stmt.setNull(${b._1}, java.sql.Types.FLOAT); else stmt.setDouble(${b._1}, $$$i.${b._2.getGetterName}().doubleValue());
           """.stripMargin
        case "number" =>
          s"""if ($$$i.${b._2.getGetterName}() == null) stmt.setNull(${b._1}, java.sql.Types.TINYINT); else stmt.setBigDecimal(${b._1}, $$$i.${b._2.getGetterName}());
           """.stripMargin
        case "string" =>
          s"""if ($$$i.${b._2.getGetterName}() == null) stmt.setNull(${b._1}, java.sql.Types.VARCHAR); else stmt.setString(${b._1}, $$$i.${b._2.getGetterName}());
           """.stripMargin
        case "binary" =>
          s"""if ($$$i.${b._2.getGetterName}() == null) stmt.setNull(${b._1}, java.sql.Types.BIT); else {
             |  try {
             |    stmt.setBytes(${b._1}, java.util.Base64.getDecoder().decode($$$i.${b._2.getGetterName}()));
             |  } catch (IllegalArgumentException e) {
             |    return javax.ws.rs.core.Response.status(400).entity("\\"错误的二进制类型\\"").build();
             |  }
             |}
           """.stripMargin
      }
    }).mkString
  }
  val spellStmt3: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      b._2.getType match {
        case "double" => s"""if ($$${b._2.getIndex + 1} == null) stmt.setNull(${b._1 + 1}, java.sql.Types.FLOAT); else stmt.setDouble(${b._1 + 1}, $$${b._2.getIndex + 1}.doubleValue());""".stripMargin
        case "number" => s"""if ($$${b._2.getIndex + 1} == null) stmt.setNull(${b._1 + 1}, java.sql.Types.TINYINT); else stmt.setBigDecimal(${b._1 + 1}, $$${b._2.getIndex + 1});""".stripMargin
        case "string" => s"""if ($$${b._2.getIndex + 1} == null) stmt.setNull(${b._1 + 1}, java.sql.Types.VARCHAR); else stmt.setString(${b._1 + 1}, $$${b._2.getIndex + 1});""".stripMargin
        case "binary" =>
          s"""if ($$${b._2.getIndex + 1} == null) stmt.setNull(${b._1 + 1}, java.sql.Types.BIT); else {
             |  try {
             |    stmt.setBytes(${b._1 + 1}, java.util.Base64.getDecoder().decode($$${b._2.getIndex + 1}));
             |  } catch (IllegalArgumentException e) {
             |    return javax.ws.rs.core.Response.status(400).entity("\\"错误的二进制类型\\"").build();
             |  }
             |}""".stripMargin
      }
    }).mkString("\n")
  }
  val spellRs: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      b._2.getType match {
        case "double" => s"""arg.${b._2.getSetterName}(new Double(rs.getDouble("${b._2.getValue}")));"""
        case "number" => s"""arg.${b._2.getSetterName}(rs.getBigDecimal("${b._2.getValue}"));"""
        case "string" => s"""arg.${b._2.getSetterName}(rs.getString("${b._2.getValue}"));"""
        case "binary" => s"""arg.${b._2.getSetterName}(java.util.Base64.getEncoder().encodeToString(rs.getBytes("${b._2.getValue}")));"""
      }
    }).mkString
  }

  private def createGetterMethod(xml: XmlLoader, returnType: CtClass, url: String, parameters: Array[CtClass], declaring: CtClass, body: String, table: Table): Unit = {
    val getterServiceFile = declaring.getClassFile
    val getterServiceConst = getterServiceFile.getConstPool
    val mname = "get" + url.substring(0, 1).toUpperCase + url.substring(1)
    val m = new CtMethod(returnType, mname, parameters, declaring)
    m.setModifiers(Modifier.PUBLIC)
    // 方法内的处理逻辑
    m.setBody(body)
    declaring.addMethod(m)
    // 给参数增加注解 @QueryParam @DefaultValue    参考：https://www.cnblogs.com/coshaho/p/5105545.html
    // 涉及到tables， 暂时不封装到JavassistUtil了 = =
    val parameterAtrribute = new ParameterAnnotationsAttribute(getterServiceConst, ParameterAnnotationsAttribute.visibleTag)
    val paramArrays = new Array[Array[Annotation]](parameters.length)
    val sqlArrays = Array[String]("pageSize", "page", "order", "orderType", "count")
    val sqlDefaultArrays = Array[String]("" + table.getPageSize, "1", table.getOrder, table.getOrderType, "false")
    val pageFullName = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase + table.getName.substring(1) + "Page"
    var uri = ""
    var i = 0
    while ( {
      i < parameters.length
    }) { // 根据 parameters 和 table.fields 的长度判断是否为SQL分页查询属性
      if (i < table.getFields.size) {
        val field = table.getFields.get(i)
        if (!(returnType == classPool.get(pageFullName)) && field.isPrimaryKey) { // @QueryParam
          uri = uri + s"""/{key${i}}"""
          val f1Annot1 = new Annotation("javax.ws.rs.PathParam", getterServiceConst)
          f1Annot1.addMemberValue("value", new StringMemberValue("key" + i, getterServiceConst))
          paramArrays(i) = new Array[Annotation](2)
          paramArrays(i)(1) = f1Annot1
          // @DefaultValue
          val f1Annot2 = new Annotation("javax.ws.rs.DefaultValue", getterServiceConst)
          f1Annot2.addMemberValue("value", new StringMemberValue("", getterServiceConst))
          paramArrays(i)(0) = f1Annot2
        }
        else {
          val f1Annot1 = new Annotation("javax.ws.rs.QueryParam", getterServiceConst)
          f1Annot1.addMemberValue("value", new StringMemberValue(field.getValue, getterServiceConst))
          paramArrays(i) = new Array[Annotation](2)
          paramArrays(i)(1) = f1Annot1
          val f1Annot2 = new Annotation("javax.ws.rs.DefaultValue", getterServiceConst)
          f1Annot2.addMemberValue("value", new StringMemberValue("", getterServiceConst))
          paramArrays(i)(0) = f1Annot2
        }
      }
      else {
        val f1Annot1 = new Annotation("javax.ws.rs.QueryParam", getterServiceConst)
        f1Annot1.addMemberValue("value", new StringMemberValue(sqlArrays(i - table.getFields.size), getterServiceConst))
        paramArrays(i) = new Array[Annotation](2)
        paramArrays(i)(1) = f1Annot1
        val f1Annot2 = new Annotation("javax.ws.rs.DefaultValue", getterServiceConst)
        f1Annot2.addMemberValue("value", new StringMemberValue(sqlDefaultArrays(i - table.getFields.size), getterServiceConst))
        paramArrays(i)(0) = f1Annot2
      }
      i += 1; i -1
    }
    parameterAtrribute.setAnnotations(paramArrays)
    if (m != null) m.getMethodInfo.addAttribute(parameterAtrribute)
    // 给方法增加注解@GET @Path("/$url") @Consumes({"application/json"}) @Produces({"application/json"})
    val annotationClasses = Array[String]("javax.ws.rs.GET", "javax.ws.rs.Path", "javax.ws.rs.Consumes", "javax.ws.rs.Produces")
    val memberNames = Array[Array[String]](Array(), Array("value"), Array("value"), Array("value"))
    val jsonArrayMemberValue = new ArrayMemberValue(new StringMemberValue("", getterServiceConst), getterServiceConst)
    jsonArrayMemberValue.setValue(Array[MemberValue](new StringMemberValue("application/json;charset=utf-8", getterServiceConst)))
    val memberValues = Array[Array[MemberValue]](Array(), Array(if (returnType == classPool.get(pageFullName)) new StringMemberValue("/" + url, getterServiceConst)
    else new StringMemberValue("/" + url + uri, getterServiceConst)), Array(jsonArrayMemberValue), Array(jsonArrayMemberValue))
    JavassistUtil.addAnnotation(getterServiceConst, m, annotationClasses, memberNames, memberValues)
  }

  private def createGetsMethod(xml: XmlLoader, returnType: CtClass, url: String, declaring: CtClass, body: String, table: Table): Unit = {
    val getterServiceFile = declaring.getClassFile
    val getterServiceConst = getterServiceFile.getConstPool
    val mname = "get" + url.substring(0, 1).toUpperCase + url.substring(1)
    val parameters = Array[CtClass](JavassistUtil.getCtClass(classPool, "javax.ws.rs.core.Request"), JavassistUtil.getCtClass(classPool, "javax.ws.rs.core.UriInfo"))
    val m = new CtMethod(returnType, mname, parameters, declaring)
    m.setModifiers(Modifier.PUBLIC)
    // 方法内的处理逻辑
    m.setBody(body)
    declaring.addMethod(m)
    // 给参数增加注解 @Context
    val parameterAtrribute = new ParameterAnnotationsAttribute(getterServiceConst, ParameterAnnotationsAttribute.visibleTag)
    val paramArrays = new Array[Array[Annotation]](parameters.length)
    val sqlArrays = Array[String]("pageSize", "page", "order", "orderType", "count")
    val sqlDefaultArrays = Array[String]("" + table.getPageSize, "1", table.getOrder, table.getOrderType, "false")
    val pageFullName = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase + table.getName.substring(1) + "Page"
    paramArrays(0) = new Array[Annotation](1)
    paramArrays(0)(0) = new Annotation("javax.ws.rs.core.Context", getterServiceConst)
    paramArrays(1) = new Array[Annotation](1)
    paramArrays(1)(0) = new Annotation("javax.ws.rs.core.Context", getterServiceConst)

    parameterAtrribute.setAnnotations(paramArrays)
    if (m != null) m.getMethodInfo.addAttribute(parameterAtrribute)
    // 给方法增加注解@GET @Path("/$url") @Consumes({"application/json"}) @Produces({"application/json"})
    val annotationClasses = Array[String]("javax.ws.rs.GET", "javax.ws.rs.Path", "javax.ws.rs.Consumes", "javax.ws.rs.Produces")
    val memberNames = Array[Array[String]](Array(), Array("value"), Array("value"), Array("value"))
    val jsonArrayMemberValue = new ArrayMemberValue(new StringMemberValue("", getterServiceConst), getterServiceConst)
    jsonArrayMemberValue.setValue(Array[MemberValue](new StringMemberValue("application/json;charset=utf-8", getterServiceConst)))
    val memberValues = Array[Array[MemberValue]](Array(), Array(new StringMemberValue("/" + url, getterServiceConst)), Array(jsonArrayMemberValue), Array(jsonArrayMemberValue))
    JavassistUtil.addAnnotation(getterServiceConst, m, annotationClasses, memberNames, memberValues)
  }

  private def createOtherMethod(returnType: CtClass, url: String, parameters: Array[CtClass], declaring: CtClass, body: String, method: String, table: Table): Unit = {
    val serviceFile = declaring.getClassFile
    val serviceConst = serviceFile.getConstPool
    val mname = method.toLowerCase + url.substring(0, 1).toUpperCase + url.substring(1)
    val m = new CtMethod(returnType, mname, parameters, declaring)
    m.setModifiers(Modifier.PUBLIC)
    // 方法内的处理逻辑
    m.setBody(body)
    declaring.addMethod(m)
    // 给参数增加注解 @QueryParam @DefaultValue    参考：https://www.cnblogs.com/coshaho/p/5105545.html
    // 涉及到tables， 暂时不封装到JavassistUtil了 = =
    var uri = ""
    if (method == "DELETE") {
      val parameterAtrribute = new ParameterAnnotationsAttribute(serviceConst, ParameterAnnotationsAttribute.visibleTag)
      val paramArrays = new Array[Array[Annotation]](parameters.length)
      for (i <- 0 until table.getFields.size()) {
        if (table.getFields.get(i).isPrimaryKey) {
          uri = uri + s"""/{key${i}}"""
          val annot1 = new Annotation("javax.ws.rs.PathParam", serviceConst)
          annot1.addMemberValue("value", new StringMemberValue("key" + i, serviceConst))
          paramArrays(i) = new Array[Annotation](1)
          paramArrays(i)(0) = annot1
        }
      }
      parameterAtrribute.setAnnotations(paramArrays)
      if (m != null) m.getMethodInfo.addAttribute(parameterAtrribute)
    }
    if (method == "PUT") {
      val parameterAtrribute = new ParameterAnnotationsAttribute(serviceConst, ParameterAnnotationsAttribute.visibleTag)
      val paramArrays = new Array[Array[Annotation]](parameters.length)
      for (i <- 0 until table.getFields.size()) {
        if (table.getFields.get(i).isPrimaryKey) {
          uri = uri + s"""/{key${i}}"""
          val annot1 = new Annotation("javax.ws.rs.PathParam", serviceConst)
          annot1.addMemberValue("value", new StringMemberValue("key" + i, serviceConst))
          paramArrays(i) = new Array[Annotation](1)
          paramArrays(i)(0) = annot1
        } else {
          val field = table.getFields.get(i)
          val annot1 = new Annotation("javax.ws.rs.QueryParam", serviceConst)
          annot1.addMemberValue("value", new StringMemberValue(field.getValue, serviceConst))
          paramArrays(i) = new Array[Annotation](2)
          paramArrays(i)(1) = annot1
          val annot2 = new Annotation("javax.ws.rs.DefaultValue", serviceConst)
          annot2.addMemberValue("value", new StringMemberValue("", serviceConst))
          paramArrays(i)(0) = annot2
        }
      }
      val annot = new Annotation("javax.ws.rs.DefaultValue", serviceConst)
      annot.addMemberValue("value", new StringMemberValue("", serviceConst))
      paramArrays(table.getFields.size()) = new Array[Annotation](1)
      paramArrays(table.getFields.size())(0) = annot
      parameterAtrribute.setAnnotations(paramArrays)
      if (m != null) m.getMethodInfo.addAttribute(parameterAtrribute)
    }
    // 给方法增加注解@${method} @Path("/${url}") @Consumes({"application/json"}) @Produces({"application/json"})
    val annotationClasses = Array[String]("javax.ws.rs." + method, "javax.ws.rs.Path", "javax.ws.rs.Consumes", "javax.ws.rs.Produces")
    val memberNames = Array[Array[String]](Array(), Array("value"), Array("value"), Array("value"))
    val jsonArrayMemberValue = new ArrayMemberValue(new StringMemberValue("", serviceConst), serviceConst)
    jsonArrayMemberValue.setValue(Array[MemberValue](new StringMemberValue("application/json;charset=utf-8", serviceConst)))
    val memberValues = Array[Array[MemberValue]](Array(), Array(if (method == "POST") new StringMemberValue("/" + url, serviceConst)
    else new StringMemberValue("/" + url + uri, serviceConst)), Array(jsonArrayMemberValue), Array(jsonArrayMemberValue))
    JavassistUtil.addAnnotation(serviceConst, m, annotationClasses, memberNames, memberValues)
  }

  private def writeClassFile(clazz: CtClass, className: String): Unit = {
    val classFile = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath + className)
    if (classFile.exists) classFile.delete
    clazz.writeFile(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
  }
}
