# Certificados extras (opcional)

Coloque aqui arquivos `*.crt` (PEM) de autoridades certificadoras extras necessárias **durante o build**
das imagens Docker — por exemplo, quando um antivírus ou proxy corporativo inspeciona TLS e o Maven/npm
falham com `PKIX path building failed` ou `SELF_SIGNED_CERT_IN_CHAIN`.

Os certificados são usados apenas nos estágios de build (Maven/npm), não nas imagens finais.
Arquivos `*.crt` desta pasta são ignorados pelo Git.
