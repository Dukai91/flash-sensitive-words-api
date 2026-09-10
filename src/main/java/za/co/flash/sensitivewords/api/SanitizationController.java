package za.co.flash.sensitivewords.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import za.co.flash.sensitivewords.application.SanitizationService;
import za.co.flash.sensitivewords.dto.SanitizeRequest;
import za.co.flash.sensitivewords.dto.SanitizeResponse;

@RestController
@RequestMapping("/api/v1/sanitize")
@Tag(name = "Sanitization", description = "External chat-message sanitization")
public class SanitizationController {
    private final SanitizationService service;

    public SanitizationController(SanitizationService service) {
        this.service = service;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Replace configured sensitive terms with stars",
            description = "Case-insensitive, whole-term matching. Shorter matches win at the same position. "
                    + "For the supplied list, SELECT * FROM sensitiveWords becomes ****** * FROM sensitiveWords.")
    @ApiResponse(responseCode = "200", description = "Original and sanitized message")
    public SanitizeResponse sanitize(@Valid @RequestBody SanitizeRequest request) {
        return service.sanitize(request.text());
    }
}
