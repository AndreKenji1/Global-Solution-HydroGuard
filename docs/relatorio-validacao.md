# Relatório de evolução e validação — HydroGuard Citizen

## Resultado e escopo

O código enviado foi evoluído mantendo Java 17, Spring Boot 3.5.16 e a interface HTML/CSS/JavaScript. PostgreSQL é o banco principal; H2 permanece para testes e para o perfil explícito `local`. A entrega é um protótipo acadêmico executável e verificado. Não houve implantação de produção nem integração com concessionárias.

## Diagnóstico inicial

Foram lidos `README.md`, `AGENTS.md`, `CODEX_TASK.md`, os documentos de arquitetura, os cenários e os exemplos HTTP antes das alterações. A estrutura original já tinha cadastro, consulta por id, listagem, triagem textual, upload e frontend, mas a existência do código não foi tomada como prova de funcionamento.

O primeiro `mvn clean verify` não pôde executar porque Maven não estava instalado. Após preparar Maven, o ambiente apresentou falta de compilador JDK e necessidade de configurar o proxy/certificados para resolução de dependências. As ferramentas foram preparadas fora do projeto, sem desativar TLS. Uma cópia separada do ZIP original foi então verificada com Java 17: **9 testes, zero falhas, BUILD SUCCESS**. Isso comprovou o build original; não comprovava segurança, robustez do upload ou integração real com Google.

Problemas identificados incluíam token administrativo padrão no código/configuração, dados completos em listagem pública, protocolo sequencial, upload sem autorização individual e validado apenas por assinatura, falta de migrações, testes insuficientes da Gemini, filtros somente no navegador e workflows incompletos.

## Funcionalidades e correções concluídas

| Área | Implementação e evidência |
|---|---|
| Cadastro e persistência | DTO validado e normalizado, protocolo UUID, HTTP 201, gravação PostgreSQL e histórico inicial. Caracteres de controle incompatíveis com PostgreSQL são rejeitados com 400. |
| Acompanhamento | Consulta por protocolo completo; resumo sem endereço exato, descrição original, id interno ou autor administrativo. Protocolo deve ser tratado como informação de acesso. |
| Gestão | Login por senha BCrypt externa ao código, sessão JDBC, perfis `ADMIN` e `ANALISTA`, listagem paginada e filtros reais de status/prioridade/busca. |
| Status e auditoria | Alterações autorizadas para ADMIN, histórico com autor/data e controle de concorrência otimista. Detalhes administrativos carregam o histórico completo. |
| Dashboard | Agregações SQL do banco, sem depender do tamanho da página ou de dados simulados. |
| Fotografias | Autorização prévia, token individual de uso único por 30 minutos, hash no banco, limites de tamanho/resolução, decodificação e reencodificação JPEG/PNG. Fotos não são públicas; metadados e conteúdo anexado são removidos. |
| Armazenamento | Adaptador local com proteção de caminhos/permissões e adaptador Azure Blob privado com Managed Identity ou configuração externa. Limpeza de objetos acompanha commit/rollback. |
| Gemini | Chamada real implementada, schema JSON, validação estrita de campos/tipos/enum/tamanho, timeout de 8 segundos e até quatro chamadas concorrentes. Texto separado das instruções; fotografias não são enviadas. Falhas usam `REGRAS_CONTINGENCIA`; ausência de chave usa `REGRAS`. |
| Interface | Dashboard, prévia da foto, cadastro, acompanhamento, histórico, login/logout, filtros/paginação, indicadores da origem da classificação, feedback e acessibilidade. Identidade visual preservada, sem migração de framework. |
| Segurança | CSRF em mutações, rotação do id de sessão no login, invalidação no logout, cookie HttpOnly/SameSite=Strict, CSP e demais cabeçalhos, erros sem detalhes internos, limite local de tentativas de login e nenhuma credencial administrativa padrão. |
| Banco | Migrações Flyway V1/V2 e Hibernate `validate`; sessões compartilhadas no JDBC. Sem limpeza ou migração destrutiva de dados existentes. |
| Colaboração e entrega | Gitignore/Gitattributes, instruções de contribuição, modelo de PR, Docker/Compose, CI/CD e documentação de operação/LGPD. |

