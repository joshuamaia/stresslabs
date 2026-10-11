INSERT INTO rate_limit_configuration (id, enabled, capacity, refill_tokens, refill_period_seconds, client_strategy, updated_at)
VALUES (1, TRUE, 100, 100, 60, 'IP', now());

INSERT INTO product (name, category, price, stock)
SELECT 'Produto ' || lpad(g::text, 4, '0'),
       (ARRAY ['eletronicos', 'livros', 'casa', 'esporte', 'moda'])[1 + (g % 5)],
       round((10 + (g * 7.31) % 990)::numeric, 2),
       (g * 13) % 500
FROM generate_series(1, 1000) AS g;
