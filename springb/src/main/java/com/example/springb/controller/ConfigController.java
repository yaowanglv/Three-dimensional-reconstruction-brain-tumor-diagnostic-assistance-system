package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.entity.ModelConfig;
import com.example.springb.service.AIService;
import com.example.springb.service.ConfigService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg");

    private final ConfigService configService;
    private final AIService aiService;

    public ConfigController(ConfigService configService, AIService aiService) {
        this.configService = configService;
        this.aiService = aiService;
    }

    @GetMapping("/branding")
    public Result getBranding() {
        return Result.success(configService.getPublicBranding());
    }

    @GetMapping("/logo")
    public ResponseEntity<Resource> getLogo() throws IOException {
        String logoPath = configService.getManagerLogoPath();
        if (logoPath == null || logoPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(logoPath).getCanonicalFile();
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        MediaType mediaType = MediaTypeFactory.getMediaType(file.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .contentLength(file.length())
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/fs/browse")
    public Result browseFileSystem(@RequestParam(required = false) String path,
                                   @RequestParam(defaultValue = "true") boolean directoriesOnly,
                                   @RequestParam(defaultValue = "false") boolean imageFilesOnly) {
        try {
            return Result.success(buildBrowseResult(path, directoriesOnly, imageFilesOnly));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/models")
    public Result getModels(@RequestParam String pageType) {
        try {
            List<ModelConfig> models = configService.getModels(pageType);
            return Result.success(models);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/models/folder")
    @PreAuthorize("hasRole('ADMIN')")
    public Result addModelFolder(@RequestBody Map<String, Object> request) {
        try {
            return Result.success(configService.scanAndSaveModels(
                    stringValue(request.get("pageType")),
                    stringValue(request.get("folderPath")),
                    booleanValue(request.get("includeLastPt"))
            ));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/models/scan")
    @PreAuthorize("hasRole('ADMIN')")
    public Result rescanModels(@RequestBody Map<String, Object> request) {
        try {
            return Result.success(configService.rescanModels(
                    stringValue(request.get("pageType")),
                    stringValue(request.get("folderPath")),
                    booleanValue(request.get("includeLastPt"))
            ));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/models/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result deleteModel(@PathVariable Long id) {
        try {
            configService.deleteModel(id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/models/{id}/display-name")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateModelDisplayName(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            configService.updateModelDisplayName(id, request.get("displayName"));
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/thresholds")
    public Result getThresholds() {
        return Result.success(configService.getThresholds());
    }

    @PutMapping("/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateThresholds(@RequestBody Map<String, Double> thresholds) {
        try {
            configService.updateThresholds(thresholds);
            return Result.success(configService.getThresholds());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/global")
    public Result getGlobalConfig() {
        return Result.success(configService.getGlobalConfig());
    }

    @PutMapping("/global")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateGlobalConfig(@RequestBody Map<String, Object> config) {
        try {
            configService.updateGlobalConfig(config);
            return Result.success(configService.getGlobalConfig());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/ai")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAiConfig() {
        return Result.success(aiService.getAiConfig());
    }

    @PutMapping("/ai")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateAiConfig(@RequestBody Map<String, Object> config) {
        try {
            aiService.updateAiConfig(config);
            return Result.success(aiService.getAiConfig());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private Map<String, Object> buildBrowseResult(String path, boolean directoriesOnly, boolean imageFilesOnly) {
        File current = normalizeBrowseTarget(path);
        File parent = current.getParentFile();
        File[] children = current.listFiles();

        List<Map<String, Object>> items = new ArrayList<>();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    items.add(buildItem(child, "directory"));
                    continue;
                }
                if (!directoriesOnly && shouldIncludeFile(child, imageFilesOnly)) {
                    items.add(buildItem(child, "file"));
                }
            }
        }

        items.sort(Comparator
                .comparing((Map<String, Object> item) -> !"directory".equals(item.get("type")))
                .thenComparing(item -> String.valueOf(item.get("name")).toLowerCase(Locale.ROOT)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", current.getAbsolutePath());
        result.put("parentPath", parent == null ? "" : parent.getAbsolutePath());
        result.put("exists", current.exists());
        result.put("readable", current.canRead());
        result.put("rootOptions", listRootOptions());
        result.put("items", items);
        return result;
    }

    private List<Map<String, Object>> listRootOptions() {
        List<Map<String, Object>> roots = new ArrayList<>();
        for (File root : File.listRoots()) {
            roots.add(buildItem(root, "root"));
        }
        return roots;
    }

    private boolean shouldIncludeFile(File file, boolean imageFilesOnly) {
        if (!file.isFile() || !file.canRead()) {
            return false;
        }
        if (!imageFilesOnly) {
            return true;
        }
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= name.length() - 1) {
            return false;
        }
        String extension = name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(extension);
    }

    private Map<String, Object> buildItem(File file, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", file.getName().isBlank() ? file.getAbsolutePath() : file.getName());
        item.put("path", file.getAbsolutePath());
        item.put("type", type);
        item.put("directory", file.isDirectory());
        item.put("readable", file.canRead());
        return item;
    }

    private File normalizeBrowseTarget(String path) {
        try {
            if (path == null || path.isBlank()) {
                File[] roots = File.listRoots();
                if (roots == null || roots.length == 0) {
                    throw new IllegalArgumentException("No filesystem roots available");
                }
                return roots[0].getCanonicalFile();
            }

            if (path.contains("\0")) {
                throw new IllegalArgumentException("Path is invalid");
            }

            File target = new File(path).getCanonicalFile();
            if (!target.exists()) {
                throw new IllegalArgumentException("Path does not exist: " + target.getAbsolutePath());
            }
            if (target.isFile()) {
                target = target.getParentFile();
            }
            if (target == null || !target.isDirectory()) {
                throw new IllegalArgumentException("Path is not a directory: " + new File(path).getAbsolutePath());
            }
            if (!target.canRead()) {
                throw new IllegalArgumentException("Directory is not readable: " + target.getAbsolutePath());
            }
            return target;
        } catch (IOException e) {
            throw new IllegalArgumentException("Path is invalid: " + path);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }
}
