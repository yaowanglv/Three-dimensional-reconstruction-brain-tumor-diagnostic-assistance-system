package com.example.springb.security;

import java.util.List;
import java.util.Locale;

public final class RolePermissionUtils {

    private RolePermissionUtils() {
    }

    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> getPermissionsByRole(String role) {
        String normalizedRole = normalizeRole(role);
        switch (normalizedRole) {
            case "admin":
                return List.of(
                        "user:manage",
                        "detect:create",
                        "detect:view",
                        "detect:delete",
                        "data:view",
                        "data:export",
                        "system:config"
                );
            case "doctor":
                return List.of(
                        "detect:create",
                        "detect:view",
                        "detect:delete",
                        "data:view",
                        "data:export"
                );
            case "researcher":
                return List.of(
                        "detect:view",
                        "data:view",
                        "data:export"
                );
            default:
                return List.of("data:view");
        }
    }
}
