package za.co.flash.sensitivewords;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SensitiveWordsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SensitiveWordsApplication.class, args);
    }
}
