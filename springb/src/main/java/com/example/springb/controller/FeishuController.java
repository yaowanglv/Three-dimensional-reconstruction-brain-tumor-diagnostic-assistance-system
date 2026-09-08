package com.example.springb.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.springb.common.Result;
import com.example.springb.mapper.ConfigMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 飞书云文档文件上传控制器
 * API: POST /drive/v1/files/upload_all
 */
@RestController
@RequestMapping("/feishu")
public class FeishuController {

    private static final Logger log = LoggerFactory.getLogger(FeishuController.class);
    private static final String FEISHU_USER_TOKEN_KEY_PREFIX = "user.feishu_token.";

    @Value("${feishu.app-id:}")
    private String appId;

    @Value("${feishu.app-secret:}")
    private String appSecret;

    // 目标文件夹token（上传到云空间指定文件夹）
    @Value("${feishu.parent-node:}")
    private String parentNode;

    @Resource
    private ConfigMapper configMapper;

    private static final String FEISHU_API_BASE = "https://open.feishu.cn/open-apis";
    private static final String TOKEN_URL = FEISHU_API_BASE + "/auth/v3/tenant_access_token/internal";
    private static final String UPLOAD_URL = FEISHU_API_BASE + "/drive/v1/files/upload_all";

    @GetMapping("/user-token")
    public Result getUserToken(HttpServletRequest request) {
        Integer userId = resolveUserId(request);
        if (userId == null) {
            return Result.error("401", "未认证，请先登录");
        }

        String token = configMapper.selectValueByKey(buildUserTokenConfigKey(userId));
        Map<String, Object> data = new HashMap<>();
        data.put("token", token == null ? "" : token);
        return Result.success(data);
    }

    @PutMapping("/user-token")
    public Result saveUserToken(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        Integer userId = resolveUserId(request);
        if (userId == null) {
            return Result.error("401", "未认证，请先登录");
        }

        String token = body == null ? "" : String.valueOf(body.getOrDefault("token", "")).trim();
        if (token.isEmpty()) {
            return Result.error("请输入飞书权限 token");
        }

        configMapper.upsertConfig(
                buildUserTokenConfigKey(userId),
                token,
                "Feishu user access token for user " + userId
        );

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        return Result.success(data);
    }

    @DeleteMapping("/user-token")
    public Result clearUserToken(HttpServletRequest request) {
        Integer userId = resolveUserId(request);
        if (userId == null) {
            return Result.error("401", "未认证，请先登录");
        }

        configMapper.deleteByConfigKey(buildUserTokenConfigKey(userId));
        return Result.success();
    }

    /**
     * 上传文件到飞书云文档 - 自动获取tenant_access_token
     */
    @PostMapping("/upload")
    public Result uploadToFeishu(HttpServletRequest request) {
        log.info("收到飞书上传请求，Content-Type: {}", request.getContentType());

        if (!(request instanceof MultipartHttpServletRequest)) {
            log.error("请求不是 multipart 类型");
            return Result.error("请求格式错误，必须是 multipart/form-data");
        }

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;

        MultipartFile file = multipartRequest.getFile("file");
        if (file == null) {
            Iterator<String> names = multipartRequest.getFileNames();
            if (names.hasNext()) {
                file = multipartRequest.getFile(names.next());
            }
        }

        if (file == null || file.isEmpty()) {
            log.error("未找到上传文件或文件为空");
            return Result.error("未找到上传文件或文件为空");
        }

        String fileName = multipartRequest.getParameter("fileName");
        if (fileName == null || fileName.isEmpty()) {
            fileName = file.getOriginalFilename();
        }

        log.info("准备上传: fileName={}, size={} bytes", fileName, file.getSize());

        String accessToken = getTenantAccessToken();
        if (accessToken == null) {
            return Result.error("获取飞书访问令牌失败");
        }

        try {
            return doUploadMedia(file.getBytes(), sanitizeFileName(fileName), accessToken);
        } catch (Exception e) {
            log.error("上传异常", e);
            return Result.error("上传异常: " + e.getMessage());
        }
    }

    /**
     * 使用自定义的 tenant_access_token 上传
     */
    @PostMapping("/upload-with-tenant-token")
    public Result uploadWithTenantToken(
            HttpServletRequest request,
            @RequestHeader("X-Tenant-Token") String tenantAccessToken) {

        log.info("使用指定的 tenant_access_token 上传");

        if (!(request instanceof MultipartHttpServletRequest)) {
            return Result.error("请求格式错误");
        }

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        MultipartFile file = multipartRequest.getFile("file");

        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }

        String fileName = multipartRequest.getParameter("fileName");
        if (fileName == null || fileName.isEmpty()) {
            fileName = file.getOriginalFilename();
        }

