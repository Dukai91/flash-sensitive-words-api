package za.co.flash.sensitivewords.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import za.co.flash.sensitivewords.application.SensitiveWordService;
import za.co.flash.sensitivewords.dto.SensitiveWordRequest;
import za.co.flash.sensitivewords.dto.SensitiveWordResponse;

@RestController
@RequestMapping(value = "/api/v1/internal/sensitive-words", produces = "application/json")
@Tag(name = "Sensitive words (internal)", description = "Administrative CRUD. Must be protected by production networking and authorization.")
public class SensitiveWordController {
    private final SensitiveWordService service;

    public SensitiveWordController(SensitiveWordService service) {
        this.service = service;
    }

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Create a sensitive term and refresh the local matcher")
    @ApiResponse(responseCode = "201", description = "Created; Location header identifies the resource")
    public ResponseEntity<SensitiveWordResponse> create(@Valid @RequestBody SensitiveWordRequest request) {
        var response = service.create(request.word());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "List sensitive terms, ordered by ascending ID")
    @ApiResponse(responseCode = "200", description = "Page of terms with page metadata")
    public PagedModel<SensitiveWordResponse> list(
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(service.list(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one sensitive term")
    @ApiResponse(responseCode = "200", description = "The requested term")
    public SensitiveWordResponse get(@Parameter(description = "Positive sensitive-word ID") @PathVariable @Positive long id) {
        return service.get(id);
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @Operation(summary = "Replace a sensitive term and refresh the local matcher")
    @ApiResponse(responseCode = "200", description = "Updated term")
    public SensitiveWordResponse update(
            @Parameter(description = "Positive sensitive-word ID") @PathVariable @Positive long id,
            @Valid @RequestBody SensitiveWordRequest request) {
        return service.update(id, request.word());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a sensitive term and refresh the local matcher")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> delete(@Parameter(description = "Positive sensitive-word ID") @PathVariable @Positive long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
