# Escopo de evolução e critérios de aceite

Este documento acompanha o pedido de evolução do HydroGuard Citizen para a Global Solution FIAP 2026. Ele descreve critérios de trabalho; não é um relatório de execução. A solicitação mais recente do usuário é a fonte do escopo. Resultados concretos ficam em `docs/relatorio-validacao.md`.

## Antes de alterar

1. Leia `AGENTS.md`, `README.md` e a documentação em `docs/`.
2. Inspecione estrutura, responsabilidades, funcionalidades existentes, riscos de segurança e diferenças entre implementação e documentação.
3. Execute `mvn clean verify`, registre o resultado e identifique bloqueios do ambiente. Não marque um recurso como concluído pela presença de seu código.

## Backend e dados

- Preserve Java/Spring Boot e a organização Controller/Service/Repository/Entity/DTO.
- Valide cadastro, protocolo UUID imprevisível, filtros combináveis de status/prioridade, paginação e busca administrativas, atualização autorizada e histórico de status com autor/data.
- Dashboard usa agregações reais do banco. A consulta por protocolo minimiza dados; endereço original, descrição e fotos completas ficam protegidos.
- PostgreSQL é principal; H2 apenas em testes e demonstração `local` explícita. Migrações Flyway iniciam schemas novos e não devem destruir ou assumir migração de dados legados.
- Upload usa JPEG/PNG reais, limites de bytes/dimensões/pixels, reencodificação e token individual de uso único. Armazenamento local e Azure Blob permanecem atrás da mesma interface.

## IA e frontend

- Integre Gemini pelo backend com segredo externo, esquema estruturado, validação estrita, timeout e limite de concorrência.
- Sucesso validado é `IA_GEMINI`; sem chave, `REGRAS`; indisponibilidade ou saída inválida, `REGRAS_CONTINGENCIA`.
- Teste sucesso, JSON inválido, timeout, HTTP 401/403/429/5xx, interrupção e saturação sem chave real.
- Não analise fotografias com IA nem represente regras locais como IA.
- Preserve HTML/CSS/JavaScript e identidade visual, garantindo responsividade, acessibilidade, feedback, preview, consulta e gestão protegida.

## Segurança e DevOps

- Sem credenciais administrativas padrão/fixas; contas por perfis com hash BCrypt e sessão JDBC. CSRF em operações de escrita e cookies apropriados ao HTTPS.
- Proteja dados pessoais, logs, fotos e variáveis; documente LGPD sem afirmar certificação jurídica.
- Docker/Compose, CI com build/testes/PostgreSQL/imagem e CD manual que promove artefato validado após aprovação humana.
- Documente Azure Container Apps, PostgreSQL gerenciado, Blob privado, disponibilidade, backup, observabilidade e escalabilidade. Não realize nem declare deploy sem autorização e evidência.

## Concluir com evidências

Execute build completo e testes, inicie a aplicação, verifique endpoints e UI, cadastre/consulte ocorrência, confira persistência e upload, valide autenticação/perfis e histórico, teste PostgreSQL e Docker quando disponíveis. Teste Gemini real somente se uma chave válida já estiver configurada com segurança.

Atualize README, diagrama Mermaid, exemplos HTTP, cenários BDD e decisões de proteção de dados. Entregue relatório separando funcionalidades comprovadas, resultados de testes, correções e pendências. Ausência de chave ou recurso Cloud não pode ser disfarçada como teste aprovado.
