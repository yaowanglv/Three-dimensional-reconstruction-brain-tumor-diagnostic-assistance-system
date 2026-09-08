# JWT + 角色验证开发文档

## 文档信息

| 项目 | 内容 |
|------|------|
| 项目名称 | 大模型驱动的脑肿瘤智能辅助诊断与分析系统 |
| 文档版本 | V1.0 |
| 创建日期 | 2026-05-17 |
| 文档状态 | 草稿 |

---

## 1. 技术选型

### 1.1 后端技术栈

| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| JWT库 | jjwt | 0.11.5 | Java JWT实现 |
| 安全框架 | Spring Security | 6.x | 认证授权框架 |
| 密码加密 | BCrypt | - | Spring Security内置 |
| 缓存 | Redis | 7.x | Token黑名单缓存（可选） |

### 1.2 前端技术栈

| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| HTTP客户端 | Axios | 1.x | 请求拦截器 |
| 状态管理 | Pinia | 2.x | Token状态管理 |
| 路由 | Vue Router | 4.x | 路由守卫 |

---

## 2. 后端实现

### 2.1 Maven依赖配置

在 `pom.xml` 中添加以下依赖：

```xml
<!-- JWT依赖 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>

<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### 2.2 配置文件

在 `application.yml` 中添加JWT配置：

```yaml
# JWT配置
jwt:
  # 密钥（生产环境请使用更复杂的密钥）
  secret: YnJhaW5UdW1vckRpYWdub3Npc1N5c3RlbVNlY3JldEtleTIwMjY=
  # Access Token过期时间（毫秒）
  access-token-expiration: 7200000  # 2小时
  # Refresh Token过期时间（毫秒）
  refresh-token-expiration: 604800000  # 7天
  # Token前缀
  token-prefix: "Bearer "
  # Token请求头
  header: "Authorization"

