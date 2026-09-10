package za.co.flash.sensitivewords.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Arrays;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import za.co.flash.sensitivewords.api.SensitiveWordController;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI api() {
        return new OpenAPI().info(new Info().title("Flash Sensitive Words API").version("v1")
                .description("Local assessment APIs are unauthenticated. Production requires issuer/audience-validated JWTs: sanitize, words:read or words:write scopes; Swagger is disabled."))
                .components(new Components());
    }

    private Schema<?> problemSchema() {
        return new ObjectSchema().addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema()).addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema())
                .addProperty("violations", new ArraySchema().items(new ObjectSchema()
                        .addProperty("field", new StringSchema()).addProperty("message", new StringSchema())))
                .addRequiredItem("type").addRequiredItem("title").addRequiredItem("status").addRequiredItem("detail");
    }

    @Bean
    OperationCustomizer errorOperations() {
        return (operation, handler) -> {
            var responses = operation.getResponses();
            responses.addApiResponse("400", problem(400, "Invalid fields, ID, pagination or JSON"));
            responses.addApiResponse("406", problem(406, "Unsupported response media type"));
            responses.addApiResponse("500", problem(500, "Unexpected server error"));
            responses.addApiResponse("503", problem(503, "Database unavailable or matcher untrusted/stale; inspect resource before retrying a write"));
            var parameters = handler.getMethod().getParameters();
            if (Arrays.stream(parameters).anyMatch(parameter -> parameter.isAnnotationPresent(PathVariable.class))) {
                responses.addApiResponse("404", problem(404, "Sensitive word not found"));
            }
            if (Arrays.stream(parameters).anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class))) {
                responses.addApiResponse("413", problem(413, "Request body exceeds the configured byte limit"));
                responses.addApiResponse("415", problem(415, "Request content type must be application/json"));
                if (handler.getBeanType() == SensitiveWordController.class) {
                    responses.addApiResponse("409", problem(409, "Duplicate normalized sensitive term"));
                }
            }
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer schemas(SanitizationProperties properties) {
        return api -> {
            // Register after pruning, alongside references supplied by the operation customizer.
            api.getComponents().addSchemas("ApiProblem", problemSchema());
            var request = api.getComponents().getSchemas().get("SanitizeRequest");
            if (request != null && request.getProperties() != null) {
                Schema<?> text = (Schema<?>) request.getProperties().get("text");
                text.setMaxLength(properties.maxMessageLength());
                text.setMinLength(1);
                text.setDescription("Nonblank string, at most "
                        + properties.maxMessageLength() + " UTF-16 units. Body limit: "
                        + (properties.maxMessageLength() * 6 + 2048) + " bytes, including JSON escapes and framing.");
            }
        };
    }

    private ApiResponse problem(int status, String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))
                        .example(Map.of("type", "about:blank", "title", HttpStatus.valueOf(status).getReasonPhrase(),
                                "status", status, "detail", description))));
    }
}
