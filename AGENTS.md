# Instruções para agentes — HydroGuard Citizen

## Contexto e leitura inicial

Projeto acadêmico FIAP Global Solution 2026, CitizenTech, para registro e acompanhamento de vazamentos. A solicitação atual do usuário prevalece sobre estes documentos. Leia `README.md`, `CODEX_TASK.md`, `docs/arquitetura.md`, `docs/seguranca-lgpd.md`, `docs/cenarios-bdd.feature` e `docs/api.http` antes de alterar o código.

## Stack e organização

- Java 17+, Maven, Spring Boot 3.5.x, MVC, Bean Validation, JPA, Spring Security e Spring Session JDBC.
- PostgreSQL é o banco principal. H2 está restrito aos testes e ao perfil explícito `local`.
- Flyway versiona schemas novos; Hibernate usa `validate`. Bancos legados exigem backup e migração planejada, nunca limpeza automática.
- Frontend HTML/CSS/JavaScript servido pelo backend. Preserve a identidade HydroGuard e evite mudar framework sem necessidade/autorização.
- Controllers tratam HTTP; Services, regras/transações; Repositories, consultas; Entities, persistência; DTOs distinguem criação, gestão e acompanhamento público.
- Gemini classifica somente texto, opcionalmente. Fotos usam adaptador local ou Azure Blob privado.

## Regras de trabalho

1. Inspecione a implementação e execute `mvn clean verify` antes de concluir que o projeto funciona. Registre comando, ambiente e resultado real.
2. Corrija falhas de build/API/segurança/UI e execute novamente os checks relevantes. O perfil `postgres-it` usa um banco de testes isolado via variáveis JDBC.
3. Não trate código, arquivo de workflow ou configuração Cloud como prova de execução. Atualize `docs/relatorio-validacao.md` com evidências e pendências.
4. Nunca coloque senhas reais, chaves Gemini, cookies, tokens de upload ou connection strings reais no Git, HTML, comandos registrados ou logs. Use variáveis/Key Vault e mantenha `.env` fora do versionamento.
5. Não restaure o antigo `X-Admin-Token`. A gestão exige sessão autenticada e perfis; mutações exigem CSRF. `ADMIN` altera status e `ANALISTA` consulta. Sem hash BCrypt configurado, a respectiva conta não existe.
6. Mantenha a listagem/detalhe/foto administrativos e o dashboard apenas agregado. O acompanhamento por protocolo não expõe endereço exato, descrição original ou autores.
7. Upload público exige token individual de uso único, expira em 30 minutos e não pode ser devolvido em consultas. Valide imagens decodificando/reencodificando; mantenha limites e metadados removidos.
8. Sem chave Gemini use `REGRAS`; falha/saída inválida/saturação usa `REGRAS_CONTINGENCIA`. Não apresente esses resultados como IA nem implemente análise de fotos antes de validar a classificação textual.
9. Preserve contratos documentados ou atualize conjuntamente frontend, testes e documentação quando mudar contratos por um motivo concreto.
10. Não apague bancos, volumes, uploads, branches ou dados existentes sem autorização. Não publique ou exponha o repositório privado por iniciativa própria.
11. Não alegue integração com concessionárias, despacho de reparos, economia de água medida ou produção publicada sem implementação e verificação.
12. Explique mudanças e limitações em português do Brasil. Prefira mudanças focadas, testáveis e compatíveis com o objetivo acadêmico.

## Verificação esperada

```bash
mvn clean verify
mvn -Ppostgres-it clean verify  # PostgreSQL de testes configurado e isolado
mvn spring-boot:run            # PostgreSQL principal via ambiente
# Alternativa de demonstração:
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Com Docker disponível, configure `.env` sem valores padrão secretos e verifique `docker compose up --build`. Teste `/actuator/health`, dashboard, cadastro, protocolo, persistência após reinício, upload, autenticação/CSRF, filtros e alteração de status com histórico. Verifique a UI em navegador e viewport móvel quando houver ferramenta disponível. Só teste Gemini real com uma chave já configurada de forma segura; mocks não comprovam conectividade real.

O pipeline Azure exige revisão manual no environment `production` e recursos previamente configurados. A preparação e a documentação não autorizam deploy de produção.
