# SweetPotato
基于Jersey的Restful API快速动态构建项目
## 快速启动
1. 从docker.io检出sweetpotato
    ```bash
    docker pull anayoo/sweetpotato
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
        serviceUrl:
          defaultZone: http://{serverIp}:{serverPort}/eureka/
    server:
      port: {servicePort}
    spring:
      application:
        name: {clientName}
    ```
    DEMO: potatoes.xml
    ```code
    <?xml version="1.0" encoding="UTF-8"?>
    <root>
        <datasource name="default">
            <url>jdbc:mysql://mariadb:3306/test?useUnicode=yes&amp;characterEncoding=UTF-8</url>
            <username>root</username>
            <password>123456</password>
            <connectionTimeout>3000</connectionTimeout>
        </datasource>
    
        <table name="user" datasource="default" value="test_user" url="user">
            <field name="标识" value="id" type="number" autoInc="true" />
            <field name="用户名" value="username" type="string" regex="^[A-Za-z0-9]{6,16}$" allowNone="false" />
            <field name="密码" value="password" type="string" allowNone="false" />
        </table>
    </root>
    ```
3. 启动sweetpotato
    ```bash
    docker run -d --name potato --network demo -v config:/usr/local/SweetPotato/config -p 8080:8080 anayoo/sweetpotato
    ```

## DEMO
### 获取主键(id)值为1的用户信息
#### REQUEST
GET http://x.x.x.x:xx/user/1
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
GET http://x.x.x.x:xx/user/1?username=test
#### RESPONSE
Status Code: 204

### 获取用户列表（默认）
#### REQUEST
GET http://x.x.x.x:xx/users
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

### 获取用户列表（根据username排序，只取第一项，统计该查询条件下的总数）
#### REQUEST
GET http://x.x.x.x:xx/users?order=username&pageSize=1&count=true
#### RESPONSE
Status Code: 200 

Content-Type: application/json;charset=utf-8
```json
{
	"data": [{
		"id": 2,
		"username": "test",
		"password": "234567"
	}],
	"setting": {
		"order": "username",
		"orderType": "asc",
		"page": 1,
		"pageSize": 1,
		"count": 2
	}
}
```

### 插入一条新的用户数据
#### REQUEST
POST http://x.x.x.x:xx/user

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
PUT http://x.x.x.x:xx/user/3

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
DELETE http://x.x.x.x:xx/user/3
#### RESPONSE
Status Code: 200 

Content-Type: application/json;charset=utf-8
```json
1
```