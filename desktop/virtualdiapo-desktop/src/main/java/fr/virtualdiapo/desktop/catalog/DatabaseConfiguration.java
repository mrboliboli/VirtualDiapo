package fr.virtualdiapo.desktop.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfiguration {
    @Bean
    DataSource dataSource(DataSourceProperties properties,
                          @Value("${virtualdiapo.data-directory}") Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory.toAbsolutePath().normalize());
        return properties.initializeDataSourceBuilder().build();
    }
}