## Build e testes automatizados

Executado o equivalente a `mvn -Ppostgres-it clean verify`, com Maven 3.9.11 e JDK 17.0.20.1. Opções locais de cache, proxy e truststore foram necessárias apenas ao ambiente de execução e não contêm segredos na entrega.

| Suíte | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| JUnit 5, Mockito, MockMvc e armazenamento, com H2/mocks | 100 | 0 | 0 | 0 |
| Failsafe contra PostgreSQL 16 real | 2 | 0 | 0 | 0 |
| **Total** | **102** | **0** | **0** | **0** |

A suíte inclui entradas inválidas, normalização, HTTP/JSON, filtros/paginação, agregações, ausência de dados privados nas consultas, perfis/CSRF, login/rotação/logout, upload válido/forjado/vazio/excessivo, uso único/expiração, limites de resolução/metadados, armazenamento local/Blob mockado, concorrência/rollback e regras de triagem. Os testes Gemini cobrem sucesso, payload/schema, JSON inválido ou incompleto, campos extras/tipos/enum/tamanhos inválidos, bloqueio/truncamento, HTTP 401/403/429/500/503, timeout, interrupção e limite de concorrência. Não exigem chave real.

JaCoCo registrou **93,0% de linhas** e **71,7% de ramos** cobertos no backend instrumentado. Cobertura não substitui testes externos nem comprova ausência de falhas.

A primeira execução da suíte ampliada teve uma expectativa de teste divergente: protocolo malformado esperava 404, enquanto o contrato corretamente retornava 400. A expectativa foi ajustada, preservando 404 para protocolo válido inexistente. A revisão posterior identificou e corrigiu o caso real de caracteres NUL que o PostgreSQL rejeita e a omissão de histórico no detalhe administrativo.

## Aplicação iniciada e fluxos reais

O JAR foi iniciado com Java 17 e PostgreSQL 16 em contêiner. Verificados por HTTP real: health/info 200, cadastro 201, consulta por protocolo 200, filtros, alteração de status e auditoria, foto sanitizada recuperada, upload repetido 403, listagem/foto anônimas 401 e entrada NUL 400. A origem `REGRAS` foi confirmada sem chave Gemini.

Playwright/Chromium executou os fluxos reais em **1440×1000** e **390×844**: cadastro com PNG, prévia, envio e fotografia privada, consulta sem endereço, login/CSRF, filtros, alteração de status, detalhe com autor e dois eventos, logout e indicadores atualizados. Não houve erro JavaScript ou HTTP inesperado nem transbordamento horizontal; o 401 inicial de `/api/auth/me` é esperado para visitante anônimo. O teste portátil em `tests/ui` também foi instalado e executado com sucesso contra o backend real.

A persistência foi conferida diretamente por SQL. A aplicação e o contêiner PostgreSQL foram reiniciados sem apagar volumes. **Protocolo, status, histórico, fotografia e sessão administrativa JDBC continuaram disponíveis após os reinícios.**

O utilitário `tools/GerarSenha.java` foi executado em terminal interativo: leitura sem eco, confirmação e geração BCrypt; execução sem terminal é recusada. A senha de validação era efêmera, permaneceu fora do projeto e não integra o pacote.

## Docker e DevOps

O PostgreSQL e a aplicação foram executados com Docker Compose, reutilizando os volumes. A aplicação passou no health HTTP e no healthcheck Docker. A imagem efetivamente executada usa UID/GID 10001, raiz somente leitura, capacidades removidas, `no-new-privileges`, `/tmp` temporário e volume privado de fotografias gravável. A consulta, foto e sessão JDBC anteriores continuaram válidas ao transferir a execução do host para o contêiner, e o cadastro/consulta/upload/status foram novamente exercitados por HTTP no contêiner.

