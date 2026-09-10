package za.co.flash.sensitivewords.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sanitization")
public record SanitizationProperties(@DefaultValue("10000") @Min(1) @Max(1000000) int maxMessageLength) {}
