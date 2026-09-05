package mx.jobmatch.configuration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class RuntimeRoleConfiguration {
    @Bean
    @Profile("migrate")
    ApplicationRunner exitAfterMigration(ConfigurableApplicationContext context) {
        return args -> context.close();
    }
}

