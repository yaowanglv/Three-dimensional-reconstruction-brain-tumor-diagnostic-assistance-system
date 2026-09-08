package com.example.springb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.springb.mapper")//扫描mapper接口
public class SpringbApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbApplication.class, args);
    }

}