# Spring Security配置
spring:
  security:
    # 静态资源和公开接口
    public-urls:
      - /api/auth/login
      - /api/auth/refresh
      - /static/**
      - /uploads/**
```

### 2.3 JWT工具类

创建文件 `springb/src/main/java/com/example/springb/utils/JwtUtils.java`：

```java
package com.example.springb.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成Access Token
     */
    public String generateAccessToken(Integer userId, String username, String role, List<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("permissions", permissions);
        claims.put("tokenType", "access");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成Refresh Token
     */
    public String generateRefreshToken(Integer userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("tokenType", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析Token
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Integer.class);
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从Token中获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 从Token中获取权限列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("permissions", List.class);
    }

    /**
     * 获取Token类型
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("tokenType", String.class);
    }

    /**
     * 判断Token是否即将过期（剩余时间小于5分钟）
     */
    public boolean isTokenExpiringSoon(String token) {
        Claims claims = parseToken(token);
        Date expiration = claims.getExpiration();
        long remainingTime = expiration.getTime() - System.currentTimeMillis();
        return remainingTime < 300000; // 5分钟
    }

    /**
     * 获取Token过期时间
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
```

### 2.4 安全配置

创建文件 `springb/src/main/java/com/example/springb/config/SecurityConfig.java`：

```java
package com.example.springb.config;

import com.example.springb.security.JwtAuthenticationFilter;
import com.example.springb.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF
            .csrf(csrf -> csrf.disable())
            // 配置CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 无状态会话
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 异常处理
            .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
            // 请求授权
            .authorizeHttpRequests(auth -> auth
                // 公开接口
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 静态资源
                .requestMatchers("/static/**", "/uploads/**").permitAll()
                // 管理员接口
                .requestMatchers("/admin/**").hasRole("admin")
                // 诊断接口（医生和管理员）
                .requestMatchers("/detect/**").hasAnyRole("admin", "doctor")
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### 2.5 JWT认证过滤器

创建文件 `springb/src/main/java/com/example/springb/security/JwtAuthenticationFilter.java`：

```java
package com.example.springb.security;

import com.example.springb.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            // 检查是否是Access Token
            String tokenType = jwtUtils.getTokenType(token);
            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 从Token中获取用户信息
            Integer userId = jwtUtils.getUserIdFromToken(token);
            String username = jwtUtils.getUsernameFromToken(token);
            String role = jwtUtils.getRoleFromToken(token);
            List<String> permissions = jwtUtils.getPermissionsFromToken(token);

            // 创建权限列表
            List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // 添加角色权限
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            // 创建认证对象
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            // 设置认证信息到上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 将用户信息存入请求属性
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("role", role);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中获取Token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### 2.6 认证入口点

创建文件 `springb/src/main/java/com/example/springb/security/JwtAuthenticationEntryPoint.java`：

```java
package com.example.springb.security;

import com.example.springb.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Result<?> result = Result.error(401, "未认证，请先登录");

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), result);
    }
}
```

### 2.7 认证控制器

创建文件 `springb/src/main/java/com/example/springb/controller/AuthController.java`：

```java
package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.entity.Admin;
import com.example.springb.service.AdminService;
import com.example.springb.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AdminService adminService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest request) {
        // 查询用户
        Admin admin = new Admin();
        admin.setUsername(request.getUsername());
        List<Admin> users = adminService.selectAll(admin);

        if (users.isEmpty()) {
            return Result.error("401", "账号不存在");
        }

        Admin user = users.get(0);

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.error("401", "账号或密码错误");
        }

        // 检查账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.error("403", "账号已被禁用");
        }

        // 获取用户权限
        List<String> permissions = getPermissionsByRole(user.getRole());

        // 生成Token
        String accessToken = jwtUtils.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                permissions
        );
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());

        // 构建响应
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("userInfo", buildUserInfo(user, permissions));

        return Result.success(data);
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Result<?> refreshToken(@RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        // 验证Refresh Token
        if (!jwtUtils.validateToken(refreshToken)) {
            return Result.error("401003", "Refresh Token已过期，请重新登录");
        }

        // 检查Token类型
        String tokenType = jwtUtils.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            return Result.error("401002", "无效的Token");
        }

        // 获取用户信息
        Integer userId = jwtUtils.getUserIdFromToken(refreshToken);

        // 查询用户信息（这里简化处理，实际应该从数据库查询）
        Admin user = adminService.selectAll(new Admin()).stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElse(null);

        if (user == null) {
            return Result.error("401", "用户不存在");
        }

        // 生成新的Token对
        List<String> permissions = getPermissionsByRole(user.getRole());
        String newAccessToken = jwtUtils.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                permissions
        );
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", newAccessToken);
        data.put("refreshToken", newRefreshToken);

        return Result.success(data);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request) {
        // 实际项目中可以将Token加入黑名单
        return Result.success("登出成功");
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/userInfo")
    public Result<?> getUserInfo(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        String role = (String) request.getAttribute("role");

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", userId);
        userInfo.put("username", username);
        userInfo.put("role", role);
        userInfo.put("permissions", getPermissionsByRole(role));

        return Result.success(userInfo);
    }

    /**
     * 根据角色获取权限列表
     */
    private List<String> getPermissionsByRole(String role) {
        List<String> permissions = new ArrayList<>();

        switch (role) {
            case "admin":
                permissions.addAll(Arrays.asList(
                        "user:manage",
                        "detect:create", "detect:view", "detect:export",
                        "data:view", "data:export",
                        "system:config"
                ));
                break;
            case "doctor":
                permissions.addAll(Arrays.asList(
                        "detect:create", "detect:view",
                        "data:view", "data:export"
                ));
                break;
            case "researcher":
                permissions.addAll(Arrays.asList(
                        "detect:view",
                        "data:view", "data:export"
                ));
                break;
            case "user":
            default:
                permissions.addAll(Arrays.asList(
                        "data:view"
                ));
                break;
        }

        return permissions;
    }

    /**
     * 构建用户信息
     */
    private Map<String, Object> buildUserInfo(Admin user, List<String> permissions) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("name", user.getName());
        userInfo.put("role", user.getRole());
        userInfo.put("permissions", permissions);
        return userInfo;
    }

    /**
     * 登录请求
     */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    /**
     * 刷新Token请求
     */
    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
