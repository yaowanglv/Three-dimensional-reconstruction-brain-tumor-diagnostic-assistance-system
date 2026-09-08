package com.example.springb.service;

import com.example.springb.config.ModelScanner;
import com.example.springb.entity.ModelConfig;
import com.example.springb.mapper.ConfigMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ConfigService {
    private static final Set<String> PAGE_TYPES = Set.of("detect", "mask", "multi");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg");
    private static final double MIN_THRESHOLD = 0.05;
    private static final double MAX_THRESHOLD = 0.95;
    private static final double THRESHOLD_STEP = 0.1;
    private static final String DEFAULT_MANAGER_LOGO_PATH = "";
    private static final String DEFAULT_LOGIN_PAGE_TITLE = "脑诊智析";
    private static final String DEFAULT_LOGIN_BRAND_TITLE = "大模型驱动的脑肿瘤智能辅助诊断与分析系统";
    private static final String DEFAULT_LOGIN_BRAND_SUBTITLE = "脑诊智析";
    private static final String DEFAULT_MANAGER_BRAND_TITLE = "脑诊智析——大模型驱动的脑肿瘤智能辅助诊断与分析系统";
    private static final String DEFAULT_LOGIN_ROUTE_KEY = "dataview";
    private static final List<String> MANAGER_ROUTE_KEYS = List.of(
            "admin",
            "dataview",
            "history",
            "config",
            "detect",
            "mask",
            "multi",
            "threedim"
    );
    private static final Map<Integer, String> DEFAULT_THREEDIM_LABEL_NAMES = Map.of(
            1, "坏死肿瘤核心",
            2, "瘤周水肿",
            3, "增强肿瘤"
    );
    private static final Map<String, String> DEFAULT_MANAGER_MENU_TITLES = Map.of(
            "user", "用户管理",
            "data", "数据管理",
            "diagnosis", "脑肿瘤智能辅助诊断与分析"
    );
    private static final Map<String, String> DEFAULT_MANAGER_ROUTE_TITLES = Map.ofEntries(
            Map.entry("admin", "管理员信息"),
            Map.entry("dataview", "数据可视化"),
            Map.entry("history", "检测历史"),
            Map.entry("config", "系统配置"),
            Map.entry("detect", "快速辅助诊断与分析"),
            Map.entry("mask", "精准辅助分割与分析"),
            Map.entry("multi", "批量图像分割"),
            Map.entry("threedim", "3D脑肿瘤可视化")
    );
    private static final Map<String, Boolean> DEFAULT_MANAGER_ROUTE_VISIBILITY = Map.ofEntries(
            Map.entry("admin", true),
            Map.entry("dataview", true),
            Map.entry("history", true),
            Map.entry("config", true),
            Map.entry("detect", true),
            Map.entry("mask", true),
            Map.entry("multi", true),
            Map.entry("threedim", true)
    );

    private final ConfigMapper configMapper;
    private final ModelScanner modelScanner;

    public ConfigService(ConfigMapper configMapper, ModelScanner modelScanner) {
        this.configMapper = configMapper;
        this.modelScanner = modelScanner;
    }

    @PostConstruct
    public void initTables() {
        configMapper.createModelConfigTable();
        ensureDisplayNameColumn();
        configMapper.backfillDisplayNames();
        configMapper.createSystemConfigTable();
        configMapper.insertDefaultConfigs();
        ensureSegmentServiceDefaults();
        ensureThreedimLabelDefaults();
        migrateUnetSegmentUrlDefault();
    }

    public List<ModelConfig> getModels(String pageType) {
        return configMapper.selectByPageType(normalizePageType(pageType));
    }

    public List<ModelConfig> scanAndSaveModels(String pageType, String folderPath) {
        return scanAndSaveModels(pageType, folderPath, false);
    }

    public List<ModelConfig> scanAndSaveModels(String pageType, String folderPath, boolean includeLastPt) {
        String normalizedPageType = normalizePageType(pageType);
        File folder = normalizeFolder(folderPath);

        List<ModelConfig> scannedModels = modelScanner.scan(normalizedPageType, folder, includeLastPt);
        for (ModelConfig model : scannedModels) {
            ModelConfig existing = configMapper.selectByPageTypeAndPath(normalizedPageType, model.getModelPath());
            if (existing == null) {
                configMapper.insertModel(model);
            }
        }
        return getModels(normalizedPageType);
    }

    public List<ModelConfig> rescanModels(String pageType, String folderPath) {
        return rescanModels(pageType, folderPath, false);
    }

    public List<ModelConfig> rescanModels(String pageType, String folderPath, boolean includeLastPt) {
        String normalizedPageType = normalizePageType(pageType);
        File folder = normalizeFolder(folderPath);
        Map<String, String> existingDisplayNames = new HashMap<>();
        for (ModelConfig model : configMapper.selectByPageType(normalizedPageType)) {
            if (folder.getAbsolutePath().equals(model.getFolderPath())) {
                existingDisplayNames.put(model.getModelPath(), model.getDisplayName());
            }
        }
        configMapper.deleteByPageTypeAndFolder(normalizedPageType, folder.getAbsolutePath());
        List<ModelConfig> models = scanAndSaveModels(normalizedPageType, folder.getAbsolutePath(), includeLastPt);
        for (ModelConfig model : models) {
            String displayName = existingDisplayNames.get(model.getModelPath());
            if (displayName != null && !displayName.isBlank()) {
                configMapper.updateDisplayName(model.getId(), displayName);
            }
        }
        return getModels(normalizedPageType);
    }

    public void deleteModel(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Model ID cannot be empty");
        }
        configMapper.deleteById(id);
    }

    public void updateModelDisplayName(Long id, String displayName) {
        if (id == null) {
            throw new IllegalArgumentException("Model ID cannot be empty");
        }
        configMapper.updateDisplayName(id, normalizeDisplayName(displayName));
    }

    public Map<String, Double> getThresholds() {
        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("detect", getThreshold("detect", 0.45));
        thresholds.put("mask", getThreshold("mask", 0.45));
        thresholds.put("multi", getThreshold("multi", 0.25));
        return thresholds;
    }

    public double getThreshold(String pageType, double defaultValue) {
        return getDoubleConfig("threshold." + normalizePageType(pageType), defaultValue);
    }

    public void updateThresholds(Map<String, Double> thresholds) {
        if (thresholds == null) {
            return;
        }
        updateThresholdIfPresent(thresholds, "detect", "Detect page default threshold");
        updateThresholdIfPresent(thresholds, "mask", "Mask page default threshold");
        updateThresholdIfPresent(thresholds, "multi", "Multi page default threshold");
    }

    public Map<String, Object> getGlobalConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("backendUrl", getBackendUrl());
        config.put("pythonDetectUrl", getPythonDetectUrl());
        config.put("pythonSegmentUrl", getPythonSegmentUrl());
        config.put("pythonYoloSegmentUrl", getPythonYoloSegmentUrl());
        config.put("pythonUnetSegmentUrl", getPythonUnetSegmentUrl());
        config.put("defaultMaskSegmentService", getDefaultMaskSegmentService());
        config.put("defaultLoginRouteKey", getDefaultLoginRouteKey());
        config.put("autoRefreshWorkPages", isAutoRefreshWorkPages());
        config.put("loginPageTitle", getLoginPageTitle());
        config.put("loginBrandTitle", getLoginBrandTitle());
        config.put("loginBrandSubtitle", getLoginBrandSubtitle());
        config.put("threedimLabelNames", getThreedimLabelNames());
        config.put("managerBrandTitle", getManagerBrandTitle());
        config.put("managerLogoVisible", isManagerLogoVisible());
        config.put("tabLogoVisible", isTabLogoVisible());
        config.put("managerLogoPath", getManagerLogoPath());
        config.put("multiFavorites", getMultiFavorites());
        config.put("threedimFavorites", getThreedimFavorites());
        config.put("managerMenuTitles", getManagerMenuTitles());
        config.put("managerRoutes", getManagerRoutes());
        return config;
    }

    public Map<String, Object> getPublicBranding() {
        Map<String, Object> data = new HashMap<>();
        data.put("loginPageTitle", getLoginPageTitle());
        data.put("loginBrandTitle", getLoginBrandTitle());
        data.put("loginBrandSubtitle", getLoginBrandSubtitle());
        data.put("managerBrandTitle", getManagerBrandTitle());
        data.put("managerLogoVisible", isManagerLogoVisible());
        data.put("tabLogoVisible", isTabLogoVisible());
        data.put("hasCustomLogo", isManagerLogoVisible() && !getManagerLogoPath().isBlank());
        return data;
    }

    public void updateGlobalConfig(Map<String, Object> config) {
        if (config == null) {
            return;
        }
        if (config.containsKey("backendUrl")) {
            upsertUrlConfig("global.backend_url", String.valueOf(config.get("backendUrl")), "Backend API URL");
        }
        if (config.containsKey("pythonDetectUrl")) {
            upsertUrlConfig("global.python_detect_url", String.valueOf(config.get("pythonDetectUrl")), "Python detect service URL");
        }
        if (config.containsKey("pythonSegmentUrl")) {
            upsertUrlConfig("global.python_segment_url", String.valueOf(config.get("pythonSegmentUrl")), "Python segment service URL");
        }
        if (config.containsKey("pythonYoloSegmentUrl")) {
            upsertUrlConfig("global.python_yolo_segment_url", String.valueOf(config.get("pythonYoloSegmentUrl")), "YOLO segment service URL");
        }
        if (config.containsKey("pythonUnetSegmentUrl")) {
            upsertUrlConfig("global.python_unet_segment_url", String.valueOf(config.get("pythonUnetSegmentUrl")), "UNet segment service URL");
        }
        if (config.containsKey("defaultMaskSegmentService")) {
            configMapper.upsertConfig(
                    "global.default_mask_segment_service",
                    normalizeSegmentService(config.get("defaultMaskSegmentService")),
                    "Default Mask page segment service"
            );
        }
        if (config.containsKey("defaultLoginRouteKey")) {
            configMapper.upsertConfig(
                    "global.default_login_route_key",
                    normalizeManagerRouteKey(config.get("defaultLoginRouteKey")),
                    "Default route after login"
            );
        }
        if (config.containsKey("autoRefreshWorkPages")) {
            configMapper.upsertConfig(
                    "global.auto_refresh_work_pages",
                    normalizeBoolean(config.get("autoRefreshWorkPages")),
                    "Work pages auto refresh"
            );
        }
        if (config.containsKey("loginPageTitle")) {
            configMapper.upsertConfig(
                    "global.login_page_title",
                    normalizeTitle(config.get("loginPageTitle"), DEFAULT_LOGIN_PAGE_TITLE, 120, "Login page title"),
                    "Login page browser title"
            );
        }
        if (config.containsKey("loginBrandTitle")) {
            configMapper.upsertConfig(
                    "global.login_brand_title",
                    normalizeTitle(config.get("loginBrandTitle"), DEFAULT_LOGIN_BRAND_TITLE, 200, "Login brand title"),
                    "Login page black title"
            );
        }
        if (config.containsKey("loginBrandSubtitle")) {
            configMapper.upsertConfig(
                    "global.login_brand_subtitle",
                    normalizeTitle(config.get("loginBrandSubtitle"), DEFAULT_LOGIN_BRAND_SUBTITLE, 120, "Login brand subtitle"),
                    "Login page color title"
            );
        }
        if (config.containsKey("threedimLabelNames") && config.get("threedimLabelNames") instanceof Map<?, ?> labelNames) {
            updateThreedimLabelNames(labelNames);
        }
        if (config.containsKey("managerBrandTitle")) {
            configMapper.upsertConfig(
                    "global.manager_brand_title",
                    normalizeTitle(config.get("managerBrandTitle"), DEFAULT_MANAGER_BRAND_TITLE, 200, "Manager brand title"),
                    "Manager brand title"
            );
        }
        if (config.containsKey("managerLogoVisible")) {
            configMapper.upsertConfig(
                    "global.manager_logo_visible",
                    normalizeBoolean(config.get("managerLogoVisible")),
                    "Manager logo visibility"
            );
        }
        if (config.containsKey("tabLogoVisible")) {
            configMapper.upsertConfig(
                    "global.tab_logo_visible",
                    normalizeBoolean(config.get("tabLogoVisible")),
                    "Browser tab logo visibility"
            );
        }
        if (config.containsKey("managerLogoPath")) {
            configMapper.upsertConfig(
                    "global.manager_logo_path",
                    normalizeImageFilePath(config.get("managerLogoPath"), true),
                    "Manager logo path"
            );
        }
        if (config.containsKey("multiFavorites") && config.get("multiFavorites") instanceof Map<?, ?> multiFavorites) {
            updateMultiFavorites(multiFavorites);
        }
        if (config.containsKey("threedimFavorites") && config.get("threedimFavorites") instanceof List<?> threedimFavorites) {
            updateThreedimFavorites(threedimFavorites);
        }
        if (config.containsKey("managerMenuTitles") && config.get("managerMenuTitles") instanceof Map<?, ?> menuTitles) {
            updateManagerMenuTitles(menuTitles);
        }
        if (config.containsKey("managerRoutes") && config.get("managerRoutes") instanceof Map<?, ?> managerRoutes) {
            updateManagerRoutes(managerRoutes);
        }
    }

    public String getBackendUrl() {
        return getStringConfig("global.backend_url", "http://localhost:9527");
    }

    public String getPythonDetectUrl() {
        return getStringConfig("global.python_detect_url", "http://localhost:5001");
    }

    public String getPythonSegmentUrl() {
        return getStringConfig("global.python_segment_url", "http://localhost:3408");
    }

    public String getPythonYoloSegmentUrl() {
        return getStringConfig("global.python_yolo_segment_url", getPythonDetectUrl());
    }

    public String getPythonUnetSegmentUrl() {
        return getStringConfig("global.python_unet_segment_url", getPythonSegmentUrl());
    }

    public String getDefaultMaskSegmentService() {
        return normalizeSegmentService(getStringConfig("global.default_mask_segment_service", "unet"));
    }

    public String getDefaultLoginRouteKey() {
        return normalizeManagerRouteKey(getStringConfig("global.default_login_route_key", DEFAULT_LOGIN_ROUTE_KEY));
    }

    public boolean isAutoRefreshWorkPages() {
        return Boolean.parseBoolean(getStringConfig("global.auto_refresh_work_pages", "false"));
    }

    public String getLoginPageTitle() {
        return getStringConfig("global.login_page_title", DEFAULT_LOGIN_PAGE_TITLE);
    }

    public String getLoginBrandTitle() {
        return getStringConfig("global.login_brand_title", DEFAULT_LOGIN_BRAND_TITLE);
    }

    public String getLoginBrandSubtitle() {
        return getStringConfig("global.login_brand_subtitle", DEFAULT_LOGIN_BRAND_SUBTITLE);
    }

    public Map<Integer, String> getThreedimLabelNames() {
        Map<Integer, String> labels = new HashMap<>();
        for (Map.Entry<Integer, String> entry : DEFAULT_THREEDIM_LABEL_NAMES.entrySet()) {
            labels.put(entry.getKey(), getStringConfig("global.threedim_label_" + entry.getKey(), entry.getValue()));
        }
        return labels;
    }

    public String getManagerBrandTitle() {
        return getStringConfig("global.manager_brand_title", DEFAULT_MANAGER_BRAND_TITLE);
    }

    public String getManagerLogoPath() {
        return getStringConfig("global.manager_logo_path", DEFAULT_MANAGER_LOGO_PATH);
    }

    public boolean isManagerLogoVisible() {
        return Boolean.parseBoolean(getStringConfig("global.manager_logo_visible", "true"));
    }

    public boolean isTabLogoVisible() {
        return Boolean.parseBoolean(getStringConfig("global.tab_logo_visible", "true"));
    }

    public Map<String, List<String>> getMultiFavorites() {
        Map<String, List<String>> favorites = new LinkedHashMap<>();
        favorites.put("input", parsePathList(getStringConfig("global.multi_favorites_input", "")));
        favorites.put("output", parsePathList(getStringConfig("global.multi_favorites_output", "")));
        return favorites;
    }

    public List<String> getThreedimFavorites() {
        return parsePathList(getStringConfig("global.threedim_favorites", ""));
    }

    public Map<String, String> getManagerMenuTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("user", getStringConfig("global.manager_menu_user_title", DEFAULT_MANAGER_MENU_TITLES.get("user")));
        titles.put("data", getStringConfig("global.manager_menu_data_title", DEFAULT_MANAGER_MENU_TITLES.get("data")));
        titles.put("diagnosis", getStringConfig("global.manager_menu_diagnosis_title", DEFAULT_MANAGER_MENU_TITLES.get("diagnosis")));
        return titles;
    }

    public Map<String, Map<String, Object>> getManagerRoutes() {
        Map<String, Map<String, Object>> routes = new LinkedHashMap<>();
        for (String routeKey : MANAGER_ROUTE_KEYS) {
            Map<String, Object> route = new LinkedHashMap<>();
            route.put("title", getStringConfig(routeTitleKey(routeKey), DEFAULT_MANAGER_ROUTE_TITLES.get(routeKey)));
            boolean visible = Boolean.parseBoolean(getStringConfig(
                    routeVisibleKey(routeKey),
                    String.valueOf(DEFAULT_MANAGER_ROUTE_VISIBILITY.getOrDefault(routeKey, true))
            ));
            if ("config".equals(routeKey)) {
                visible = true;
            }
            route.put("visible", visible);
            routes.put(routeKey, route);
        }
        return routes;
    }

    public String normalizeSegmentService(Object value) {
        String service = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if ("yolo".equals(service) || "unet".equals(service)) {
            return service;
        }
        return "unet";
    }

    private void updateThresholdIfPresent(Map<String, Double> thresholds, String pageType, String description) {
        if (!thresholds.containsKey(pageType)) {
            return;
        }
        double threshold = normalizeThreshold(thresholds.get(pageType));
        configMapper.upsertConfig("threshold." + pageType, formatDecimal(threshold), description);
    }

    private double normalizeThreshold(Double value) {
        if (value == null) {
            throw new IllegalArgumentException("Threshold cannot be empty");
        }
        double rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (rounded < MIN_THRESHOLD || rounded > MAX_THRESHOLD) {
            throw new IllegalArgumentException("Threshold must be between 0.05 and 0.95");
        }
        double stepIndex = (rounded - MIN_THRESHOLD) / THRESHOLD_STEP;
        if (Math.abs(stepIndex - Math.round(stepIndex)) > 0.000001) {
            throw new IllegalArgumentException("Threshold step must be 0.1");
        }
        return rounded;
    }

    private String formatDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private void upsertUrlConfig(String key, String value, String description) {
        configMapper.upsertConfig(key, normalizeUrl(value), description);
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Service URL cannot be empty");
        }
        String url = value.trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("Service URL must start with http:// or https://");
        }
        return url.replaceAll("/+$", "");
    }

    private String normalizeBoolean(Object value) {
        String normalized = String.valueOf(value).trim();
        return String.valueOf("true".equalsIgnoreCase(normalized) || "1".equals(normalized));
    }

    private void updateThreedimLabelNames(Map<?, ?> labelNames) {
        for (Integer labelId : DEFAULT_THREEDIM_LABEL_NAMES.keySet()) {
            Object rawValue = labelNames.containsKey(labelId) ? labelNames.get(labelId) : labelNames.get(String.valueOf(labelId));
            if (rawValue == null) {
                continue;
            }
            configMapper.upsertConfig(
                    "global.threedim_label_" + labelId,
                    normalizeLabelName(rawValue, DEFAULT_THREEDIM_LABEL_NAMES.get(labelId)),
                    "3D tumor label " + labelId + " display name"
            );
        }
    }

    private void updateManagerMenuTitles(Map<?, ?> menuTitles) {
        updateMenuTitleIfPresent(menuTitles, "user", "global.manager_menu_user_title");
        updateMenuTitleIfPresent(menuTitles, "data", "global.manager_menu_data_title");
        updateMenuTitleIfPresent(menuTitles, "diagnosis", "global.manager_menu_diagnosis_title");
    }

    private void updateMultiFavorites(Map<?, ?> multiFavorites) {
        configMapper.upsertConfig(
                "global.multi_favorites_input",
                joinPathList(extractPathList(multiFavorites.get("input"))),
                "Multi input favorite directories"
        );
        configMapper.upsertConfig(
                "global.multi_favorites_output",
                joinPathList(extractPathList(multiFavorites.get("output"))),
                "Multi output favorite directories"
        );
    }

    private void updateThreedimFavorites(List<?> threedimFavorites) {
        configMapper.upsertConfig(
                "global.threedim_favorites",
                joinPathList(extractPathList(threedimFavorites)),
                "Threedim favorite directories"
        );
    }

    private void updateMenuTitleIfPresent(Map<?, ?> menuTitles, String key, String configKey) {
        Object rawValue = menuTitles.containsKey(key) ? menuTitles.get(key) : null;
        if (rawValue == null) {
            return;
        }
        configMapper.upsertConfig(
                configKey,
                normalizeTitle(rawValue, DEFAULT_MANAGER_MENU_TITLES.get(key), 80, "Menu title"),
                "Manager menu title: " + key
        );
    }

    private void updateManagerRoutes(Map<?, ?> managerRoutes) {
        for (String routeKey : MANAGER_ROUTE_KEYS) {
            Object rawRoute = managerRoutes.containsKey(routeKey) ? managerRoutes.get(routeKey) : null;
            if (!(rawRoute instanceof Map<?, ?> routeConfig)) {
                continue;
            }
            if (routeConfig.containsKey("title")) {
                configMapper.upsertConfig(
                        routeTitleKey(routeKey),
                        normalizeTitle(routeConfig.get("title"), DEFAULT_MANAGER_ROUTE_TITLES.get(routeKey), 80, "Route title"),
                        "Manager route title: " + routeKey
                );
            }
            if (routeConfig.containsKey("visible")) {
                boolean visible = normalizeVisible(routeConfig.get("visible"), DEFAULT_MANAGER_ROUTE_VISIBILITY.getOrDefault(routeKey, true));
                if ("config".equals(routeKey)) {
                    visible = true;
                }
                configMapper.upsertConfig(
                        routeVisibleKey(routeKey),
                        String.valueOf(visible),
                        "Manager route visibility: " + routeKey
                );
            }
        }
    }

    private String routeTitleKey(String routeKey) {
        return "global.manager_route_" + routeKey + "_title";
    }

    private String routeVisibleKey(String routeKey) {
        return "global.manager_route_" + routeKey + "_visible";
    }

    private boolean normalizeVisible(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private String normalizeManagerRouteKey(Object value) {
        String routeKey = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (routeKey.isBlank()) {
            return DEFAULT_LOGIN_ROUTE_KEY;
        }
        if (!MANAGER_ROUTE_KEYS.contains(routeKey)) {
            throw new IllegalArgumentException("Default login route is invalid");
        }
        return routeKey;
    }

    private String normalizeLabelName(Object value, String defaultValue) {
        String label = String.valueOf(value == null ? "" : value).trim();
        if (label.isBlank()) {
            return defaultValue;
        }
        if (label.length() > 50) {
            throw new IllegalArgumentException("3D label name cannot exceed 50 characters");
        }
        return label;
    }

    private String normalizeTitle(Object value, String defaultValue, int maxLength, String fieldName) {
        String title = String.valueOf(value == null ? "" : value).trim();
        if (title.isBlank()) {
            return defaultValue;
        }
        if (title.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }
        return title;
    }

    private String normalizeImageFilePath(Object value, boolean allowBlank) {
        String path = String.valueOf(value == null ? "" : value).trim();
        if (path.isBlank()) {
            return allowBlank ? "" : path;
        }
        if (path.contains("\0")) {
            throw new IllegalArgumentException("Logo path is invalid");
        }
        try {
            File file = new File(path).getCanonicalFile();
            if (!file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("Logo file does not exist: " + file.getAbsolutePath());
            }
            if (!file.canRead()) {
                throw new IllegalArgumentException("Logo file is not readable: " + file.getAbsolutePath());
            }
            String extension = getFileExtension(file.getName());
            if (!IMAGE_EXTENSIONS.contains(extension)) {
                throw new IllegalArgumentException("Logo file must be a common image format");
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Logo path is invalid: " + path);
        }
    }

    private List<String> extractPathList(Object value) {
        List<String> paths = new ArrayList<>();
        if (!(value instanceof List<?> rawList)) {
            return paths;
        }
        for (Object item : rawList) {
            String path = String.valueOf(item == null ? "" : item).trim();
            if (path.isBlank()) {
                continue;
            }
            if (path.length() > 500) {
                throw new IllegalArgumentException("Favorite path cannot exceed 500 characters");
            }
            if (path.contains("\0")) {
                throw new IllegalArgumentException("Favorite path is invalid");
            }
            if (!paths.contains(path)) {
                paths.add(path);
            }
            if (paths.size() >= 8) {
                break;
            }
        }
        return paths;
    }

    private List<String> parsePathList(String value) {
        List<String> paths = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return paths;
        }
        for (String item : value.split("\\r?\\n")) {
            String path = item.trim();
            if (!path.isBlank() && !paths.contains(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private String joinPathList(List<String> paths) {
        return String.join("\n", paths);
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String getStringConfig(String key, String defaultValue) {
        String value = configMapper.selectValueByKey(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private double getDoubleConfig(String key, double defaultValue) {
        String value = configMapper.selectValueByKey(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String normalizePageType(String pageType) {
        if (pageType == null || pageType.isBlank()) {
            throw new IllegalArgumentException("Page type cannot be empty");
        }
        String normalized = pageType.trim().toLowerCase(Locale.ROOT);
        if (!PAGE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Page type must be detect, mask or multi");
        }
        return normalized;
    }

    private File normalizeFolder(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("Model folder cannot be empty");
        }
        if (folderPath.contains("\0")) {
            throw new IllegalArgumentException("Model folder path is invalid");
        }

        try {
            File folder = new File(folderPath.trim()).getCanonicalFile();
            if (!folder.exists() || !folder.isDirectory()) {
                throw new IllegalArgumentException("Directory does not exist: " + folder.getAbsolutePath());
            }
            if (!folder.canRead()) {
                throw new IllegalArgumentException("Directory is not readable: " + folder.getAbsolutePath());
            }
            return folder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Model folder path is invalid: " + folderPath);
        }
    }

    private void ensureDisplayNameColumn() {
        try {
            configMapper.addDisplayNameColumn();
        } catch (Exception ignored) {
            // Existing installations already have the column.
        }
    }

    private void migrateUnetSegmentUrlDefault() {
        String segmentUrl = configMapper.selectValueByKey("global.python_segment_url");
        if (segmentUrl == null || segmentUrl.isBlank() || "http://localhost:5001".equals(segmentUrl.trim())) {
            configMapper.upsertConfig("global.python_segment_url", "http://localhost:3408", "UNet分割服务地址");
        }
    }

    private void ensureSegmentServiceDefaults() {
        if (configMapper.selectValueByKey("global.python_yolo_segment_url") == null) {
            configMapper.upsertConfig("global.python_yolo_segment_url", getPythonDetectUrl(), "YOLO segment service URL");
        }
        if (configMapper.selectValueByKey("global.python_unet_segment_url") == null) {
            configMapper.upsertConfig("global.python_unet_segment_url", getPythonSegmentUrl(), "UNet segment service URL");
        }
        if (configMapper.selectValueByKey("global.default_mask_segment_service") == null) {
            configMapper.upsertConfig("global.default_mask_segment_service", "unet", "Default Mask page segment service");
        }
    }

    private void ensureThreedimLabelDefaults() {
        for (Map.Entry<Integer, String> entry : DEFAULT_THREEDIM_LABEL_NAMES.entrySet()) {
            String key = "global.threedim_label_" + entry.getKey();
            if (configMapper.selectValueByKey(key) == null) {
                configMapper.upsertConfig(key, entry.getValue(), "3D tumor label " + entry.getKey() + " display name");
            }
        }
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be empty");
        }
        String normalized = displayName.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("Display name cannot exceed 200 characters");
        }
        return normalized;
    }
}
