# Colaboração

Mantenha o repositório privado durante o desenvolvimento acadêmico. Crie branches curtas como `feat/consulta-protocolo` ou `fix/validacao-upload`, abra pull requests para `main` e peça revisão de outro integrante. Configure proteção de `main`, CI obrigatório e aprovação antes do merge; esses controles são definidos no GitHub e não são ativados apenas por este arquivo.

Use Java 17. Execute `mvn clean verify` antes do PR. Para alterações de banco, inicie um PostgreSQL descartável e execute `mvn -Ppostgres-it clean verify` com `JDBC_URL`, `DB_USERNAME` e `DB_PASSWORD` desse banco. Testes nunca devem apontar para produção. Acrescente migrações Flyway novas; não altere uma migração já aplicada em ambientes compartilhados.

Use dados sintéticos em testes e demonstrações. `.env`, bancos, uploads e relatórios gerados não são versionados. Compartilhe somente `.env.example`, sem valores reais. Preserve contratos de API ou atualize testes, frontend e documentação juntos. Correções de falhas devem incluir regressões que comprovem o comportamento corrigido.

O CI gera uma imagem imutável identificada pelo SHA. A promoção para produção depende do workflow manual e das proteções do environment `production`; veja `docs/cloud.md`.