```

### 2.8 权限注解使用示例

在Controller方法上使用权限注解：

```java
@RestController
@RequestMapping("/admin")
public class AdminController {

    /**
     * 需要admin角色才能访问
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('admin')")
    public Result<?> getUsers() {
        // ...
    }

    /**
     * 需要user:manage权限才能访问
     */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public Result<?> createUser() {
        // ...
    }

    /**
     * admin或doctor角色可以访问
     */
    @GetMapping("/detect")
    @PreAuthorize("hasAnyRole('admin', 'doctor')")
    public Result<?> getDetectData() {
        // ...
    }
}
```

---

## 3. 前端实现

### 3.1 安装依赖

```bash
cd vue
npm install axios pinia
```

### 3.2 Token状态管理

创建文件 `vue/src/stores/auth.js`：

```javascript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

export const useAuthStore = defineStore('auth', () => {
    // 状态
    const accessToken = ref(null)
    const refreshToken = ref(null)
    const userInfo = ref(null)
    const isRefreshing = ref(false)
    let refreshSubscribers = []

    // 计算属性
    const isAuthenticated = computed(() => !!accessToken.value)
    const userRole = computed(() => userInfo.value?.role || '')
    const permissions = computed(() => userInfo.value?.permissions || [])

    // 登录
    async function login(username, password) {
        try {
            const res = await request.post('/api/auth/login', { username, password })

            if (res.code === '200') {
                setTokens(res.data.accessToken, res.data.refreshToken)
                userInfo.value = res.data.userInfo
                return { success: true }
            } else {
                return { success: false, message: res.msg }
            }
        } catch (error) {
            return { success: false, message: '登录失败，请重试' }
        }
    }

    // 登出
    async function logout() {
        try {
            await request.post('/api/auth/logout')
        } catch (e) {
            // 忽略错误
        } finally {
            clearAuth()
        }
    }

    // 设置Token
    function setTokens(access, refresh) {
        accessToken.value = access
        refreshToken.value = refresh
        localStorage.setItem('refreshToken', refresh)
    }

    // 清除认证信息
    function clearAuth() {
        accessToken.value = null
        refreshToken.value = null
        userInfo.value = null
        localStorage.removeItem('refreshToken')
    }

    // 刷新Token
    async function refreshAccessToken() {
        if (isRefreshing.value) {
            return new Promise((resolve) => {
                refreshSubscribers.push(resolve)
            })
        }

        isRefreshing.value = true

        try {
            const refreshTokenValue = refreshToken.value || localStorage.getItem('refreshToken')

            if (!refreshTokenValue) {
                throw new Error('No refresh token')
            }

            const res = await request.post('/api/auth/refresh', {
                refreshToken: refreshTokenValue
            })

            if (res.code === '200') {
                setTokens(res.data.accessToken, res.data.refreshToken)

                // 执行等待中的请求
                refreshSubscribers.forEach(callback => callback(accessToken.value))
                refreshSubscribers = []

                return accessToken.value
            } else {
                throw new Error('Refresh failed')
            }
        } catch (error) {
            clearAuth()
            window.location.href = '/login'
            throw error
        } finally {
            isRefreshing.value = false
        }
    }

    // 获取用户信息
    async function fetchUserInfo() {
        try {
            const res = await request.get('/api/auth/userInfo')
            if (res.code === '200') {
                userInfo.value = res.data
            }
        } catch (e) {
            console.error('获取用户信息失败', e)
        }
    }

    // 检查权限
    function hasPermission(permission) {
        return permissions.value.includes(permission)
    }

    // 检查角色
    function hasRole(role) {
        return userRole.value === role
    }

    // 初始化（从localStorage恢复）
    function init() {
        const storedRefreshToken = localStorage.getItem('refreshToken')
        if (storedRefreshToken) {
            refreshToken.value = storedRefreshToken
        }
    }

    return {
        accessToken,
        refreshToken,
        userInfo,
        isAuthenticated,
        userRole,
        permissions,
        login,
        logout,
        setTokens,
        clearAuth,
        refreshAccessToken,
        fetchUserInfo,
        hasPermission,
        hasRole,
        init
    }
})
```

### 3.3 Axios请求拦截器

修改文件 `vue/src/utils/request.js`：

```javascript
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const request = axios.create({
    baseURL: 'http://localhost:9090',
    timeout: 30000
})

