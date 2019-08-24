# SweetPotato
基于Jersey的Restful API快速动态构建项目
## 快速启动
1. 从docker.io检出sweetpotato
    ```bash
    docker pull anayoo/sweetpotato:latest
    ```
2. 使用自己的application.yml/xml potatoes.xml配置文件
    ```bash
    mkdir config
    touch config/application.yml
    touch config/potatoes.xml
    ```
    DEMO: application.yml
    ```code
    # 以下信息按项目实际情况填写
    eureka:
      client:
        register-with-eureka: false
        fetch-registry: false
        serviceUrl:
          defaultZone: http://eureka:8060/eureka/
    server:
      port: 8080
    spring:
      application:
        name: sweetpotato
    ```
    DEMO: potatoes.xml

    see: [potatoes.md](https://github.com/Anayoo/SweetPotato/blob/master/potatoes.md)
    ```code
    <?xml version="1.0" encoding="UTF-8"?>
    <root>
        <datasource name="demo">
            <url>jdbc:mysql://localhost:3306/test?useUnicode=yes&amp;characterEncoding=UTF-8</url>
            <username>root</username>
            <password>123456</password>
            <connectionTimeout>3000</connectionTimeout>
        </datasource>
        <datasource name="test">
            <url>jdbc:oracle:thin:@localhost:1521/testdb.localdomain</url>
            <username>test</username>
            <password>123456</password>
        </datasource>

        <table name="user" datasource="demo" value="test_user" autoBuild="true" url="user" pageSize="16">
            <field value="username" regex="^[A-Za-z0-9]{6,16}$" allowNone="false" />
        </table>
        <table name="oracle_user" datasource="test" value="test_user" url="oracle_user" />
    </root>
    ```
3. 启动sweetpotato
    ```bash
    docker run -d --name potato --network demo -v config:/usr/local/SweetPotato/config -p 8080:8080 anayoo/sweetpotato
    ```

## DEMO
### 获取主键(id)值为1的用户信息
#### REQUEST
GET http://x.x.x.x:xx/basic/user/1
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
{
	"id": 1,
	"username": "testuser",
	"password": "123456"
}
```

### 获取主键(id)值为1并且username=test的用户信息
#### REQUEST
GET http://x.x.x.x:xx/basic/user/1?username=test
#### RESPONSE
Status Code: 204

### 获取用户列表（默认）
#### REQUEST
GET http://x.x.x.x:xx/basic/users
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
{
	"data": [{
		"id": 1,
		"username": "testuser",
		"password": "123456"
	}, {
		"id": 2,
		"username": "test",
		"password": "234567"
	}],
	"setting": {
		"order": "id",
		"orderType": "asc",
		"page": 1,
		"pageSize": 16,
		"count": null
	}
}
```

### 获取用户列表（根据username倒序，每页1项，查询第2页，统计该查询条件下的总数）
#### REQUEST
GET http://x.x.x.x:xx/basic/users?order=username&orderType=desc&pageSize=1&page=2&count=true
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
{
  "data": [
    {
      "id": "2",
      "password": "234567",
      "username": "test"
    }
  ],
  "setting": {
    "order": "username",
    "orderType": "desc",
    "page": 2,
    "pageSize": 1,
    "count": 2
  }
}
```

### 获取用户列表（对username使用模糊查询）
> 浏览器URL中使用%应转义为%25
#### REQUEST
GET http://x.x.x.x:xx/basic/users?username=%25user
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
{
  "data": [
    {
      "id": "1",
      "password": "123456",
      "username": "testuser"
    }
  ],
  "setting": {
    "order": "id",
    "orderType": "asc",
    "page": 1,
    "pageSize": 16,
    "count": null
  }
}
```

### 获取用户列表（查询id>=2）
#### REQUEST
GET http://x.x.x.x:xx/basic/users?id>=2
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
{
  "data": [
    {
      "id": "2",
      "password": "234567",
      "username": "test"
    }
  ],
  "setting": {
    "order": "id",
    "orderType": "asc",
    "page": 1,
    "pageSize": 16,
    "count": null
  }
}
```

### 获取用户列表（查询username为testuser或user1）
#### REQUEST
GET http://x.x.x.x:xx/basic/users?username=testuser,user1
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
{
  "data": [
    {
      "id": "1",
      "password": "123456",
      "username": "testuser"
    }
  ],
  "setting": {
    "order": "id",
    "orderType": "asc",
    "page": 1,
    "pageSize": 16,
    "count": null
  }
}
```

### 插入一条新的用户数据
#### REQUEST
POST http://x.x.x.x:xx/basic/user

Content-Type: application/json;charset=utf-8
```json
{
    "username": "test12",
    "password": "111111"
}
```
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
1
```

### 插入/更新一条用户数据（主键必须指定）
#### REQUEST
PUT http://x.x.x.x:xx/basic/user/3

Content-Type: application/json;charset=utf-8
```json
{
    "username": "test12",
    "password": "111111"
}
```
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
1
```

### 删除一条用户数据（主键必须指定）
#### REQUEST
DELETE http://x.x.x.x:xx/basic/user/3
#### RESPONSE
Status Code: 200

Content-Type: application/json;charset=utf-8
```json
1
```
