# Gateway服务网关

为微服务架构提供一种简单有效的统一的 API 路由管理方式。

## 为什么需要网关

Gateway网关是我们服务的守门神，所有微服务的统一入口。

网关的**核心功能特性**：

- 服务路由，负载均衡
- 身份认证和权限校验
- 请求限流

架构图：

![image-20220725200117098](https://s2.loli.net/2022/07/25/inwo5dUy7PS269T.png)

**权限控制**：网关作为微服务入口，需要校验用户是是否有请求资格，如果没有则进行拦截。

**路由和负载均衡**：一切请求都必须先经过gateway，但网关不处理业务，而是根据某种规则，把请求转发到某个微服务，这个过程叫做路由。当然路由的目标服务有多个时，还需要做负载均衡。

**限流**：当请求流量过高时，在网关中按照下流的微服务能够接受的速度来放行请求，避免服务压力过大。



在SpringCloud中网关的实现包括两种：

- gateway
- zuul

Zuul是基于Servlet的实现，属于阻塞式编程。而SpringCloudGateway则是基于Spring5中提供的WebFlux，属于响应式编程的实现，具备更好的性能。

------

## 入门使用

基本步骤如下：

1. 创建模块gateway，引入网关依赖
2. 编写启动类
3. 编写基础配置和路由规则
4. 启动网关服务进行测试



**依赖**

```xml
<!--网关-->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<!--nacos服务发现依赖-->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

application.yml 内容如下：

```yaml
server:
  port: 10010   # 网关端口
spring:
  application:
    name: gateway   # 服务名称
  cloud:
    nacos:
      server-addr: localhost:10086   # nacos地址
    gateway:
      routes:   # 网关路由配置
        - id: userservice   # 路由id，自定义，只要唯一即可
          # uri: http://127.0.0.1:8081   # 路由的目标地址 http就是固定地址
          uri: lb://userservice   # 路由的目标地址 lb就是负载均衡，后面跟服务名称
          predicates:   # 路由断言，也就是判断请求是否符合路由规则的条件
            - Path=/user/**   # 这个是按照路径匹配，只要以/user/开头就符合要求
        - id: orderservice
          uri: lb://orderservice
          predicates:
            - Path=/order/**
```

我们将 `/user/**`开头的请求，代理到`lb://userservice`，**lb是负载均衡**，根据服务名拉取服务列表，实现负载均衡。

路由（routers）配置包括：

1. 路由id：路由的唯一标示

2. 路由目标（uri）：路由的目标地址，http代表固定地址，lb代表根据服务名负载均衡

3. 路由断言（predicates）：判断路由的规则，

4. 路由过滤器（filters）：对请求或响应做处理



**启动网关 测试**

访问 http://localhost:10010/user/1 时，符合 `/user/**` 规则，请求转发到 uri：http://userservice/user/1

![image-20220725202916328](https://s2.loli.net/2022/07/25/IstYpG62VQ7OLlB.png)

访问http://localhost:10010/order/102

![image-20220725202934495](https://s2.loli.net/2022/07/25/BQgcMEdyDiv3Rs9.png)

**处理流程图**

![image-20220725202951740](https://s2.loli.net/2022/07/25/dInPlzivajO2Zcs.png)

------

## 路由断言工厂

我们上面在配置文件中写的断言规则只是简单字符串，这些字符串会被Predicate Factory读取并处理，转变为路由判断的条件

例如`Path=/user/**`是按照路径匹配，这个规则是由

`org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory`类来

处理的，像这样的断言工厂在SpringCloudGateway还有十几个:

| **名称**   | **说明**                       | **示例**                                                     |
| ---------- | ------------------------------ | ------------------------------------------------------------ |
| After      | 是某个时间点后的请求           | -  After=2037-01-20T17:42:47.789-07:00[America/Denver]       |
| Before     | 是某个时间点之前的请求         | -  Before=2031-04-13T15:14:47.433+08:00[Asia/Shanghai]       |
| Between    | 是某两个时间点之前的请求       | -  Between=2037-01-20T17:42:47.789-07:00[America/Denver],  2037-01-21T17:42:47.789-07:00[America/Denver] |
| Cookie     | 请求必须包含某些cookie         | - Cookie=chocolate, ch.p                                     |
| Header     | 请求必须包含某些header         | - Header=X-Request-Id, \d+                                   |
| Host       | 请求必须是访问某个host（域名） | -  Host=**.somehost.org,**.anotherhost.org                   |
| Method     | 请求方式必须是指定方式         | - Method=GET,POST                                            |
| **Path**   | 请求路径必须符合指定规则       | - Path=/red/{segment},/blue/**                               |
| Query      | 请求参数必须包含指定参数       | - Query=name, Jack或者-  Query=name                          |
| RemoteAddr | 请求者的ip必须是指定范围       | - RemoteAddr=192.168.1.1/24                                  |
| Weight     | 权重处理                       |                                                              |

**一般只需要掌握Path这种路由工程就可以了，更多详细去官方文档照抄就好了**

> 官方文档：https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#gateway-request-predicates-factories

------

## 过滤器工厂

GatewayFilter 是网关中提供的一种过滤器，可以**对进入网关的请求和微服务返回的响应做处理**。

![image-20220725204309101](https://s2.loli.net/2022/07/25/xDdOKptaTny1934.png)

过滤器的**作用**是什么？

① 对路由的请求或响应做加工处理，比如添加请求头

② 配置在路由下的过滤器只对当前路由的请求生效



### 路由过滤器的种类

Spring提供了31种不同的路由过滤器工厂。例如：

| **名称**             | **说明**                     |
| -------------------- | ---------------------------- |
| AddRequestHeader     | 给当前请求添加一个请求头     |
| RemoveRequestHeader  | 移除请求中的一个请求头       |
| AddResponseHeader    | 给响应结果中添加一个响应头   |
| RemoveResponseHeader | 从响应结果中移除有一个响应头 |
| RequestRateLimiter   | 限制请求的流量               |

> 官方文档：https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#gatewayfilter-factories



**例如：**给所有进入userservice的请求添加一个请求头：Truth=www.ly00.cn

只需要修改gateway服务的application.yml文件，添加路由过滤即可：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: userservice
          uri: lb://userservice
          predicates:
            - Path=/user/**
          filters: # 过滤器
            - AddRequestHeader=Truth, www.ly00.cn!   # 添加请求头
```

当前过滤器写在userservice路由下，因此仅仅对访问userservice的请求有效。



**验证**，我们修改 userservice 中的一个接口，打印输出

```java
@GetMapping("/{id}")
public User queryById(@PathVariable("id") Long id, @RequestHeader(value = "Truth", required = false) String truth) {
    log.warn("truth: " + truth);  // 打印
    return userService.queryById(id);
}
```

![image-20220725221614824](https://s2.loli.net/2022/07/25/2w5Lh8P1ZVMye6x.png)



如果要对所有的路由都生效，则可以将过滤器工厂写到 **default-filters 默认过滤器**下。格式如下：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: userservice
          uri: lb://userservice
          predicates:
            - Path=/user/**
      default-filters: # 默认过滤项
        - AddRequestHeader=Truth, www.ly00.cn!
```

------

## 全局过滤器

上面的过滤器，Spring网关提供了31种，但每一种过滤器的作用都是固定的。如果我们希望拦截请求，做自己的业务逻辑则没办法实现。

全局过滤器的作用也是处理一切进入网关的请求和微服务响应，与GatewayFilter的作用一样。区别在于GatewayFilter通过配置定义，处理逻辑是**固定的**；而GlobalFilter的逻辑需要自己写代码实现。

定义方式是**实现GlobalFilter接口**。

```java
public interface GlobalFilter {
    /**
     *  处理当前请求，有必要的话通过{@link GatewayFilterChain}将请求交给下一个过滤器处理
     *
     * @param exchange 请求上下文，里面可以获取Request、Response等信息
     * @param chain 用来把请求委托给下一个过滤器 
     * @return {@code Mono<Void>} 返回标示当前过滤器业务结束
     */
    Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);
}
```



在filter中编写自定义逻辑，可以实现下列功能：

- **登录状态判断**
- **权限校验**
- **请求限流等**



### 自定义全局过滤器

**需求**：定义全局过滤器，拦截请求，判断请求的参数是否满足下面条件：

- 参数中是否有authorization，

- authorization参数值是否为admin

如果同时满足则放行，否则拦截



实现：

在gateway中定义一个过滤器：

```java
package cn.ly00.gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