// 请求拦截器
request.interceptors.request.use(
    config => {
        const authStore = useAuthStore()

        // 如果有token，添加到请求头
        if (authStore.accessToken) {
            config.headers['Authorization'] = `Bearer ${authStore.accessToken}`
        }

        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 响应拦截器
request.interceptors.response.use(
    response => {
        return response.data
    },
    async error => {
        const originalRequest = error.config
        const authStore = useAuthStore()

        // 如果是401错误且不是刷新请求
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true

            try {
                // 尝试刷新Token
                const newToken = await authStore.refreshAccessToken()

                // 使用新Token重试原请求
                originalRequest.headers['Authorization'] = `Bearer ${newToken}`
                return request(originalRequest)
            } catch (refreshError) {
                // 刷新失败，跳转登录页
                ElMessage.error('登录已过期，请重新登录')
                return Promise.reject(refreshError)
            }
        }

        // 处理其他错误
        const message = error.response?.data?.msg || '请求失败'
        ElMessage.error(message)

        return Promise.reject(error)
    }
)

export default request
```

### 3.4 路由守卫

修改文件 `vue/src/router/index.js`：

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            redirect: '/login'
        },
        {
            path: '/manager',
            component: () => import('../views/Manager.vue'),
            meta: { requiresAuth: true },
            children: [
                {
                    path: 'admin',
                    meta: {
                        name: '管理员信息',
                        requiresRole: 'admin'
                    },
                    component: () => import('../views/Admin.vue')
                },
                {
                    path: 'detect',
                    meta: {
                        name: '脑肿瘤辅助诊断与分析',
                        requiresPermission: 'detect:create'
                    },
                    component: () => import('../views/Detect.vue')
                },
                {
                    path: 'mask',
                    meta: {
                        name: '脑肿瘤辅助分割图像与分析',
                        requiresPermission: 'detect:view'
                    },
                    component: () => import('../views/Mask.vue')
                },
                {
                    path: 'history',
                    meta: {
                        name: '检测历史',
                        requiresPermission: 'detect:view'
                    },
                    component: () => import('../views/History.vue')
                },
                {
                    path: 'dataview',
                    meta: {
                        name: '数据可视化',
                        requiresPermission: 'data:view'
                    },
                    component: () => import('../views/Dataview.vue')
                }
            ]
        },
        {
            path: '/login',
            component: () => import('../views/Login.vue')
        },
        {
            path: '/403',
            name: 'Forbidden',
            component: () => import('../views/403.vue')
        },
        {
            path: '/notFound',
            name: '404',
            component: () => import('../views/404.vue')
        }
    ]
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
    const authStore = useAuthStore()

    // 初始化auth store
    if (!authStore.isAuthenticated && localStorage.getItem('refreshToken')) {
        authStore.init()
    }

    // 不需要认证的页面
    if (!to.meta.requiresAuth) {
        // 如果已登录且访问登录页，重定向到主页
        if (to.path === '/login' && authStore.isAuthenticated) {
            return next('/manager/dataview')
        }
        return next()
    }

    // 需要认证但未登录
    if (!authStore.isAuthenticated) {
        // 尝试使用refresh token恢复会话
        try {
            await authStore.refreshAccessToken()
            await authStore.fetchUserInfo()
        } catch (e) {
            return next('/login')
        }
    }

    // 检查角色要求
    if (to.meta.requiresRole) {
        if (!authStore.hasRole(to.meta.requiresRole)) {
            ElMessage.error('权限不足')
            return next('/403')
        }
    }

    // 检查权限要求
    if (to.meta.requiresPermission) {
        if (!authStore.hasPermission(to.meta.requiresPermission)) {
            ElMessage.error('权限不足')
            return next('/403')
        }
    }

    next()
})

export default router
```

### 3.5 权限指令

创建文件 `vue/src/directives/permission.js`：

```javascript
import { useAuthStore } from '@/stores/auth'

/**
 * 权限指令
 * 用法：v-permission="'user:manage'" 或 v-permission="['user:manage', 'user:view']"
 */
export const permission = {
    mounted(el, binding) {
        const authStore = useAuthStore()
        const { value } = binding

        if (!value) return

        let hasPermission = false

        if (Array.isArray(value)) {
            hasPermission = value.some(perm => authStore.hasPermission(perm))
        } else {
            hasPermission = authStore.hasPermission(value)
        }

        if (!hasPermission) {
            el.parentNode?.removeChild(el)
        }
    }
}

/**
 * 角色指令
 * 用法：v-role="'admin'" 或 v-role="['admin', 'doctor']"
 */
export const role = {
    mounted(el, binding) {
        const authStore = useAuthStore()
        const { value } = binding

        if (!value) return

        let hasRole = false

        if (Array.isArray(value)) {
            hasRole = value.some(r => authStore.hasRole(r))
        } else {
            hasRole = authStore.hasRole(value)
        }

        if (!hasRole) {
            el.parentNode?.removeChild(el)
        }
    }
}
```

### 3.6 注册指令

在 `vue/src/main.js` 中注册：

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { permission, role } from './directives/permission'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// 注册权限指令
app.directive('permission', permission)
app.directive('role', role)

app.mount('#app')
```

### 3.7 登录页面修改

修改 `vue/src/views/Login.vue` 中的登录方法：

```javascript
<script setup>
import { reactive, ref } from "vue"
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from "element-plus"
import router from "@/router"

const authStore = useAuthStore()
const data = reactive({
    form: {},
})
const loginLoading = ref(false)

const login = async () => {
    const { username, password } = data.form

    if (!username || !password) {
        ElMessage.error('请填写账号和密码')
        return
    }

    loginLoading.value = true

    try {
        const result = await authStore.login(username, password)

        if (result.success) {
            ElMessage.success('登录成功')
            router.push('/manager/dataview')
        } else {
            ElMessage.error(result.message || '账号或密码错误')
        }
    } catch (error) {
        ElMessage.error('登录失败，请重试')
    } finally {
        loginLoading.value = false
    }
}
</script>
```

### 3.8 403页面

创建文件 `vue/src/views/403.vue`：

```vue
<template>
  <div class="forbidden-container">
    <div class="forbidden-content">
      <h1>403</h1>
      <h2>权限不足</h2>
      <p>抱歉，您没有访问该页面的权限</p>
      <el-button type="primary" @click="goHome">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
    router.push('/manager/dataview')
}
</script>

