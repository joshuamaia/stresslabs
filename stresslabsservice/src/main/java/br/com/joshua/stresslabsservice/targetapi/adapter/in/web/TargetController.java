package br.com.joshua.stresslabsservice.targetapi.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.joshua.stresslabsservice.shared.domain.PageResult;
import br.com.joshua.stresslabsservice.targetapi.application.ProcessingService;
import br.com.joshua.stresslabsservice.targetapi.application.ProductService;
import br.com.joshua.stresslabsservice.targetapi.application.ProductService.CreateProductCommand;
import br.com.joshua.stresslabsservice.targetapi.domain.ProcessingWorkload.ProcessingResult;
import br.com.joshua.stresslabsservice.targetapi.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Endpoints reais submetidos aos testes de carga e ao Rate Limiting. */
@RestController
@RequestMapping("/api/target")
public class TargetController {

    private final ProductService productService;
    private final ProcessingService processingService;

    public TargetController(ProductService productService, ProcessingService processingService) {
        this.productService = productService;
        this.processingService = processingService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now());
    }

    @GetMapping("/products")
    public PageResult<ProductResponse> products(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return productService.list(page, size).map(ProductResponse::from);
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable long id) {
        return ProductResponse.from(productService.get(id));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        var command = new CreateProductCommand(request.name(), request.category(), request.price(), request.stock());
        return ProductResponse.from(productService.create(command));
    }

    @GetMapping("/processing")
    public ProcessingResult processing(@RequestParam(defaultValue = "5") int workUnits) {
        return processingService.process(workUnits);
    }

    public record HealthResponse(String status, Instant timestamp) {
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 60) String category,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            @Min(0) int stock) {
    }

    public record ProductResponse(Long id, String name, String category, BigDecimal price, int stock,
                                  Instant createdAt) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.id(), product.name(), product.category(), product.price(),
                    product.stock(), product.createdAt());
        }
    }
}
