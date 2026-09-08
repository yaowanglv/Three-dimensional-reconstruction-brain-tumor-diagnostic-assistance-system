package com.example.springb.mapper;

import com.example.springb.entity.Admin;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AdminMapper {
    List<Admin> selectAll(Admin admin);

    void insert(Admin admin);

    @Select("select  * from  `admin` where username=#{username}")
    Admin selectByUsername(String username);

    @Select("select * from `admin` where id=#{id}")
    Admin selectById(Integer id);

    @Select("select count(*) from information_schema.columns where table_schema = database() and table_name = 'admin' and column_name = #{columnName}")
    int existsColumn(String columnName);

    void updateById(Admin admin);

    @Update("update `admin` set password=#{password} where id=#{id}")
    void updatePasswordById(Admin admin);

    @Delete("delete from  `admin` where id=#{id}")
    void deleteById(Integer id);
}
