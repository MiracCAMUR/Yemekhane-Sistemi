package edu.kampus.yemekhane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class YemekhaneUygulamasi {
    public static void main(String[] args) {
        SpringApplication.run(YemekhaneUygulamasi.class, args);
    }
}