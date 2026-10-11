package br.com.joshua.stresslabsservice.targetapi.application.port.out;

import java.util.Optional;

import br.com.joshua.stresslabsservice.shared.domain.PageResult;
import br.com.joshua.stresslabsservice.targetapi.domain.Product;

public interface ProductRepositoryPort {

    PageResult<Product> findPage(int page, int size);

    Optional<Product> findById(long id);

    Product save(Product product);
}