<style scoped>
.forbidden-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background: #f5f5f5;
}

.forbidden-content {
    text-align: center;
}

.forbidden-content h1 {
    font-size: 120px;
    color: #e74c3c;
    margin: 0;
}

.forbidden-content h2 {
    font-size: 24px;
    color: #333;
    margin: 20px 0;
}

.forbidden-content p {
    color: #666;
    margin-bottom: 30px;
}
</style>
```

---

## 4. 数据库迁移

### 4.1 执行SQL脚本

```sql
-- 1. 更新admin表，添加必要字段
ALTER TABLE `admin`
ADD COLUMN `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用' AFTER `role`,
ADD COLUMN `email` VARCHAR(100) AFTER `status`,
ADD COLUMN `phone` VARCHAR(20) AFTER `email`,
ADD COLUMN `last_login_time` DATETIME AFTER `phone`,
ADD COLUMN `last_login_ip` VARCHAR(50) AFTER `last_login_time`,
ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `last_login_ip`;

-- 2. 创建refresh_token表
CREATE TABLE IF NOT EXISTS `refresh_token` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `token` VARCHAR(500) NOT NULL,
    `device_info` VARCHAR(200),
    `ip_address` VARCHAR(50),
    `expire_time` DATETIME NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_token` (`token`(100)),
    FOREIGN KEY (`user_id`) REFERENCES `admin`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 创建operation_log表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT,
    `username` VARCHAR(50),
    `operation` VARCHAR(50),
    `method` VARCHAR(10),
    `url` VARCHAR(200),
    `ip` VARCHAR(50),
    `status` TINYINT,
    `error_msg` TEXT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 更新现有用户密码为BCrypt加密格式
