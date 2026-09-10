package za.co.flash.sensitivewords.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI api() {
        return new OpenAPI().info(new Info().title("Flash Sensitive Words API").version("v1")
                .description("Chat sanitization and internal vocabulary management. No authentication in the local assessment deployment."))
                .components(new Components());
    }

    private Schema<?> problemSchema() {
        return new ObjectSchema()
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Invalid request. Check the JSON body, field constraints and parameters"))
                .addProperty("instance", new StringSchema().example("/api/v1/sanitize"));
    }

    @Bean
    OpenApiCustomizer errorResponses() {
        return api -> {
            // Add the schema alongside its references, after Springdoc's unused-schema pruning.
            api.getComponents().addSchemas("ApiProblem", problemSchema());
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                operation.getResponses().addApiResponse("400", problem(400, "Validation failure, invalid ID/pagination, or malformed JSON"));
                operation.getResponses().addApiResponse("500", problem(500, "Unexpected server error; internal details are not returned"));
                if (path.contains("{id}")) {
                    operation.getResponses().addApiResponse("404", problem(404, "Sensitive word not found"));
                }
                if (method.name().equals("POST") || method.name().equals("PUT")) {
                    operation.getResponses().addApiResponse("415", problem(415, "Unsupported request content type"));
                    if (path.contains("internal")) {
                        operation.getResponses().addApiResponse("409", problem(409, "Duplicate normalized sensitive term"));
                    }
                }
                if (path.endsWith("sanitize")) {
                    operation.getResponses().addApiResponse("503", problem(503, "Matcher has not finished initialization"));
                }
            }));
        };
    }

    private ApiResponse problem(int status, String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))
                        .example(Map.of("type", "about:blank", "title", HttpStatus.valueOf(status).getReasonPhrase(),
                                "status", status, "detail", description))));
    }
}
