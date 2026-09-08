package com.example.springb.service;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.example.springb.entity.Admin;
import com.example.springb.exception.CustomerException;
import com.example.springb.mapper.AdminMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    @Resource
    AdminMapper adminMapper;

    @Resource
    private PasswordEncoder passwordEncoder;


    public Admin createAdmin(Admin admin) {
        // 手动设置创建时间

//        admin.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        adminMapper.insert(admin); // 插入数据库
        return admin;
    }

    public void add(Admin admin) {
        // 根据新的账号查询数据库  是否存在同样账号的数据
        admin.setTime(DateUtil.now());// tBody 接收前端传来的json参数
        Admin dbAdmin = adminMapper.selectByUsername(admin.getUsername());
        if (dbAdmin != null) {
            throw new CustomerException("9527","账号重复");//抛出自定义异常
        }
        // 默认密码设置为123456
        if (StrUtil.isBlank(admin.getPassword())) {
            admin.setPassword("123456");
        }
        admin.setPassword(encodePasswordIfNecessary(admin.getPassword()));
        if (StrUtil.isBlank(admin.getRole())) {
            admin.setRole("user");
        }
        //插入数据库
        adminMapper.insert(admin);
    }


    public String admin(String name) {
            if ("admin".equals(name)) {
                return "admin";
            } else {
                throw new CustomerException("1234","账号错误");
            }
    }

    public void update(Admin admin) {
        if (admin != null && StrUtil.isNotBlank(admin.getPassword())) {
            admin.setPassword(encodePasswordIfNecessary(admin.getPassword()));
        }
        adminMapper.updateById(admin);//根据主键去修改，主键唯一
    }

    public void changePassword(Integer id, String oldPassword, String newPassword) {
        if (id == null) {
            throw new CustomerException("400", "用户不存在");
        }
        if (StrUtil.isBlank(oldPassword)) {
            throw new CustomerException("400", "请输入原密码");
        }
        if (StrUtil.isBlank(newPassword)) {
            throw new CustomerException("400", "请输入新密码");
        }

        Admin dbAdmin = adminMapper.selectById(id);
        if (dbAdmin == null) {
            throw new CustomerException("400", "用户不存在");
        }
        if (!matchesPassword(oldPassword, dbAdmin.getPassword())) {
            throw new CustomerException("400", "原密码错误");
        }

        Admin updateAdmin = new Admin();
        updateAdmin.setId(id);
        updateAdmin.setPassword(encodePasswordIfNecessary(newPassword));
        adminMapper.updatePasswordById(updateAdmin);
    }

    public void updatePasswordOnly(Admin admin) {
        adminMapper.updatePasswordById(admin);
    }

    public List<Admin> selectAll(Admin admin){
            return adminMapper.selectAll(admin);
        }

        //单个删除
    public void deleteById(Integer id) {
        adminMapper.deleteById(id);
    }

    //批量删除
    public void deleteBatch(List<Admin> list) {
        for (Admin admin : list) {
            this.deleteById(admin.getId());
        }
    }


    public PageInfo<Admin> selectPage(Integer pageNum, Integer pageSize,Admin admin) {
       //开启分页查询
        PageHelper.startPage(pageNum, pageSize);
        List<Admin> list = adminMapper.selectAll(admin);
        return PageInfo.of(list);
    }


//    public Admin login(Account account) {
//        // 验证账号是否存在
//        Admin dbAdmin = adminMapper.selectByUsername(account.getUsername());
//        if (dbAdmin == null) {
//            throw new CustomerException("账号不存在");
//        }
//        // 验证密码是否正确
//        if (!dbAdmin.getPassword().equals(account.getPassword())) {
//            throw new CustomerException("账号或密码错误");
//        }
//        return dbAdmin;
//    }

        public Admin login(Admin admin) {
        // 验证账号是否存在
        Admin dbAdmin = adminMapper.selectByUsername(admin.getUsername());
        if (dbAdmin == null) {
            throw new CustomerException("账号不存在");
        }
        // 验证密码是否正确
        if (!matchesPassword(admin.getPassword(), dbAdmin.getPassword())) {
            throw new CustomerException("账号或密码错误");
        }
        return dbAdmin;
    }

    public boolean hasColumn(String columnName) {
        return adminMapper.existsColumn(columnName) > 0;
    }

    public boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBCrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    public String encodePasswordIfNecessary(String password) {
        if (StrUtil.isBlank(password) || isBCrypt(password)) {
            return password;
        }
        return passwordEncoder.encode(password);
    }

    private boolean isBCrypt(String password) {
        return password != null && password.matches("^\\$2[aby]\\$.{56}$");
    }




}
