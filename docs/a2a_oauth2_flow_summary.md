# A2A OAuth2 认证流程完整总结

## 架构概览

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  a2a-secure-    │    │  a2a-auth-      │    │  a2a-secure-    │
│  client (18081) │    │  server (9000)  │    │  server (18080) │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                      │
         │  1. POST /oauth2/token                      │
         │  (client_credentials)                       │
         │─────────────────────>│                      │
         │                      │                      │
         │  2. JWT Access Token │                      │
         │<─────────────────────│                      │
         │                      │                      │
         │  3. POST /a2a (Authorization: Bearer token) │
         │─────────────────────────────────────────────>
         │                      │                      │
         │                      │  (验证 JWT 签名)     │
         │                      │                      │
         │  4. Agent 响应       │                      │
         │<─────────────────────────────────────────────
```

---

## 服务列表

| 服务 | 端口 | 角色 | 关键职责 |
|------|------|------|----------|
| `a2a-auth-server` | 9000 | 授权服务器 | 签发 JWT Token |
| `a2a-secure-server` | 18080 | 资源服务器 | 验证 Token，提供 Agent 服务 |
| `a2a-secure-client` | 18081 | 客户端 | 获取 Token，调用远程 Agent |

---

## 1️⃣ a2a-auth-server (授权服务器)

### 启动时做了什么

| 步骤 | 操作 | 关键类/方法 |
|------|------|-------------|
| 1 | 生成 RSA 2048 密钥对 | `AuthorizationServerConfig.generateRsaKey()` |
| 2 | 创建 JWK (JSON Web Key) | `AuthorizationServerConfig.jwkSource()` |
| 3 | 注册 OAuth2 客户端 | `AuthorizationServerConfig.registeredClientRepository()` |
| 4 | 配置授权服务器端点 | `AuthorizationServerConfig.authorizationServerSecurityFilterChain()` |

### 收到请求时做了什么

**POST `/oauth2/token`** (Client Credentials Grant)

```
请求:
  Authorization: Basic base64(a2a-client:a2a-secret)
  Body: grant_type=client_credentials&scope=agent.call
```

| 步骤 | 操作 |
|------|------|
| 1 | 解析 Basic Auth，验证 client_id/secret |
| 2 | 检查 grant_type 和 scope |
| 3 | 使用私钥签名生成 JWT |
| 4 | 返回 access_token |

### 关键类

**文件**: `examples/a2a-auth-server/src/main/java/com/alibaba/cloud/ai/examples/a2a/auth/AuthorizationServerConfig.java`

```java
// 客户端配置
.clientId("a2a-client")
.clientSecret("{noop}a2a-secret")
.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
.scope("agent.call")
.accessTokenTimeToLive(Duration.ofHours(1))

// Issuer
.issuer("http://localhost:9000")
```

### 关键配置

**文件**: `examples/a2a-auth-server/src/main/resources/application.properties`

```properties
server.port=9000
```

---

## 2️⃣ a2a-secure-server (资源服务器)

### 启动时做了什么

| 步骤 | 操作 | 说明 |
|------|------|------|
| 1 | 从授权服务器获取 JWKS | 访问 `http://localhost:9000/.well-known/jwks.json` |
| 2 | 缓存 RSA 公钥 | Spring Security 自动处理 |
| 3 | 配置 Security Filter | 配置 JWT 验证 |
| 4 | 注册 Agent 到 A2A 端点 | 定义 `rootAgent` Bean |
| 5 | 向 Nacos 注册服务 | A2A Nacos AutoConfiguration |

### 收到请求时做了什么

**POST `/a2a`** (调用 Agent)

```
请求:
  Authorization: Bearer eyJraWQiOi...
  Body: { "method": "message/send", ... }
```

| 步骤 | 操作 | 关键类 |
|------|------|--------|
| 1 | 提取 Authorization Header | `BearerTokenAuthenticationFilter` |
| 2 | 解码 JWT | `NimbusJwtDecoder` |
| 3 | 验证签名 (使用缓存的公钥) | RSA 公钥验证 |
| 4 | 检查 exp/iss/aud | JWT 标准验证 |
| 5 | 验证通过 → 调用 Agent | A2A Server Handler |
| 6 | 验证失败 → 401 Unauthorized | - |

