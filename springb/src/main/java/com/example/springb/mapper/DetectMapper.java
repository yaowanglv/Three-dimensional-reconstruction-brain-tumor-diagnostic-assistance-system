package com.example.springb.mapper;

import com.example.springb.entity.Detect;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface DetectMapper {

    List<Detect> selectAll(Detect detect);

    @Insert("INSERT INTO detect (user_id, user_name, original_image_name, " +
            "original_image_url, original_image_size, original_image_format, " +
            "result_image_url, detection_data, segment_type, case_path, output_path, total_slices, " +
            "service_type, input_size, model_format, detect_mode, detect_status, ai_status, create_time) VALUES " +
            "(#{userId}, #{userName}, #{originalImageName}, " +
            "#{originalImageUrl}, #{originalImageSize}, #{originalImageFormat}, " +
            "#{resultImageUrl}, #{detectionData}, #{segmentType}, #{casePath}, #{outputPath}, #{totalSlices}, " +
            "#{serviceType}, #{inputSize}, #{modelFormat}, #{detectMode}, " +
            "COALESCE(#{detectStatus}, 0), COALESCE(#{aiStatus}, 0), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Detect detect);

    void updateById(Detect detect);

    Detect selectById(Integer id);

    @Delete("DELETE FROM detect WHERE id = #{id}")
    void deleteById(Integer id);
}
