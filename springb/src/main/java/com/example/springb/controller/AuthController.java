package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.entity.Admin;
import com.example.springb.exception.CustomerException;
import com.example.springb.security.RolePermissionUtils;
import com.example.springb.service.AdminService;
import com.example.springb.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AdminService adminService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private PasswordEncoder passwordEncoder;

    private Boolean hasStatusColumn;

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        Admin dbUser = adminService.login(toAdmin(request));
        upgradePasswordIfNeeded(dbUser, request.getPassword());
        checkUserEnabled(dbUser);

        List<String> permissions = RolePermissionUtils.getPermissionsByRole(dbUser.getRole());
        String accessToken = jwtUtils.generateAccessToken(
                dbUser.getId(),
                dbUser.getUsername(),
                RolePermissionUtils.normalizeRole(dbUser.getRole()),
                permissions
        );
        String refreshToken = jwtUtils.generateRefreshToken(dbUser.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("expiresIn", jwtUtils.getAccessTokenExpiration());
        data.put("userInfo", buildUserInfo(dbUser, permissions));

        return Result.success(data);
    }

    @PostMapping("/refresh")
    public Result refresh(@RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || !jwtUtils.validateToken(refreshToken)) {
            return Result.error("401003", "Refresh Token已过期，请重新登录");
        }
        if (!"refresh".equals(jwtUtils.getTokenType(refreshToken))) {
            return Result.error("401002", "无效的Token");
        }

        Integer userId = jwtUtils.getUserIdFromToken(refreshToken);
        Admin user = adminService.selectAll(new Admin()).stream()
                .filter(item -> userId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (user == null) {
            return Result.error("401", "用户不存在");
        }
        checkUserEnabled(user);

        List<String> permissions = RolePermissionUtils.getPermissionsByRole(user.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", jwtUtils.generateAccessToken(
                user.getId(),
                user.getUsername(),
                RolePermissionUtils.normalizeRole(user.getRole()),
                permissions
        ));
        data.put("refreshToken", jwtUtils.generateRefreshToken(user.getId()));
        data.put("expiresIn", jwtUtils.getAccessTokenExpiration());
        data.put("userInfo", buildUserInfo(user, permissions));

        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result logout() {
        // 无Redis/黑名单版本保持后端无状态，前端清理本地token即可。
        return Result.success("登出成功");
    }

    @GetMapping("/userInfo")
    public Result userInfo(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("401", "未认证，请先登录");
        }

        Admin user = adminService.selectAll(new Admin()).stream()
                .filter(item -> userId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (user == null) {
            return Result.error("401", "用户不存在");
        }
        checkUserEnabled(user);

        return Result.success(buildUserInfo(user, RolePermissionUtils.getPermissionsByRole(user.getRole())));
    }

    private Admin toAdmin(LoginRequest request) {
        Admin admin = new Admin();
        admin.setUsername(request.getUsername());
        admin.setPassword(request.getPassword());
        return admin;
    }

    private void checkUserEnabled(Admin user) {
        if (isStatusColumnAvailable() && user.getStatus() != null && user.getStatus() == 0) {
            throw new CustomerException("403", "账号已被禁用");
        }
    }

    private boolean isStatusColumnAvailable() {
        if (hasStatusColumn == null) {
            hasStatusColumn = adminService.hasColumn("status");
        }
        return hasStatusColumn;
    }

    private Map<String, Object> buildUserInfo(Admin user, List<String> permissions) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("name", user.getName());
        userInfo.put("role", RolePermissionUtils.normalizeRole(user.getRole()));
        userInfo.put("permissions", permissions);
        return userInfo;
    }

    private void upgradePasswordIfNeeded(Admin user, String rawPassword) {
        String storedPassword = user.getPassword();
        if (storedPassword != null && !storedPassword.matches("^\\$2[aby]\\$.{56}$")) {
            Admin updateAdmin = new Admin();
            updateAdmin.setId(user.getId());
            updateAdmin.setPassword(passwordEncoder.encode(rawPassword));
            adminService.updatePasswordOnly(updateAdmin);
            user.setPassword(updateAdmin.getPassword());
        }
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RefreshRequest {
        private String refreshToken;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }
}
