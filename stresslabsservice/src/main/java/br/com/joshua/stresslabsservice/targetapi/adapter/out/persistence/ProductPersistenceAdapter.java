package br.com.joshua.stresslabsservice.targetapi.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.joshua.stresslabsservice.shared.domain.PageResult;
import br.com.joshua.stresslabsservice.targetapi.application.port.out.ProductRepositoryPort;
import br.com.joshua.stresslabsservice.targetapi.domain.Product;

@Component
class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository repository;

    ProductPersistenceAdapter(ProductJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> findPage(int page, int size) {
        Page<ProductJpaEntity> result = repository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new PageResult<>(result.getContent().stream().map(ProductPersistenceAdapter::toDomain).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(long id) {
        return repository.findById(id).map(ProductPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Product save(Product product) {
        var entity = new ProductJpaEntity(product.name(), product.category(), product.price(), product.stock(),
                product.createdAt());
        return toDomain(repository.save(entity));
    }

    private static Product toDomain(ProductJpaEntity entity) {
        return new Product(entity.getId(), entity.getName(), entity.getCategory(), entity.getPrice(),
                entity.getStock(), entity.getCreatedAt());
    }
}
