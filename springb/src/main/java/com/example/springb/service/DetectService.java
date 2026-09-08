package com.example.springb.service;

import com.example.springb.entity.Detect;
import com.example.springb.mapper.DetectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DetectService {

    @Resource
    private DetectMapper detectMapper;

    public void add(Detect detect) {
        detectMapper.insert(detect);
    }

    public void update(Detect detect) {
        detectMapper.updateById(detect);
    }

    public void deleteById(Integer id) {
        detectMapper.deleteById(id);
    }

    public void deleteBatch(List<Detect> list) {
        for (Detect detect : list) {
            this.deleteById(detect.getId());
        }
    }

    public List<Detect> selectAll(Detect detect) {
        return detectMapper.selectAll(detect);
    }

    public PageInfo<Detect> selectPage(Integer pageNum, Integer pageSize, Detect detect) {
        PageHelper.startPage(pageNum, pageSize);
        List<Detect> list = detectMapper.selectAll(detect);
        return PageInfo.of(list);
    }

    public Detect selectById(Integer id) {
        return detectMapper.selectById(id);
    }
}
