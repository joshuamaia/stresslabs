package br.com.joshua.stresslabsservice.targetapi.application;

import java.math.BigDecimal;
import java.time.Clock;

import org.springframework.stereotype.Service;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;
import br.com.joshua.stresslabsservice.shared.domain.PageResult;
import br.com.joshua.stresslabsservice.shared.domain.ResourceNotFoundException;
import br.com.joshua.stresslabsservice.targetapi.application.port.out.ProductRepositoryPort;
import br.com.joshua.stresslabsservice.targetapi.domain.Product;

@Service
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ProductRepositoryPort repository;
    private final Clock clock;

    public ProductService(ProductRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResult<Product> list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainValidationException("page >= 0 e size entre 1 e " + MAX_PAGE_SIZE);
        }
        return repository.findPage(page, size);
    }

    public Product get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto " + id + " não encontrado"));
    }

    public Product create(CreateProductCommand command) {
        Product product = Product.newProduct(command.name(), command.category(), command.price(),
                command.stock(), clock.instant());
        return repository.save(product);
    }

    public record CreateProductCommand(String name, String category, BigDecimal price, int stock) {
    }
}
