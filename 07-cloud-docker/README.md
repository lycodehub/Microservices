# 利用Docker部署微服务应用

* 要先知道怎么自定义镜像
* Docker-Compose快速部署多个镜像

## 怎么自定义镜像

自定义镜像我们只需要告诉Docker，镜像的组成，需要哪些BaseImage、需要拷贝什么文件、需要安装什么依赖、启动脚本是什么，将来Docker会帮助我们构建镜像。

描述上述信息的文件就是**Dockerfile**。就是一个文件，通过**指令**描述镜像的构建过程

![image-20220727140827215](https://s2.loli.net/2022/07/27/4rpnU5eA7WEcJ13.png)

更新详细语法说明，请参考官网文档： https://docs.docker.com/engine/reference/builder

Dockerfile的第一行必须是FROM，从一个基础镜像来构建



### 将一个Java项目构建为镜像

我们基于java:8-alpine作为**基础镜像**构建，它内置了jdk，因此不需要自己导入jdk

步骤：

- ① 新建一个目录，然后在目录中新建一个文件，命名为Dockerfile

- ```
  mkdir javaweb
  cd javaweb/
  touch Dockerfile
  ```

- ② maven打包我们的java项目，命名app.jar复制到这个目录中

- ③ 编辑Dockerfile文件：

  ```dockerfile
  FROM java:8-alpine
  COPY ./app.jar /tmp/app.jar
  EXPOSE 8090
  ENTRYPOINT java -jar /tmp/app.jar
  ```

  - a ）**基于java:8-alpine作为基础镜像**

  - b ）将app.jar拷贝到镜像中

  - c ）暴露端口

  - d ）编写入口ENTRYPOINT

- ④ 在目录内使用`docker build -t javaweb:1.0 .`命令构建镜像

- 查看一下镜像列表，**构建成功！**

  ```
  [root@ly00 javaweb]# docker images
  REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
  javaweb             1.0                 5e383c54b3be        8 seconds ago       171MB
  ```

我们可以创建容器并运行试试

```
docker run --name web -p 8090:8090 -d javaweb:1.0
```



## Docker-Compose部署微服务

Docker Compose可以基于Compose文件帮我们快速的**部署分布式应用**，而无需手动一个个创建和运行容器！

Compose也是一个文本**文件**，通过**指令**定义集群中的每个容器**如何运行**。相当于集合docker run

```json
version: "3.8"
 services:
  mysql:
    image: mysql:5.7.25
    environment:
     MYSQL_ROOT_PASSWORD: 123 
    volumes:
     - "/tmp/mysql/data:/var/lib/mysql"
     - "/tmp/mysql/conf/hmy.cnf:/etc/mysql/conf.d/hmy.cnf"
  web:
    build: .
    ports:
     - "8090:8090"

```

上面的Compose文件就描述一个项目，其中包含两个容器：

- mysql：一个基于`mysql:5.7.25`镜像构建的容器，并且挂载了两个目录
- web：一个基于`docker build`临时构建的镜像容器，映射端口时8090



DockerCompose的详细语法参考官网：https://docs.docker.com/compose/compose-file/



### 部署案例

将之前学习的cloud-demo微服务集群利用DockerCompose部署

>  案例文件：https://github.com/lycodehub/Microservices/tree/main/07-cloud-docker



创建目录：cloud-demo，包含这些文件夹：

* gateway
* order-service
* user-service
* mysql
* docker-compose.yml

每个文件夹内都放入我们的Dockerfile文件和app.jar包文件

Dockerfile文件，内容如下

```dockerfile
FROM java:8-alpine
COPY ./app.jar /tmp/app.jar
ENTRYPOINT java -jar /tmp/app.jar
```

![image-20220727150019342](https://s2.loli.net/2022/07/27/nTkeLplgdiEO85N.png)



mysql里面的内容则是用于映射my.cnf配置文件和data数据



编写docker-compose.yml文件，内容如下

```yaml
version: "3.2"

services:
  nacos:
    image: nacos/nacos-server
    environment:
      MODE: standalone
    ports:
      - "8848:8848"
  mysql:
    image: mysql:5.7.25
    environment:
      MYSQL_ROOT_PASSWORD: 123
    volumes:
      - "$PWD/mysql/data:/var/lib/mysql"
      - "$PWD/mysql/conf:/etc/mysql/conf.d/"
  userservice:
    build: ./user-service
  orderservice:
    build: ./order-service
  gateway:
    build: ./gateway
    ports:
      - "10010:10010"
```

可以看到，其中包含5个service服务：

- `nacos`：注册中心和配置中心
  - `image: nacos/nacos-server`： 基于nacos/nacos-server镜像构建
  - `environment`：环境变量
    - `MODE: standalone`：单点模式启动
  - `ports`：端口映射，这里暴露了8848端口
- `mysql`：数据库
  - `image: mysql:5.7.25`：镜像版本是mysql:5.7.25
  - `environment`：环境变量
    - `MYSQL_ROOT_PASSWORD: 123`：设置数据库root账户的密码为123
  - `volumes`：数据卷挂载，这里挂载了mysql的data、conf目录，其中data是提前准备好的数据
- `userservice`、`orderservice`、`gateway`：都是需要基于Dockerfile build构建镜像



**修改微服务配置（关键）**

因为微服务将来要部署为docker容器，而容器之间互联不是通过IP地址，而是通过容器名。这里我们将order-service、user-service、gateway服务的mysql、nacos地址都修改为基于容器名的访问。

如下所示：

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/cloud_order?useSSL=false
    username: root
    password: 123
    driver-class-name: com.mysql.jdbc.Driver
  application:
    name: orderservice
  cloud:
    nacos:
      server-addr: nacos:8848 # nacos服务地址
```



**Maven打包**

接下来需要将我们的每个微服务都打包。因为之前查看到Dockerfile中的jar包名称都是app.jar，因此我们的每个微服务都需要用这个名称。

可以通过修改每个微服务**pom.xml**中的打包名称来实现，每个微服务都需要修改：

```xml
<build>
  <!-- 服务打包的最终名称 -->
  <finalName>app</finalName>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

项目打包后：拷贝target目录下的jar包到部署目录



最后进入cloud-demo目录，然后运行下面的命令：

```sh
docker-compose up -d
```

![image-20220727162841961](https://s2.loli.net/2022/07/27/fW1zbX4cU2sPExl.png)

测试：http://192.168.71.107:10010/user/101?authorization=admin


demo下载：[08-docker-compose-demo.zip](https://lyliu-my.sharepoint.com/:u:/g/personal/admin_lyliu_onmicrosoft_com/EUmx3Q-0UUtCsTJJ8d0ptekBhEXCvKlR6fBtBgzTBy87pg?e=IrR4AJ)

