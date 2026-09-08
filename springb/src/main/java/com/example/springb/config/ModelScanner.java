package com.example.springb.config;

import com.example.springb.entity.ModelConfig;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ModelScanner {
    private static final Set<String> MODEL_EXTENSIONS = Set.of("pt", "pth", "onnx", "engine", "trt");

    public List<ModelConfig> scan(String pageType, File folder) {
        return scan(pageType, folder, false);
    }

    public List<ModelConfig> scan(String pageType, File folder, boolean includeLastPt) {
        List<ModelConfig> models = new ArrayList<>();
        scanDirectory(pageType, folder, folder.getAbsolutePath(), includeLastPt, models);
        return models;
    }

    private void scanDirectory(String pageType, File dir, String baseFolder, boolean includeLastPt, List<ModelConfig> models) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(pageType, file, baseFolder, includeLastPt, models);
                continue;
            }

            String ext = getExtension(file.getName()).toLowerCase(Locale.ROOT);
            if (!MODEL_EXTENSIONS.contains(ext)) {
                continue;
            }
            if (!includeLastPt && "pt".equals(ext) && "last.pt".equalsIgnoreCase(file.getName())) {
                continue;
            }

            ModelConfig model = new ModelConfig();
            model.setPageType(pageType);
            model.setFolderPath(baseFolder);
            model.setDisplayName(stripExtension(file.getName()));
            model.setModelName(file.getName());
            model.setModelPath(file.getAbsolutePath());
            model.setModelType(ext);
            model.setIsActive(1);
            models.add(model);
        }
    }

    private String getExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index >= 0 && index < filename.length() - 1 ? filename.substring(index + 1) : "";
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : filename;
    }
}
