# POTATOES.XML

## 根节点(root)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<root>
  ...
</root>
```
### 全局配置(config)
全局配置目前包括分页接口GETS默认页大小，自动构建的相关类包名，以下信息均为默认配置(可不配)
```xml
<config>
  <pageSize>10</pageSize>
  <modelPackage>cn.anayoo.sweetpotato.run.model</modelPackage>
  <servicePackage>cn.anayoo.sweetpotato.run.service</servicePackage>
</config>
```

### 数据源(datasource)
一个配置文件可以定义多个数据源，目前支持mysql和oracle12c。每一个数据源必须有一个name属性，并且不同数据源的name不能相同。
```xml
<datasource name="demo">
  <url>jdbc:mysql://localhost:3306/test?useUnicode=yes&amp;characterEncoding=UTF-8</url>
  <username>root</username>
  <password>123456</password>
  <connectionTimeout>3000</connectionTimeout>
</datasource>
```

### 表(table)
一个配置文件可以定义多个表，每个表必须有一个name和url属性，并且不同表的name和url不能相同。
* name: 表资源的名称
* datasource: 使用的数据源name
* value: 该表在数据库中的名称
* autoBuild: 使用自动构建，为true时不需要详细定义每一个表字段的详细属性，也可以通过显式配置覆盖表字段的任何属性(默认为true)
* url: 对应web服务该资源的服务地址
* gets: 对应web服务该资源分页查询接口的服务地址(可以不显式配置，默认值为url + "s")
* key: 表在数据库中的主键(目前仅支持单主键，可以不显式配置，默认通过自动构建设置，若autoBuild=false则取第一个field的值)
* order: 排序字段(可以不显式配置，默认和key相同，若autoBuild=false则取第一个field的值)
* orderType: 排序方式(可以不显式配置，默认为正序asc，可选倒序desc)
* pageSize: 分页大小(可以不显式配置，默认继承全局配置config中的pageSize)
```xml
<table name="user" datasource="demo" value="test_user" autoBuild="true" url="user" key="id" order="username" orderType="desc" pageSize="16">
  ...
</table>
```

### 表字段(field)
一个表(table)可以定义多个表字段(field)，需注意如果表配置了autoBuild=false，需要将主键的表字段放在第一个。
* value: 该字段在数据库中的名称
* type: 字段类型，只有number和string两种(一般来说，这两种类型通吃几乎所有其他类型)
* regex: 当字段类型为string时，regex域用于POST和PUT请求中参数合法性校验使用(如果表配置了autoBuild，regex默认值为""，可以通过显式定义field的方式使用自定义的校验)
* isPrimaryKey: 是否为主键，可以显式定义(但请不要定义多个主键)
* allowNone: 是否允许为空值，用于POST和PUT请求中参数合法性校验使用(可以显式配置覆盖数据库中的设置，但请不要将数据库中不允许为空的字段配置为true)
```xml
<field value="username" type="string" regex="^[0-9A-Za-z]{6,16}$" isPrimaryKey="false" allowNone="true" />
```