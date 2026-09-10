package za.co.flash.sensitivewords.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJson() {
        return builder -> builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .postConfigurer(mapper -> {
                    var strings = mapper.coercionConfigFor(LogicalType.Textual);
                    for (var shape : new CoercionInputShape[]{CoercionInputShape.Integer,
                            CoercionInputShape.Float, CoercionInputShape.Boolean}) {
                        strings.setCoercion(shape, CoercionAction.Fail);
                    }
                });
    }
}