-- 注意：这一步需要根据实际情况执行
-- UPDATE `admin` SET `password` = BCrypt加密后的密码 WHERE id = ?
```

### 4.2 密码迁移脚本

创建一个临时的密码迁移工具：

```java
package com.example.springb.migration;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordMigration {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 原始密码
        String rawPassword = "123456";
        String encodedPassword = encoder.encode(rawPassword);

        System.out.println("原始密码: " + rawPassword);
        System.out.println("加密后: " + encodedPassword);

        // 验证
        System.out.println("验证结果: " + encoder.matches(rawPassword, encodedPassword));
    }
}
```

---

## 5. 测试

### 5.1 后端接口测试

使用curl或Postman测试：

```bash
# 1. 登录
curl -X POST http://localhost:9090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 2. 使用Token访问受保护接口
curl -X GET http://localhost:9090/api/auth/userInfo \
  -H "Authorization: Bearer <your_access_token>"

# 3. 刷新Token
curl -X POST http://localhost:9090/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}'

# 4. 访问需要admin权限的接口
curl -X GET http://localhost:9090/admin/selectAll \
  -H "Authorization: Bearer <your_access_token>"
```

### 5.2 前端测试

1. 启动前端开发服务器
2. 访问登录页面
3. 使用正确凭证登录
4. 验证Token是否正确存储
5. 访问需要权限的页面
6. 等待Token过期，验证自动刷新
7. 点击登出，验证Token清除

---

## 6. 部署注意事项

### 6.1 环境变量配置

生产环境请使用环境变量配置敏感信息：

```yaml
jwt:
  secret: ${JWT_SECRET}
```

### 6.2 HTTPS配置

生产环境必须启用HTTPS，配置参考：

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

### 6.3 CORS配置

根据实际部署域名配置CORS：

```java
configuration.setAllowedOrigins(List.of("https://yourdomain.com"));
```

---

## 7. 常见问题

### 7.1 Token过期时间设置

- Access Token：建议2小时，平衡安全性和用户体验
- Refresh Token：建议7天，支持长期会话

### 7.2 多端登录

当前实现支持多端登录，如需单端登录，需要：
1. 在数据库中记录用户的当前Token
2. 登录时使旧Token失效

### 7.3 Token刷新失败

可能原因：
- Refresh Token已过期
- 用户密码已修改
- 用户账号被禁用

处理方式：跳转登录页，提示用户重新登录

---

## 8. 扩展功能

### 8.1 Token黑名单

使用Redis实现Token黑名单：

```java
@Service
public class TokenBlacklistService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    public void addToBlacklist(String token, long expiration) {
        redisTemplate.opsForValue().set(
            "token:blacklist:" + token,
            "blacklisted",
            expiration,
            TimeUnit.MILLISECONDS
        );
    }

    public boolean isBlacklisted(String token) {
        return redisTemplate.hasKey("token:blacklist:" + token);
    }
}
```

### 8.2 操作日志记录

使用AOP记录操作日志：

```java
@Aspect
@Component
public class OperationLogAspect {

    @Around("@annotation(operationLog)")
    public Object logOperation(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        // 记录操作日志
        // ...
        return joinPoint.proceed();
    }
}
```

---

## 9. 参考资料

- [JWT官方文档](https://jwt.io/introduction)
- [Spring Security官方文档](https://docs.spring.io/spring-security/reference/)
- [Vue 3官方文档](https://vuejs.org/)
- [Pinia官方文档](https://pinia.vuejs.org/)