O `Dockerfile` entregue tem o build Maven padrão e a opção `JAR_SOURCE=prebuilt`, que empacota o JAR já verificado. O modo prebuilt gerou a imagem `sha256:e7d4da495aa8b867d03a0a89bb3b0bd1f792b9489d3cbea452a5cc50e0c9b60c`, e essa imagem está executando no Compose. **Entretanto, o comando de build terminou com código 102:** o BuildKit falhou ao gravar um descritor temporário em `content/ingest`, com `ENOSPC`, depois de exportar e registrar a imagem. Repetir sem sandbox apresentou o mesmo erro. Não se registra esse comando como um build Docker aprovado.

A tentativa do modo Maven completo no Docker também esbarrou no limite de disco de 8 GB e no driver `vfs`, que duplica camadas durante a extração do JDK. O build Maven no host e a execução da imagem foram comprovados, mas **a conclusão com código zero do build Docker e a construção Maven integral dentro do contêiner permanecem pendentes de um daemon com armazenamento suficiente**. Nenhum daemon foi reiniciado, banco foi apagado ou volume existente foi descartado.

Workflows validados com **actionlint**, parser YAML e `bash -n`; `docker compose config --quiet` também passou. O CI declara build/testes H2/PostgreSQL e preparação de imagem por SHA. O CD manual promove o artefato do CI por digest, usa Azure OIDC e depende de aprovadores no environment `production`, com proibição de autoaprovação. A validação dessa proteção exige o secret `ENVIRONMENT_READ_TOKEN` com leitura de Administration; o workflow falha fechado quando não configurado.

Os workflows não foram executados no GitHub nesta sessão e os recursos Azure não foram provisionados. A validação sintática não equivale a uma execução do pipeline.

## Pendências e limites efetivos

- **Build Docker:** repetir o build integral em um ambiente com espaço e driver adequados; nesta sessão o BuildKit exportou a imagem, mas falhou na conclusão por `ENOSPC`. A execução da imagem/Compose foi verificada.
- **Gemini real:** nenhuma `GEMINI_API_KEY` estava configurada. O cliente real está implementado, mas somente mocks e fallback foram exercitados; falta validar uma chamada ao Google com chave válida e modelo disponível na conta.
- **Azure real:** não houve deploy, criação de recursos ou teste com conta Blob real. Container Apps, PostgreSQL gerenciado, Blob privado, Key Vault, monitoramento, backups, alta disponibilidade e escala estão documentados; o adaptador Blob foi testado por mocks.
- **Configuração no GitHub:** cadastrar variáveis/segredos/OIDC, configurar revisores e permissões do environment, proteger a branch e executar os workflows em um repositório privado.
- **Operação com cidadãos reais:** definir controlador/base legal, retenção e direitos de titulares, moderação de conteúdo e medidas contra abuso distribuído. Texto livre ou a justificativa da IA ainda podem conter informações fornecidas indevidamente pelo usuário; remoção de EXIF não anonimiza o conteúdo visual de uma imagem.
- **Instalações anteriores:** a entrega inicia schemas novos; um banco legado com dados exige backup e migração explícita validada em cópia. Não houve banco legado com dados fornecido no ZIP.

## Arquivos e reprodução

As alterações estão concentradas em `controller/`, `security/`, `service/`, `model/`, `repository/`, `dto/`, `storage/`, configurações e migrações, frontend estático, testes, Docker/Compose, workflows e documentos. O README contém os comandos Windows/IntelliJ, execução com PostgreSQL ou perfil `local`, variáveis e endpoints. `docs/arquitetura.md` contém o diagrama Mermaid. `tests/ui/README.md` explica como repetir o fluxo de navegador com dados sintéticos.

As evidências textuais selecionadas estão em `docs/evidencias/`, e capturas sintéticas em `docs/imagens/`. O pacote exclui credenciais efêmeras, bancos, uploads, caches, `node_modules` e artefatos temporários.
