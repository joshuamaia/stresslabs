package br.com.joshua.stresslabsservice.targetapi.domain;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

public record Product(Long id, String name, String category, BigDecimal price, int stock, Instant createdAt) {

    public Product {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new DomainValidationException("Nome do produto deve ter entre 1 e 120 caracteres");
        }
        if (category == null || category.isBlank() || category.length() > 60) {
            throw new DomainValidationException("Categoria deve ter entre 1 e 60 caracteres");
        }
        if (price == null || price.signum() < 0) {
            throw new DomainValidationException("Preço deve ser maior ou igual a zero");
        }
        if (stock < 0) {
            throw new DomainValidationException("Estoque deve ser maior ou igual a zero");
        }
    }

    public static Product newProduct(String name, String category, BigDecimal price, int stock, Instant now) {
        return new Product(null, name.trim(), category.trim(), price, stock, now);
    }
}
