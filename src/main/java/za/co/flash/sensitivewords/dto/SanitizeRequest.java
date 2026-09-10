package za.co.flash.sensitivewords.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SanitizeRequest(
        @NotBlank @Schema(description = "Message; maximum length is configured by SANITIZATION_MAX_MESSAGE_LENGTH (default 10000 UTF-16 units)",
                example = "You need to CREATE a string") String text) {}
