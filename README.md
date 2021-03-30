## snail-ws-proxy 

snail-ws-proxy 是一个简单的ws数据转发，使用java原生nio与spring实现

## 功能
- [x] 服务端starter，可以集成到项目中，调试使用
- [x] 基本服务端jar
- [x] 端口转发客户端
- [x] socket5客户端
- [x] 反向代理服务端额外starter包
- [x] 反向代理客户端
- [x] 获取服务端本地terminal额外starter包
- [x] 获取服务端本地terminal客户端

starter和basic-server配置

```yaml
wp:
  server:
    #是否开启
    enable: true
    #endpoint地址
    endpoint-path: /wp
```

```xml
<dependency>
  <groupId>io.github.project-snail</groupId>
  <artifactId>wp-server-boot-starter</artifactId>
  <version>1.1</version>
</dependency>
```

自定义客户端基础包

```xml
<dependency>
  <groupId>io.github.project-snail</groupId>
  <artifactId>wp-client-common</artifactId>
  <version>1.1</version>
</dependency>
```

pass-proxy 配置

starter配置

```xml
<dependencies>
  <groupId>io.github.project-snail</groupId>
  <artifactId>wp-pass-proxy-server-support</artifactId>
  <version>1.12</version>
</dependencies>
```

客户端配置

```yaml
wp:
  pass-proxy:
    client:
      local-addr: 127.0.0.1              #代理地址
      local-port: 22                     #代理端口
      remote-bind-addr: 127.0.0.1        #服务端绑定地址
      remote-bind-port: 9022             #服务端绑定端口
      server-url: ws://127.0.0.1:8080/wp #服务端endpoint地址  
```

port-client配置

```yaml
wp:
  port:
    client:
      #服务端endpoint地址    
      server-url: ws://127.0.0.1:8080/wp
      port-forwarding-list:
        #端口转发列表      
        - remote-address: 127.0.0.1 #远程地址
          remote-port: 22           #远程端口
          bind-addr: 127.0.0.1      #本地绑定地址
          bind-port: 8226           #本地绑定端口
        - remote-address: 127.0.0.1
          remote-port: 21
          bind-addr: 127.0.0.1
          bind-port: 8227
```

socket5-client配置

```yaml
wp:
  socket5:
    client:
      #服务端endpoint地址    
      server-url: ws://127.0.0.1:8080/wp
      #本地绑定地址
      bind-addr: 0.0.0.0
      #本地绑定端口
      bind-port: 8224

```

terminal-client配置

```yaml
wp:
  terminal:
    client:
      #服务端endpoint地址   
      server-url: ws://127.0.0.1:8080/wp
```

启动效果

![sever启动](./img/Jietu20210330-122001.jpg)

![客户端启动](./img/Jietu20210330-122225.jpg)
