package za.co.flash.sensitivewords.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("vocabulary")
public record VocabularyProperties(
        @DefaultValue("300") @Min(1) @Max(86400) int maxStaleSeconds,
        @DefaultValue("10000") @Min(228) @Max(100000) int maxTerms) {}