### 关键类

**文件**: `examples/a2a-secure-server/src/main/java/com/alibaba/cloud/ai/examples/a2a/server/SecureServerApplication.java`

```java
// 安全规则
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            // Agent 发现端点公开
            .requestMatchers("/.well-known/agent.json").permitAll()
            // 其他请求需要认证
            .anyRequest().authenticated())
        // 使用 JWT 验证
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}

// Agent 定义
@Bean
public Agent rootAgent(ChatModel chatModel) {
    return ReactAgent.builder()
        .name("SecureDataAnalysisAgent")
        .model(chatModel)
        .description("一个受 OAuth2 保护的数据分析 Agent")
        .build();
}
```

### 关键配置

**文件**: `examples/a2a-secure-server/src/main/resources/application.properties`

```properties
server.port=18080
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000
```

---

## 3️⃣ a2a-secure-client (客户端)

### 启动时做了什么

| 步骤 | 操作 | 说明 |
|------|------|------|
| 1 | 配置 OAuth2 客户端 | 创建 `ClientRegistrationRepository` |
| 2 | 创建 Token 管理器 | 创建 `OAuth2AuthorizedClientManager` |
| 3 | 初始化 TokenProvider | 封装 Token 获取逻辑 |
| 4 | 从 Nacos 发现 Agent | `AgentCardProvider` |

### 收到请求时做了什么

**GET `/invoke?message=xxx`**

| 步骤 | 操作 | 关键类/方法 |
|------|------|-------------|
| 1 | 接收用户请求 | `SecureClientController.invoke()` |
| 2 | 从 Nacos 获取 Agent 地址 | `AgentCardProvider` |
| 3 | 调用 TokenProvider 获取 Token | `OAuth2TokenProvider.get()` |
| 4 | (如无缓存) 向 Auth Server 请求 Token | `OAuth2AuthorizedClientManager.authorize()` |
| 5 | 构建 A2aRemoteAgent | 设置 `tokenProvider` |
| 6 | 调用远程 Agent (带 Token) | `A2aRemoteAgent.stream()` |

### 关键类

**文件**: `examples/a2a-secure-client/src/main/java/com/alibaba/cloud/ai/examples/a2a/client/OAuth2ClientConfig.java`

```java
// 手动创建 ClientRegistrationRepository
@Bean
public ClientRegistrationRepository clientRegistrationRepository() {
    ClientRegistration clientRegistration = ClientRegistration
        .withRegistrationId("a2a")
        .clientId(clientId)
        .clientSecret(clientSecret)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope(scope.split(","))
        .tokenUri(tokenUri)
        .build();
    return new InMemoryClientRegistrationRepository(clientRegistration);
}

// Token 管理器
@Bean
public OAuth2AuthorizedClientManager authorizedClientManager(...) {
    OAuth2AuthorizedClientProvider authorizedClientProvider = 
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build();
    // ...
}
```

**文件**: `examples/a2a-secure-client/src/main/java/com/alibaba/cloud/ai/examples/a2a/client/OAuth2TokenProvider.java`

```java
@Component
public class OAuth2TokenProvider implements Supplier<String> {
    
    @Override
    public String get() {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
            .withClientRegistrationId("a2a")
            .principal("a2a-client")
            .build();
        
        OAuth2AuthorizedClient authorizedClient = 
            authorizedClientManager.authorize(authorizeRequest);
        
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
```

**文件**: `examples/a2a-secure-client/src/main/java/com/alibaba/cloud/ai/examples/a2a/client/SecureClientController.java`

```java
@GetMapping("/invoke")
public Flux<String> invoke(@RequestParam String message) {
    A2aRemoteAgent remoteAgent = A2aRemoteAgent.builder()
        .name("SecureDataAnalysisAgent")
        .agentCardProvider(agentCardProvider)  // 从 Nacos 发现
        .tokenProvider(() -> tokenProvider.get())  // 动态获取 Token
        .build();

    return remoteAgent.getAndCompileGraph().stream(Map.of("input", message));
}
```

