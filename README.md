# SweetPotato
基于Jersey的Restful API快速动态构建项目
## 快速构建
1. 使用`mvn package -Dmaven.test.skip=true`打包git
2. 修改war包中的WEB-INF/classes/potatoes.xml配置
3. 部署到web容器中启动(或其他jersey支持的启动方式)