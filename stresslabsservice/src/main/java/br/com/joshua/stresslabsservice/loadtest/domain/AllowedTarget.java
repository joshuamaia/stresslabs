package br.com.joshua.stresslabsservice.loadtest.domain;

/**
 * Destino interno autorizado para testes. Apenas caminhos relativos da própria API são aceitos.
 *
 * @param idRange quando maior que zero, o placeholder {id} do caminho é substituído pelo gerador
 *                por um valor aleatório entre 1 e idRange
 */
public record AllowedTarget(String key, String label, TargetHttpMethod method, String path, String description,
                            String defaultBody, int idRange) {
}
