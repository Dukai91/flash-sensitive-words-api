package za.co.flash.sensitivewords.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SanitizeResponse(
        @Schema(example = "You need to CREATE a string") String original,
        @Schema(example = "You need to ****** a string") String sanitized) {}