### 关键配置

**文件**: `examples/a2a-secure-client/src/main/resources/application.properties`

```properties
server.port=18081

# OAuth2 客户端配置
spring.security.oauth2.client.registration.a2a.client-id=a2a-client
spring.security.oauth2.client.registration.a2a.client-secret=a2a-secret
spring.security.oauth2.client.registration.a2a.authorization-grant-type=client_credentials
spring.security.oauth2.client.registration.a2a.scope=agent.call
spring.security.oauth2.client.provider.a2a.token-uri=http://localhost:9000/oauth2/token
```

---

## 关键类索引

### a2a-auth-server

| 类 | 文件路径 | 职责 |
|----|----------|------|
| `AuthorizationServerConfig` | `examples/a2a-auth-server/.../AuthorizationServerConfig.java` | 配置授权服务器、客户端注册、RSA 密钥 |

### a2a-secure-server

| 类 | 文件路径 | 职责 |
|----|----------|------|
| `SecureServerApplication` | `examples/a2a-secure-server/.../SecureServerApplication.java` | 主应用，配置 Security 和 Agent |
| `A2aServerSecurityAutoConfiguration` | `spring-boot-starters/spring-ai-alibaba-starter-a2a-nacos/.../A2aServerSecurityAutoConfiguration.java` | 自动配置 JWT 资源服务器 |

### a2a-secure-client

| 类 | 文件路径 | 职责 |
|----|----------|------|
| `OAuth2ClientConfig` | `examples/a2a-secure-client/.../OAuth2ClientConfig.java` | 配置 OAuth2 客户端、Token 管理器 |
| `OAuth2TokenProvider` | `examples/a2a-secure-client/.../OAuth2TokenProvider.java` | 获取并缓存 OAuth2 Access Token |
| `SecureClientController` | `examples/a2a-secure-client/.../SecureClientController.java` | HTTP 接口，调用远程 Agent |

### spring-ai-alibaba-agent-framework

| 类 | 文件路径 | 职责 |
|----|----------|------|
| `A2aRemoteAgent` | `spring-ai-alibaba-agent-framework/.../A2aRemoteAgent.java` | 远程 Agent 调用，支持 tokenProvider |

---

## 完整调用时序

```
用户 → GET /invoke?message=xxx
         ↓
   SecureClientController
         ↓
   OAuth2TokenProvider.get()
         ↓ (如无缓存)
   POST http://localhost:9000/oauth2/token
         ↓
   AuthorizationServerConfig (签发 JWT)
         ↓
   返回 access_token
         ↓
   A2aRemoteAgent.stream() + Bearer Token
         ↓
   POST http://localhost:18080/a2a
         ↓
   SecureServerApplication.securityFilterChain (验证 JWT)
         ↓ (验证通过)
   Agent 处理请求
         ↓
   返回响应
```

---

## 如何验证无效 Token

修改 `OAuth2TokenProvider.get()` 方法：

```java
@Override
public String get() {
    // 测试无效 token
    return "invalid_token";
}
```

预期结果：服务端返回 **401 Unauthorized**

---

## 启动顺序

1. **先启动** `a2a-auth-server` (端口 9000)
2. **再启动** `a2a-secure-server` (端口 18080) - 它需要从 auth-server 获取公钥
3. **最后启动** `a2a-secure-client` (端口 18081)

## 测试命令

```powershell
# 1. 手动获取 Token
$body = "grant_type=client_credentials&scope=agent.call"
$headers = @{Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("a2a-client:a2a-secret"))}
$response = Invoke-RestMethod -Uri "http://localhost:9000/oauth2/token" -Method Post -Body $body -Headers $headers
$response.access_token

# 2. 通过客户端调用
Invoke-RestMethod -Uri "http://localhost:18081/invoke?message=你好"
```
