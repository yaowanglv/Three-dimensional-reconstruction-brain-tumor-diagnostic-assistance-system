package com.example.springb.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.example.springb.common.Result;
import com.example.springb.entity.Admin;
import com.example.springb.exception.CustomerException;

import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import com.example.springb.service.AdminService;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {
    @Resource

    AdminService       adminService;

@PostMapping("/add")
    public  Result add(@RequestBody Admin admin){//@Reques
//    admin.setTime(DateUtil.now());// tBody 接收前端传来的json参数
        adminService.add(admin);
        return Result.success();

}

//更新数据到数据库中
//    @PutMapping ("/update")
//    public  Result update(@RequestBody Admin admin){//@RequestBody 接收前端传来的json参数
//        adminService .add(admin);
//        return Result.success();
//    }
    @PutMapping("/update")
    public Result update(@RequestBody Admin admin) {
        try {
            System.out.println("更新管理员数据: " + admin);

            // 检查 ID 是否存在
            if (admin.getId() == null) {
                return Result.error("更新操作需要提供ID");
            }

            adminService.update(admin);
            return Result.success();
        } catch (Exception e) {
            // 打印详细的堆栈信息
            e.printStackTrace();
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @PutMapping("/changePassword")
    public Result changePassword(@RequestBody Map<String, Object> params) {
        try {
            Integer id = parseInteger(params.get("id"));
            String oldPassword = stringValue(params.get("oldPassword"));
            String newPassword = stringValue(params.get("newPassword"));
            adminService.changePassword(id, oldPassword, newPassword);
            return Result.success();
        } catch (CustomerException e) {
            return Result.error(e.getCode(), e.getMsg());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("修改密码失败: " + e.getMessage());
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value)) || "null".equals(String.valueOf(value))) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable Integer id) {//PathVariable 接收前端传来的路径参数
        adminService .deleteById(id);
        return Result.success();
    }
//批量删除
    @DeleteMapping("/deleteBatch")
    public Result deleteBatch(@RequestBody List<Admin> list) {//@RequestBody 接收前端传来的json参数
        adminService .deleteBatch(list);
        return Result.success();
    }



    @GetMapping("/selectAll")  //   完整的请求路径：http://ip:port/admin/selectAll
    public Result selectAll(Admin admin) {
        List<Admin> adminList =adminService.selectAll(admin);
        return Result.success(adminList);
}

    /**
     * 分页查询
     * pageNum: 当前的页码
     * pageSize：每页的个数
     */
    @GetMapping("/selectPage")
    public Result selectPage(@RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize,
                             Admin admin) {
        PageInfo<Admin> pageInfo = adminService.selectPage(pageNum, pageSize,admin);
        return Result.success(pageInfo);  // 返回的是分页的对象
    }


    /**
     * 数据导出
     * ids:1,2,3
     */
    @GetMapping("/export")
    public void exportData(Admin admin, HttpServletResponse response) throws Exception {
//        String ids=admin.getIds();
//        if(StrUtil.isBlank(ids)){
//            String[] idsArr = ids.split(",");
//            admin.setIdsArr(idsArr);
//        }
        String ids = admin.getIds();
        if (StrUtil.isNotBlank(ids)) { // 修正条件判断：当ids不为空时执行
            String[] idsArr = ids.split(",");
            admin.setIdsArr(idsArr);
        } else {
            // 处理ids为空的情况，可以选择设置空数组或者抛出异常
            admin.setIdsArr(new String[0]); // 设置空数组避免null
        }

        // 1. 拿到所有数据
        List<Admin> list = adminService.selectAll(admin);
        // 2. 构建Writer对象
        ExcelWriter writer = ExcelUtil.getWriter(true);
        // 3. 设置中文表头
        writer.addHeaderAlias("username", "账号");
        writer.addHeaderAlias("name", "名称");
//        writer.addHeaderAlias("id", "数据ID");

        // 默认的，未添加alias的属性也会写出，如果想只写出加了别名的字段，可以调用此方法排除之
        writer.setOnlyAlias(true);
        // 4. 写出数据到writer
        writer.write(list);
        // 5. 设置输出的文件的名称以及输出流的头信息
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("管理员信息", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
        // 6. 写出到输出流 并关闭 writer
        ServletOutputStream os = response.getOutputStream();
        writer.flush(os);
        writer.close();
        os.close();
    }


    /**
     * 批量导入
     */
    @PostMapping("/import")
    public Result importData(MultipartFile file) throws Exception {
        //  1. 拿到输入流 构建 reader
        InputStream inputStream = file.getInputStream();
        ExcelReader reader = ExcelUtil.getReader(inputStream);
        //  2. 通过Reader 读取 excel 里面的数据
        reader.addHeaderAlias("账号", "username");
        reader.addHeaderAlias("名称", "name");
        reader.addHeaderAlias("密码（不填则默认admin）", "password");
//        reader.addHeaderAlias("数据ID", "id");

        List<Admin> list = reader.readAll(Admin.class);
        // 3. 将数据写到数据库
        for (Admin admin : list) {
            adminService.add(admin);
        }
        return Result.success();
    }


}