//@Order(-1)  // 必须配置优先级顺序，或实现Ordered接口  值越低优先级越高
@Component
public class AuthorizeFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.获取请求参数
        MultiValueMap<String, String> params = exchange.getRequest().getQueryParams();
        // 2.获取authorization参数
        String auth = params.getFirst("authorization");
        // 3.校验admin
        if ("admin".equals(auth)) {
            // 放行
            return chain.filter(exchange);
        }
        // 4.拦截
        // 4.1.禁止访问，设置状态码
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        // 4.2.结束处理
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
```



**重启gateway**，访问：http://localhost:10010/user/1    和    http://localhost:10010/user/1?authorization=xxx

![image-20220725225530020](https://s2.loli.net/2022/07/25/qCp5fwIVjk3xlLE.png)

访问：http://localhost:10010/user/1?authorization=admin

![image-20220725225257550](https://s2.loli.net/2022/07/25/g9KTjkownGVpB68.png)

### 过滤器执行顺序

请求进入网关会碰到三类过滤器：当前路由的过滤器、DefaultFilter、GlobalFilter

请求路由后，会将当前路由过滤器和DefaultFilter、GlobalFilter，合并到一个过滤器链（集合）中，排序后依次执行每个过滤器：

![image-20220725234506897](https://s2.loli.net/2022/07/25/YOUimHNXRVAugTy.png)



排序的规则是什么呢？

- 每一个过滤器都必须指定一个int类型的order值，**order值越小，优先级越高，执行顺序越靠前**。
- GlobalFilter通过实现Ordered接口，或者添加@Order注解来指定order值，由我们自己指定
- 路由过滤器和defaultFilter的order由Spring指定，默认是按照声明顺序从1递增。
- 当过滤器的order值一样时，会按照 **defaultFilter > 路由过滤器 > GlobalFilter**的顺序执行。



## 跨域问题

**什么是跨域问题**

跨域：域名不一致就是跨域，主要包括：

- 域名不同： www.taobao.com 和 www.taobao.org 和 www.jd.com 和 miaosha.jd.com

- 域名相同，端口不同：localhost:8080和localhost8081

跨域问题：**浏览器禁止**请求的发起者与服务端发生跨域ajax请求，请求被浏览器拦截的问题

不知道的小伙伴可以查看https://www.ruanyifeng.com/blog/2016/04/cors.html



之前的orderService调用userService不存在跨域，是因为不是ajax请求。http直链请求了。



**模拟跨域**

>  index.html

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="ie=edge">
    <title>Document</title>
</head>
<body>
<p>请查看控制台</p>
</body>
<script src="https://cdn.bootcdn.net/ajax/libs/axios/0.27.2/axios.min.js"></script>
<script>
  axios.get("http://localhost:10010/user/1?authorization=admin")
  .then(resp => console.log(resp.data))
  .catch(err => console.log(err))
</script>
</html>
```

放入tomcat或者nginx等web服务器中，启动并访问。可以在浏览器**控制台**看到下面的错误：

![image-20220725233922928](https://s2.loli.net/2022/07/25/fcHGsFOnPZI7xbW.png)



**解决跨域问题**

在gateway服务的application.yml文件中，添加下面的配置：

```yaml
spring:
  cloud:
    gateway:
      globalcors: # 全局的跨域处理
        add-to-simple-url-handler-mapping: true # 解决options请求被拦截问题
        corsConfigurations:
          '[/**]':
            allowedOrigins: # 允许哪些网站的跨域请求 
              - "http://localhost"
            allowedMethods: # 允许的跨域ajax的请求方式
              - "GET"
              - "POST"
              - "DELETE"
              - "PUT"
              - "OPTIONS"
            allowedHeaders: "*" # 允许在请求中携带的头信息
            allowCredentials: true # 是否允许携带cookie
            maxAge: 360000 # 这次跨域检测的有效期
```





> 案例代码：https://github.com/lycodehub/Microservices/tree/main/06-cloud-gateway