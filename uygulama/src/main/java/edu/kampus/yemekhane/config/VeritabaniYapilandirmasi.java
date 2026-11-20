package edu.kampus.yemekhane.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

@Configuration
public class VeritabaniYapilandirmasi {

    @Primary
    @Bean(name = "yazmaDataSource")
    @ConfigurationProperties(prefix = "app.datasource.yazma")
    public DataSource yazmaDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "okumaDataSource")
    @ConfigurationProperties(prefix = "app.datasource.okuma")
    public DataSource okumaDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean(name = "yazmaJdbcTemplate")
    public JdbcTemplate yazmaJdbcTemplate(@Qualifier("yazmaDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "okumaJdbcTemplate")
    public JdbcTemplate okumaJdbcTemplate(@Qualifier("okumaDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}