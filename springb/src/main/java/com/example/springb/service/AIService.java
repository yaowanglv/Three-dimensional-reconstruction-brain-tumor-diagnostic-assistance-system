package com.example.springb.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.springb.mapper.ConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private static final String MODEL_DEEPSEEK = "deepseek";
    private static final String MODEL_GLM = "glm";
    private static final String MODEL_KIMI = "kimi";

    private final ConfigMapper configMapper;

    @Value("${ai.models.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.models.deepseek.endpoint:https://api.deepseek.com/chat/completions}")
    private String deepseekEndpoint;

    @Value("${ai.models.deepseek.model-name:deepseek-v4-flash}")
    private String deepseekModel;

    @Value("${ai.models.glm.api-key:}")
    private String glmApiKey;

    @Value("${ai.models.glm.endpoint:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String glmEndpoint;

    @Value("${ai.models.glm.model-name:glm-4}")
    private String glmModel;

    @Value("${ai.models.kimi.api-key:}")
    private String kimiApiKey;

    @Value("${ai.models.kimi.endpoint:https://api.moonshot.cn/v1/chat/completions}")
    private String kimiEndpoint;

    @Value("${ai.models.kimi.model-name:${KIMI_MODEL:kimi-k2.5-preview}}")
    private String kimiModel;

    public AIService(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public String analyze(String model, String prompt) {
        String normalizedModel = normalizeModel(model);
        switch (normalizedModel) {
            case MODEL_DEEPSEEK:
                return callDeepSeek(prompt);
            case MODEL_GLM:
                return callGLM(prompt);
            case MODEL_KIMI:
                return callKimi(prompt);
            default:
                throw new IllegalArgumentException("不支持的AI模型: " + model);
        }
    }

    private String callDeepSeek(String prompt) {
        String apiKey = resolveApiKey(MODEL_DEEPSEEK);
        ensureConfigured(apiKey, "DeepSeek");

        JSONObject requestBody = buildChatRequest(deepseekModel, prompt);
        String endpoint = deepseekEndpoint == null ? "" : deepseekEndpoint.trim();
        log.info("调用DeepSeek API, endpoint: {}, model: {}, key: {}", endpoint, deepseekModel, maskApiKey(apiKey));

        HttpResponse response = HttpRequest.post(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        if (response.getStatus() == 401) {
            log.error("DeepSeek API鉴权失败: status={}, body={}", response.getStatus(), response.body());
            throw new RuntimeException("DeepSeek API鉴权失败(HTTP 401)，请检查配置的 API Key 是否正确");
        }

        return parseResponse(response, "DeepSeek");
    }

    private String callGLM(String prompt) {
        String apiKey = resolveApiKey(MODEL_GLM);
        ensureConfigured(apiKey, "GLM");

        JSONObject requestBody = buildChatRequest(glmModel, prompt);
        log.info("调用GLM API, model: {}, key: {}", glmModel, maskApiKey(apiKey));

        HttpResponse response = HttpRequest.post(glmEndpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        return parseResponse(response, "GLM");
    }

    private String callKimi(String prompt) {
        String apiKey = resolveApiKey(MODEL_KIMI);
        ensureConfigured(apiKey, "Kimi");

        log.info("调用Kimi API, model: {}, key: {}", kimiModel, maskApiKey(apiKey));

        JSONObject requestBody = buildChatRequest(kimiModel, prompt);
        HttpResponse response = HttpRequest.post(kimiEndpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        log.info("Kimi响应状态: {}, 内容: {}", response.getStatus(), response.body());
        return parseResponse(response, "Kimi");
    }

    public boolean isConfigured(String model) {
        String normalizedModel = normalizeModel(model);
        if (normalizedModel.isEmpty()) {
            return false;
        }
        return isValidApiKey(resolveApiKey(normalizedModel));
    }

    public Map<String, Boolean> getConfigStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        status.put(MODEL_DEEPSEEK, isConfigured(MODEL_DEEPSEEK));
        status.put(MODEL_GLM, isConfigured(MODEL_GLM));
        status.put(MODEL_KIMI, isConfigured(MODEL_KIMI));
        return status;
    }

    public Map<String, Object> getAiConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(MODEL_DEEPSEEK, buildModelConfigInfo(MODEL_DEEPSEEK));
        config.put(MODEL_GLM, buildModelConfigInfo(MODEL_GLM));
        config.put(MODEL_KIMI, buildModelConfigInfo(MODEL_KIMI));
        return config;
    }

    public void updateAiConfig(Map<String, Object> request) {
        if (request == null) {
            return;
        }

        updateOverrideIfPresent(MODEL_DEEPSEEK, request.get("deepseekApiKey"));
        updateOverrideIfPresent(MODEL_GLM, request.get("glmApiKey"));
        updateOverrideIfPresent(MODEL_KIMI, request.get("kimiApiKey"));

        Object restoreDefaultModelsRaw = request.get("restoreDefaultModels");
        if (restoreDefaultModelsRaw instanceof List<?> restoreDefaultModels) {
            for (Object item : restoreDefaultModels) {
                String normalizedModel = normalizeModel(String.valueOf(item));
                if (!normalizedModel.isEmpty()) {
                    configMapper.deleteByConfigKey(overrideConfigKey(normalizedModel));
                }
            }
        }
    }

    private void updateOverrideIfPresent(String model, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        String apiKey = normalizeApiKey(String.valueOf(rawValue));
        if (apiKey.isEmpty()) {
            return;
        }
        configMapper.upsertConfig(
                overrideConfigKey(model),
                apiKey,
                model.toUpperCase() + " API override key"
        );
    }

    private Map<String, Object> buildModelConfigInfo(String model) {
        String overrideApiKey = getOverrideApiKey(model);
        String defaultApiKey = getDefaultApiKey(model);

        boolean hasCustom = isValidApiKey(overrideApiKey);
        boolean hasDefault = isValidApiKey(defaultApiKey);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("configured", hasCustom || hasDefault);
        info.put("hasCustom", hasCustom);
        info.put("hasDefault", hasDefault);
        info.put("usingCustom", hasCustom);
        info.put("usingDefault", !hasCustom && hasDefault);
        return info;
    }

    private JSONObject buildChatRequest(String modelName, String prompt) {
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", modelName);
        requestBody.set("messages", new JSONArray()
                .put(new JSONObject()
                        .set("role", "system")
                        .set("content", "你是一位专业的脑肿瘤影像诊断专家，擅长分析医学影像检测报告并提供专业诊断建议。"))
                .put(new JSONObject()
                        .set("role", "user")
                        .set("content", prompt))
        );
        requestBody.set("temperature", 0.7);
        requestBody.set("max_tokens", 2000);
        return requestBody;
    }

    private String parseResponse(HttpResponse response, String modelName) {
        if (response.getStatus() != 200) {
            log.error("{} API响应错误: {}, body: {}", modelName, response.getStatus(), response.body());
            throw new RuntimeException(modelName + " API调用失败: HTTP " + response.getStatus());
        }

        String body = response.body();
        JSONObject jsonResponse = JSONUtil.parseObj(body);
        if (jsonResponse.containsKey("error")) {
            JSONObject error = jsonResponse.getJSONObject("error");
            String errorMsg = error.getStr("message", "未知错误");
            log.error("{} API返回错误: {}", modelName, errorMsg);
            throw new RuntimeException(modelName + " API错误: " + errorMsg);
        }

        JSONArray choices = jsonResponse.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException(modelName + " API返回结果为空");
        }

        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message.getStr("content", "");
        log.info("{} API调用成功, 返回内容长度: {}", modelName, content.length());
        return content.trim();
    }

    private void ensureConfigured(String apiKey, String modelName) {
        if (!isValidApiKey(apiKey)) {
            throw new RuntimeException(modelName + " API Key未配置");
        }
    }

    private String resolveApiKey(String model) {
        String overrideApiKey = getOverrideApiKey(model);
        if (isValidApiKey(overrideApiKey)) {
            return overrideApiKey;
        }
        return getDefaultApiKey(model);
    }

    private String getOverrideApiKey(String model) {
        return normalizeApiKey(configMapper.selectValueByKey(overrideConfigKey(model)));
    }

    private String getDefaultApiKey(String model) {
        String normalizedModel = normalizeModel(model);
        switch (normalizedModel) {
            case MODEL_DEEPSEEK:
                return normalizeApiKey(deepseekApiKey);
            case MODEL_GLM:
                return normalizeApiKey(glmApiKey);
            case MODEL_KIMI:
                return normalizeApiKey(kimiApiKey);
            default:
                return "";
        }
    }

    private String overrideConfigKey(String model) {
        return "ai." + normalizeModel(model) + ".api_key_override";
    }

    private String normalizeModel(String model) {
        if (model == null) {
            return "";
        }
        String normalized = model.trim().toLowerCase();
        if (MODEL_DEEPSEEK.equals(normalized) || MODEL_GLM.equals(normalized) || MODEL_KIMI.equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        String key = apiKey.trim();
        if (key.toLowerCase().startsWith("bearer ")) {
            key = key.substring("Bearer ".length()).trim();
        }
        return key;
    }

    private boolean isValidApiKey(String apiKey) {
        String key = normalizeApiKey(apiKey);
        if (key.isEmpty()) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        return !lowerKey.contains("your_")
                && !lowerKey.contains("your-")
                && !lowerKey.contains("placeholder")
                && !lowerKey.contains("api-key-here")
                && !lowerKey.contains("api_key_here");
    }

    private String maskApiKey(String apiKey) {
        String normalized = normalizeApiKey(apiKey);
        if (normalized.isEmpty()) {
            return "(empty)";
        }
        if (normalized.length() <= 8) {
            return "****";
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }
}
