package com.study.sttproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SttProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(SttProjectApplication.class, args);
    }

}
