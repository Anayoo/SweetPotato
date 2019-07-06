package cn.anayoo.sweetpotato

import java.io.File
import java.util.logging.{Level, Logger}

import scala.collection.JavaConverters._
import cn.anayoo.sweetpotato.model.{Field, Table}
import cn.anayoo.sweetpotato.util.JavassistUtil
import javassist._
import javassist.bytecode.ParameterAnnotationsAttribute
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
      table.getFields.stream().forEach(field => {
        val f1 = new CtField(JavassistUtil.getCtClass(obj.getClassPool, field.getType), field.getValue, obj)
        f1.setModifiers(Modifier.PRIVATE)
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
      Array[Array[MemberValue]](Array(new StringMemberValue("/", getterService.getClassFile.getConstPool)), Array(new StringMemberValue("", getterService.getClassFile.getConstPool))))
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
           |   java.lang.StringBuilder where = new java.lang.StringBuilder();
           |   boolean isNull = true;
           |   $wheres
           |   if (isNull) return null;
           |   java.lang.String prepareSQL = "select $args from ${table.getValue} where " + where + ";";
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   int i = 1;
           |   $stmts
           |   java.sql.ResultSet rs = stmt.executeQuery();
           |   $model arg = new $model();
           |   rs.last();
           |   if (rs.getRow() > 0) {
           |      $rs
           |   } else arg = null;
           |   conn.close();
           |   return arg;
           |}
         """.stripMargin
      createGetterMethod(xml, classPool.get(model), table.getUrl, parameters, getterService, body, table)

      // GETS
      val parameters2 = parameters :+ classPool.get("int") :+ classPool.get("int") :+ classPool.get("java.lang.String") :+ classPool.get("java.lang.String") :+ classPool.get("boolean")
      val body2 =
        s"""{
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   java.lang.StringBuilder where = new java.lang.StringBuilder();
           |   boolean isNull = true;
           |   $wheres
           |   java.lang.String whereStr = isNull ? "" : " where " + where.toString();
           |   int limitStart = $$${fields.size + 1} * ($$${fields.size + 2} - 1);
           |   java.lang.String prepareSQL = "select $args from ${table.getValue}" + whereStr + " order by " + $$${fields.size + 3} + " " + $$${fields.size + 4} + " limit " + limitStart + ", " + $$${fields.size + 1} + ";";
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   int i = 1;
           |   $stmts
           |   java.sql.ResultSet rs = stmt.executeQuery();
           |   java.util.List args = new java.util.ArrayList();
           |   while(rs.next()) {
           |      $model arg = new $model();
           |      $rs
           |      args.add(arg);
           |   }
           |   $modelPage page = new $modelPage();
           |   page.setData(args);
           |   cn.anayoo.sweetpotato.model.Setting setting = new cn.anayoo.sweetpotato.model.Setting();
           |   setting.setPageSize($$${fields.size + 1});
           |   setting.setPage($$${fields.size + 2});
           |   setting.setOrder($$${fields.size + 3});
           |   setting.setOrderType($$${fields.size + 4});
           |   if ($$${fields.size + 5}) {
           |      prepareSQL = "select count(1) from ${table.getValue} " + whereStr + ";";
           |      stmt = conn.prepareStatement(prepareSQL);
           |      i = 1;
           |      $stmts
           |      rs = stmt.executeQuery();
           |      if (rs.next()) {
           |         setting.setCount(java.lang.Integer.valueOf(rs.getInt(1)));
           |      }
           |   }
           |   conn.close();
           |   page.setSetting(setting);
           |   return page;
           |}
         """.stripMargin
      createGetterMethod(xml, classPool.get(modelPage), table.getGets, parameters2, getterService, body2, table)
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
      Array[Array[MemberValue]](Array(new StringMemberValue("/", posterService.getClassFile.getConstPool)), Array(new StringMemberValue("", posterService.getClassFile.getConstPool))))
    xml.getTables.values().stream().forEach(table => {
      val model = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase() + table.getName.substring(1)
      val fields = for (i <- 0 until table.getFields.size()) yield i + 1 -> table.getFields.get(i)
      val fieldsWithoutKey = (for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)).filterNot(_._2.getValue.equals(table.getKey))
      val args = fields.map(_._2.getValue).mkString(", ")
      val values = fields.map(_ => "?").mkString(", ")
      val verify = spellVerify(fieldsWithoutKey, 1)
      val stmt = spellStmt2(fields, 1)
      val body =
        s"""{
           |   $verify
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   java.lang.String prepareSQL = "insert into ${table.getValue} ($args) values ($values);";
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   $stmt
           |   java.lang.String number = "" + stmt.executeUpdate();
           |   conn.close();
           |   return javax.ws.rs.core.Response.status(200).entity(number).build();
           |}
         """.stripMargin
      createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl, Array[CtClass](classPool.get(model)), posterService, body, "POST")
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
      Array[Array[MemberValue]](Array(new StringMemberValue("/", putterService.getClassFile.getConstPool)), Array(new StringMemberValue("", putterService.getClassFile.getConstPool))))
    xml.getTables.values().stream().forEach(table => {
      val model = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase() + table.getName.substring(1)
      val fieldsWithoutKey = (for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)).filterNot(_._2.getValue.equals(table.getKey))
      val fieldsWithKey = (for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)).filter(_._2.getValue.equals(table.getKey))
      val fields = fieldsWithoutKey :+ (fieldsWithoutKey.size, fieldsWithKey.apply(0)._2)
      val args = fields.map(_._2.getValue).mkString(", ")
      val values = fields.map(_ => "?").mkString(", ")
      val verify = spellVerify(fieldsWithoutKey, 2)
      val whereWithKey = spellWhere2(fieldsWithKey)
      val whereWithoutKey = spellWhere2(fieldsWithoutKey)
      val stmtWithKey = spellStmt3(fieldsWithKey)
      val stmt = spellStmt2(fieldsWithoutKey, 2) + spellStmt3(Seq.empty :+ (fieldsWithoutKey.size, fieldsWithKey.apply(0)._2))
      val body =
        s"""{
           |   if ($$1 == null) return javax.ws.rs.core.Response.status(400).entity("\\"未指明主键\\"").build();
           |   $verify
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   java.lang.String prepareSQL = "select 1 from ${table.getValue} where $whereWithKey;";
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   $stmtWithKey
           |   java.sql.ResultSet rs = stmt.executeQuery();
           |   rs.last();
           |   int number = rs.getRow();
           |   if (number > 0) prepareSQL = "update ${table.getValue} set $whereWithoutKey where $whereWithKey;"; else prepareSQL = "insert into ${table.getValue} ($args) values ($values);";
           |   stmt = conn.prepareStatement(prepareSQL);
           |   $stmt
           |   java.lang.String number = "" + stmt.executeUpdate();
           |   conn.close();
           |   return javax.ws.rs.core.Response.status(200).entity(number).build();
           |}
         """.stripMargin
      val arg = fieldsWithKey.map(_._2).map(f => JavassistUtil.getCtClass(classPool, f.getType)) :+ classPool.get(model)
      createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl, arg.toArray, putterService, body, "PUT")
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
      Array[Array[MemberValue]](Array(new StringMemberValue("/", deleterService.getClassFile.getConstPool)), Array(new StringMemberValue("", deleterService.getClassFile.getConstPool))))
    xml.getTables.values().stream().forEach(table => {
      val model = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase() + table.getName.substring(1)
      val fieldsWithKey = (for (i <- 0 until table.getFields.size()) yield i -> table.getFields.get(i)).filter(_._2.getValue.equals(table.getKey))
      val whereWithKey = spellWhere2(fieldsWithKey)
      val stmtWithKey = spellStmt3(fieldsWithKey)
      val body =
        s"""{
           |   if ($$1 == null) return javax.ws.rs.core.Response.status(400).entity("\\"未指明主键\\"").build();
           |   cn.anayoo.sweetpotato.db.DatabasePool pool = cn.anayoo.sweetpotato.db.DatabasePool.getInstance();
           |   java.sql.Connection conn = pool.getConn("${table.getDatasource}");
           |   java.lang.String prepareSQL = "delete from ${table.getValue} where $whereWithKey;";
           |   java.sql.PreparedStatement stmt = conn.prepareStatement(prepareSQL);
           |   $stmtWithKey
           |   java.lang.String number = "" + stmt.executeUpdate();
           |   conn.close();
           |   return javax.ws.rs.core.Response.status(200).entity(number).build();
           |}
         """.stripMargin
      val arg = fieldsWithKey.map(_._2).map(f => JavassistUtil.getCtClass(classPool, f.getType))
      createOtherMethod(classPool.get("javax.ws.rs.core.Response"), table.getUrl, arg.toArray, deleterService, body, "DELETE")
    })
    writeClassFile(deleterService, "DeleterService.class")
    this
  }

  val spellVerify: (Seq[(Int, Field)], Int) => String = (a:Seq[(Int, Field)], i: Int) => {
    a.map(b => {
      var verify = if (!b._2.isAllowNone)
        s"""if ($$$i.${b._2.getGetterName}() == null) return javax.ws.rs.core.Response.status(400).entity("\\"属性${b._2.getValue}不能为空\\"").build();
         """.stripMargin else ""
      b._2.getType match {
        case "number" => verify += ""
        case "string" => verify +=
          s"""if ($$$i.${b._2.getGetterName}() != null && !java.util.regex.Pattern.compile("${b._2.getRegex.replaceAll("\\\\", "\\\\\\\\")}").matcher($$$i.${b._2.getGetterName}()).find()) return javax.ws.rs.core.Response.status(400).entity("\\"参数${b._2.getValue}校验错误\\"").build();
           """.stripMargin
      }
      verify
    }).mkString
  }

  val spellWhere: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      b._2.getType match {
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
      }
    }).mkString
  }
  val spellWhere2: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      s"""${b._2.getValue}=?"""
    }).mkString(", ")
  }
  val spellStmt: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      b._2.getType match {
        case "number" =>
          s"""if ($$${b._1 + 1} != null) {
             |   stmt.setLong(i, $$${b._1 + 1}.longValue());
             |   i ++;
             |}
             """.stripMargin
        case "string" =>
          s"""if (!$$${b._1 + 1}.equals("")) {
             |   stmt.setString(i, $$${b._1 + 1});
             |   i ++;
             |}
             """.stripMargin
      }
    }).mkString
  }
  val spellStmt2: (Seq[(Int, Field)], Int) => String = (a:Seq[(Int, Field)], i: Int) => {
    a.map(b => {
      b._2.getType match {
        case "number" =>
          s"""if ($$$i.${b._2.getGetterName}() == null) stmt.setNull(${b._1}, java.sql.Types.INTEGER); else stmt.setLong(${b._1}, $$$i.${b._2.getGetterName}().longValue());
           """.stripMargin
        case "string" =>
          s"""if ($$$i.${b._2.getGetterName}() == null) stmt.setNull(${b._1}, java.sql.Types.VARCHAR); else stmt.setString(${b._1}, $$$i.${b._2.getGetterName}());
           """.stripMargin
      }
    }).mkString
  }
  val spellStmt3: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      b._2.getType match {
        case "number" => s"""if ($$1 == null) stmt.setNull(${b._1 + 1}, java.sql.Types.INTEGER); else stmt.setLong(${b._1 + 1}, $$1.longValue());""".stripMargin
        case "string" => s"""if ($$1 == null) stmt.setNull(${b._1 + 1}, java.sql.Types.VARCHAR); else stmt.setString(${b._1 + 1}, $$1);""".stripMargin
      }
    }).mkString
  }
  val spellRs: Seq[(Int, Field)] => String = (a:Seq[(Int, Field)]) => {
    a.map(b => {
      b._2.getType match {
        case "number" => s"""arg.${b._2.getSetterName}(Long.valueOf(rs.getLong("${b._2.getValue}")));"""
        case "string" => s"""arg.${b._2.getSetterName}(rs.getString("${b._2.getValue}"));"""
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
    //System.out.println(body)
    m.setBody(body)
    declaring.addMethod(m)
    // 给参数增加注解 @QueryParam @DefaultValue    参考：https://www.cnblogs.com/coshaho/p/5105545.html
    // 涉及到tables， 暂时不封装到JavassistUtil了 = =
    val parameterAtrribute = new ParameterAnnotationsAttribute(getterServiceConst, ParameterAnnotationsAttribute.visibleTag)
    val paramArrays = new Array[Array[Annotation]](parameters.length)
    val sqlArrays = Array[String]("pageSize", "page", "order", "orderType", "count")
    val sqlDefaultArrays = Array[String]("" + table.getPageSize, "1", table.getOrder, table.getOrderType, "false")
    val pageFullName = xml.getModelPackage + "." + table.getName.substring(0, 1).toUpperCase + table.getName.substring(1) + "Page"
    var i = 0
    while ( {
      i < parameters.length
    }) { // 根据 parameters 和 table.fields 的长度判断是否为SQL分页查询属性
      if (i < table.getFields.size) {
        val field = table.getFields.get(i)
        if (!(returnType == classPool.get(pageFullName)) && field.getValue == table.getKey) { // @QueryParam
          val f1Annot1 = new Annotation("javax.ws.rs.PathParam", getterServiceConst)
          f1Annot1.addMemberValue("value", new StringMemberValue("key", getterServiceConst))
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
    else new StringMemberValue("/" + url + "/{key}", getterServiceConst)), Array(jsonArrayMemberValue), Array(jsonArrayMemberValue))
    JavassistUtil.addAnnotation(getterServiceConst, m, annotationClasses, memberNames, memberValues)
  }

  private def createOtherMethod(returnType: CtClass, url: String, parameters: Array[CtClass], declaring: CtClass, body: String, method: String): Unit = {
    val serviceFile = declaring.getClassFile
    val serviceConst = serviceFile.getConstPool
    val mname = method.toLowerCase + url.substring(0, 1).toUpperCase + url.substring(1)
    val m = new CtMethod(returnType, mname, parameters, declaring)
    m.setModifiers(Modifier.PUBLIC)
    // 方法内的处理逻辑
    //System.out.println(body)
    m.setBody(body)
    declaring.addMethod(m)
    // 给参数增加注解 @QueryParam @DefaultValue    参考：https://www.cnblogs.com/coshaho/p/5105545.html
    // 涉及到tables， 暂时不封装到JavassistUtil了 = =
    if (method == "DELETE") {
      val parameterAtrribute = new ParameterAnnotationsAttribute(serviceConst, ParameterAnnotationsAttribute.visibleTag)
      val paramArrays = new Array[Array[Annotation]](parameters.length)
      val annot1 = new Annotation("javax.ws.rs.PathParam", serviceConst)
      annot1.addMemberValue("value", new StringMemberValue("key", serviceConst))
      paramArrays(0) = new Array[Annotation](1)
      paramArrays(0)(0) = annot1
      parameterAtrribute.setAnnotations(paramArrays)
      if (m != null) m.getMethodInfo.addAttribute(parameterAtrribute)
    }
    if (method == "PUT") {
      val parameterAtrribute = new ParameterAnnotationsAttribute(serviceConst, ParameterAnnotationsAttribute.visibleTag)
      val paramArrays = new Array[Array[Annotation]](parameters.length)
      val annot1 = new Annotation("javax.ws.rs.PathParam", serviceConst)
      annot1.addMemberValue("value", new StringMemberValue("key", serviceConst))
      paramArrays(0) = new Array[Annotation](1)
      paramArrays(0)(0) = annot1
      val annot2 = new Annotation("javax.ws.rs.DefaultValue", serviceConst)
      annot2.addMemberValue("value", new StringMemberValue("", serviceConst))
      paramArrays(1) = new Array[Annotation](1)
      paramArrays(1)(0) = annot2
      parameterAtrribute.setAnnotations(paramArrays)
      if (m != null) m.getMethodInfo.addAttribute(parameterAtrribute)
    }
    // 给方法增加注解@${method} @Path("/${url}") @Consumes({"application/json"}) @Produces({"application/json"})
    val annotationClasses = Array[String]("javax.ws.rs." + method, "javax.ws.rs.Path", "javax.ws.rs.Consumes", "javax.ws.rs.Produces")
    val memberNames = Array[Array[String]](Array(), Array("value"), Array("value"), Array("value"))
    val jsonArrayMemberValue = new ArrayMemberValue(new StringMemberValue("", serviceConst), serviceConst)
    jsonArrayMemberValue.setValue(Array[MemberValue](new StringMemberValue("application/json;charset=utf-8", serviceConst)))
    val memberValues = Array[Array[MemberValue]](Array(), Array(if (method == "POST") new StringMemberValue("/" + url, serviceConst)
    else new StringMemberValue("/" + url + "/{key}", serviceConst)), Array(jsonArrayMemberValue), Array(jsonArrayMemberValue))
    JavassistUtil.addAnnotation(serviceConst, m, annotationClasses, memberNames, memberValues)
  }

  private def writeClassFile(clazz: CtClass, className: String): Unit = {
    val classFile = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath + className)
    if (classFile.exists) classFile.delete
    clazz.writeFile(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
  }
}