        try {
            return doUploadMedia(file.getBytes(), sanitizeFileName(fileName), tenantAccessToken);
        } catch (Exception e) {
            log.error("上传异常", e);
            return Result.error("上传异常: " + e.getMessage());
        }
    }

    /**
     * 使用 user_access_token 上传（前端调用此接口）
     */
    @PostMapping("/upload-with-user-token")
    public Result uploadWithUserToken(
            HttpServletRequest request,
            @RequestHeader("X-User-Token") String userAccessToken) {

        log.info("使用 user_access_token 上传");

        if (!(request instanceof MultipartHttpServletRequest)) {
            return Result.error("请求格式错误");
        }

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        MultipartFile file = multipartRequest.getFile("file");

        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }

        String fileName = multipartRequest.getParameter("fileName");
        if (fileName == null || fileName.isEmpty()) {
            fileName = file.getOriginalFilename();
        }

        try {
            return doUploadMedia(file.getBytes(), sanitizeFileName(fileName), userAccessToken);
        } catch (Exception e) {
            log.error("上传异常", e);
            return Result.error("上传异常: " + e.getMessage());
        }
    }

    /**
     * 通用文件上传方法
     * API: POST /drive/v1/files/upload_all
     */
    private Result doUploadMedia(byte[] fileBytes, String safeFileName, String accessToken) throws IOException {
        long fileSize = fileBytes.length;

        if (fileSize > 20 * 1024 * 1024) {
            log.error("文件过大: {} bytes (限制20MB)", fileSize);
            return Result.error("文件大小超过20MB");
        }

        if (parentNode == null || parentNode.isEmpty()) {
            log.error("未配置 feishu.parent-node");
            return Result.error("请先配置飞书文件夹token (feishu.parent-node)");
        }

        log.info("上传文件到云空间: fileName={}, size={}, folder={}",
                safeFileName, fileSize, parentNode);

        String boundary = "----" + System.currentTimeMillis();
        String lineEnd = "\r\n";

        URL url = new URL(UPLOAD_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
            // file_name
            dos.writeBytes("--" + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"file_name\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(safeFileName + lineEnd);

            // parent_type（固定为 explorer，表示云空间）
            dos.writeBytes("--" + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"parent_type\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes("explorer" + lineEnd);

            // parent_node（文件夹 token）
            dos.writeBytes("--" + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"parent_node\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(parentNode + lineEnd);

            // size
            dos.writeBytes("--" + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"size\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(String.valueOf(fileSize) + lineEnd);

            // file
            dos.writeBytes("--" + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName + "\"" + lineEnd);
            dos.writeBytes("Content-Type: application/octet-stream" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.write(fileBytes);
            dos.writeBytes(lineEnd);

            // end
            dos.writeBytes("--" + boundary + "--" + lineEnd);
            dos.flush();
        }

        int responseCode = conn.getResponseCode();
        String responseBody;
        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                responseBody = sb.toString();
            }
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                responseBody = sb.toString();
            }
        }
        conn.disconnect();

        log.info("飞书响应(code={}): {}", responseCode, responseBody);

        JSONObject json = JSONUtil.parseObj(responseBody);
        Integer code = json.getInt("code");

        if (code != null && code == 0) {
            JSONObject data = json.getJSONObject("data");
            String fileToken = data.getStr("file_token");
            log.info("上传成功，file_token: {}", fileToken);

            Map<String, Object> result = new HashMap<>();
            result.put("fileToken", fileToken);
            result.put("fileName", safeFileName);
            result.put("size", fileSize);

            return Result.success(result);
        } else {
            String msg = json.getStr("msg");
            log.error("上传失败: code={}, msg={}", code, msg);
            return Result.error("上传失败: " + msg);
        }
    }

    /**
     * 净化文件名：移除中文字符和特殊字符，保留字母数字和扩展名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "file.pdf";
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = (dotIdx > 0) ? fileName.substring(0, dotIdx) : fileName;
        String ext = (dotIdx > 0) ? fileName.substring(dotIdx) : ".pdf";
        String clean = baseName.replaceAll("[^a-zA-Z0-9_-]", "");
        if (clean.isEmpty()) clean = "report";
        return clean + ext;
    }

    /**
     * 获取 tenant_access_token
     */
    private String getTenantAccessToken() {
        try {
            JSONObject body = new JSONObject();
            body.set("app_id", appId);
            body.set("app_secret", appSecret);

            log.info("获取tenant_access_token, app_id={}", appId);

            HttpResponse response = HttpRequest.post(TOKEN_URL)
                    .body(body.toString())
                    .timeout(10000)
                    .execute();

            String result = response.body();
            log.info("获取token响应: {}", result);

            JSONObject json = JSONUtil.parseObj(result);
            Integer code = json.getInt("code");

            if (code != null && code == 0) {
                String token = json.getStr("tenant_access_token");
                log.info("获取token成功");
                return token;
            } else {
                log.error("获取token失败: {}", json.getStr("msg"));
                return null;
            }
        } catch (Exception e) {
            log.error("获取token异常", e);
            return null;
        }
    }

    private Integer resolveUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Integer integerUserId) {
            return integerUserId;
        }
        if (userId instanceof Number numberUserId) {
            return numberUserId.intValue();
        }
        return null;
    }

    private String buildUserTokenConfigKey(Integer userId) {
        return FEISHU_USER_TOKEN_KEY_PREFIX + userId;
    }
}
