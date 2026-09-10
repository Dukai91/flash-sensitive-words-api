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
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI api() {
        var problem = new ObjectSchema()
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Invalid request. Check the JSON body, field constraints and parameters"))
                .addProperty("instance", new StringSchema().example("/api/v1/sanitize"));
        return new OpenAPI().info(new Info().title("Flash Sensitive Words API").version("v1")
                .description("Chat sanitization and internal vocabulary management. No authentication in the local assessment deployment."))
                .components(new Components().addSchemas("ApiProblem", problem));
    }

    @Bean
    OpenApiCustomizer errorResponses() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            operation.getResponses().addApiResponse("400", problem("Validation failure, invalid ID/pagination, or malformed JSON"));
            operation.getResponses().addApiResponse("500", problem("Unexpected server error; internal details are not returned"));
            if (path.contains("{id}")) {
                operation.getResponses().addApiResponse("404", problem("Sensitive word not found"));
            }
            if (method.name().equals("POST") || method.name().equals("PUT")) {
                operation.getResponses().addApiResponse("415", problem("Unsupported request content type"));
                if (path.contains("internal")) {
                    operation.getResponses().addApiResponse("409", problem("Duplicate normalized sensitive term"));
                }
            }
            if (path.endsWith("sanitize")) {
                operation.getResponses().addApiResponse("503", problem("Matcher has not finished initialization"));
            }
        }));
    }

    private ApiResponse problem(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))));
    }
}
