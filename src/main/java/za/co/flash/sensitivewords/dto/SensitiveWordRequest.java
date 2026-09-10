package za.co.flash.sensitivewords.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SensitiveWordRequest(
        @NotBlank @Size(max = 200) @Schema(example = "CONFIDENTIAL", description = "Literal term; surrounding whitespace is stripped; no control characters")
        String word) {}
