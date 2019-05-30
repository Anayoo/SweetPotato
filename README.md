# SweetPotato
基于Jersey的Restful API快速动态构建项目
## 快速构建
1. 修改src/main/resources下的potatoes.xml配置
2. 使用`mvn package -Dmaven.test.skip=true`打包
3. 部署到web容器中启动(或其他jersey支持的启动方式)

## DEMO
### 获取主键(id)值为1的用户信息
#### REQUEST
GET http://x.x.x.x:xx/SweetPotato/rest/user/1
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
GET http://x.x.x.x:xx/SweetPotato/rest/user/1?username=test
#### RESPONSE
Status Code: 204

### 获取用户列表（默认）
#### REQUEST
GET http://x.x.x.x:xx/SweetPotato/rest/users
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
GET http://x.x.x.x:xx/SweetPotato/rest/users?order=username&pageSize=1&count=true
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
POST http://x.x.x.x:xx/SweetPotato/rest/user

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
PUT http://x.x.x.x:xx/SweetPotato/rest/user/3

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
DELETE http://x.x.x.x:xx/SweetPotato/rest/user/3
#### RESPONSE
Status Code: 200 

Content-Type: application/json;charset=utf-8
```json
1
```