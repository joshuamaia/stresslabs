package br.com.joshua.stresslabsservice.ratelimit.domain;

/** Política de identificação do cliente para separar os buckets. */
public enum ClientIdentificationStrategy {
    /** Um bucket por endereço IP de origem. */
    IP,
    /** Um bucket por valor do header X-Client-Id (fallback para IP). */
    HEADER,
    /** Um único bucket compartilhado por todos os clientes. */
    GLOBAL
}
