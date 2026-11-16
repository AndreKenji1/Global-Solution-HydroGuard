# Segurança e decisões de proteção de dados

Este documento registra decisões técnicas do protótipo acadêmico. Ele não equivale a certificação de conformidade nem define sozinho a base legal de uma operação real. Antes de atender cidadãos, o responsável deve estabelecer controlador/operador, finalidade, base legal, transparência, retenção, contratos e canais de atendimento ao titular.

## Dados e finalidades

| Dados | Finalidade | Exposição prevista |
|---|---|---|
| Descrição, endereço/ponto público e bairro | Localizar e compreender o relato | Dados completos somente à gestão autenticada e na resposta imediata ao autor do cadastro |
| Protocolo UUID e classificação | Acompanhamento pelo cidadão | Resumo consultável por quem possui o protocolo |
| Foto sanitizada | Apoio à verificação do vazamento | Leitura por `ADMIN`/`ANALISTA`; volume ou Blob privado |
| Histórico de status, datas e autor administrativo | Rastreabilidade do andamento | Histórico com autor somente à gestão; público recebe status/data |
| Identidade administrativa e sessão | Autenticação e autorização | Backend e banco de sessões, sem senha em texto puro no repositório |
| Descrição enviada à Gemini, quando configurada | Sugestão de categoria/prioridade/justificativa | Fornecedor externo de IA; nunca são enviadas fotos |

Não são solicitados nome, CPF, telefone, e-mail ou cadastro do cidadão. Ainda assim, texto livre, endereço e imagem podem conter dados pessoais. O formulário orienta a relatar apenas locais públicos e não identificar pessoas; essa orientação não constitui anonimização automática. Categoria, justificativa ou bairro também podem conter informações que o usuário forneceu. O protocolo deve ser guardado e compartilhado com cuidado.

## Controles implementados

- **Autenticação:** contas administrativas por variáveis de ambiente com hash BCrypt (custo entre 10 e 16; utilitário gera custo 12), sem credenciais de demonstração alternativas. Não há login público de cidadão nem criação pública de administradores.
- **Perfis:** `ADMIN` pode alterar status e substituir foto; `ANALISTA` tem consulta administrativa. O backend aplica a autorização independentemente da interface.
- **Sessões:** armazenamento JDBC, renovação do id no login, invalidação no logout, cookie HttpOnly/SameSite=Strict e timeout por inatividade de 20 minutos. `COOKIE_SECURE=true` é necessário quando a aplicação usa HTTPS.
- **CSRF:** exigido em todas as mutações, incluindo login, cadastro e upload públicos. O frontend obtém um token da mesma sessão e busca um novo após autenticar/sair.
- **Proteção de login:** limite em memória por endereço de até dez tentativas por minuto e mapa limitado. Em produção com múltiplas réplicas, o gateway deve aplicar limite distribuído; o limite local não é uma proteção global.
- **Minimização:** dashboard apenas agregado; listagem, foto, id e detalhes completos protegidos. O resumo por protocolo retira endereço exato, descrição original e autores, mas não deve ser tratado como dado totalmente anônimo.
- **Upload:** JPEG/PNG decodificável, até 4 MiB, até 4096 pixels por lado/12 milhões de pixels e reencodificação para remover EXIF, texto e conteúdo anexado. Nome original não define caminho no armazenamento. SVG/GIF/WebP e arquivos inválidos são rejeitados.
- **Autorização de upload:** segredo aleatório individual retornado apenas na criação, válido por 30 minutos e para uma foto bem-sucedida. O banco armazena seu hash SHA-256 e a comparação usa tempo constante; `ADMIN` pode gerenciar uma substituição.
- **Concorrência e auditoria:** versão otimista e histórico de mudanças de status com autor/data. A consulta pública recebe somente status/data.
- **Navegador:** CSP restrita à mesma origem, bloqueio de iframe, `nosniff`, política de referrer e permissões de câmera/microfone/geolocalização desabilitadas. O frontend insere dados com APIs DOM seguras.
- **Segredos e erros:** chaves e senhas externas ao código; mensagens de erro não devolvem stack traces nem respostas brutas do fornecedor. Logs de falha Gemini registram apenas a classe da falha; não devem registrar chave, relato, cookie ou token.

A sanitização de imagem reduz riscos de metadados/payloads, mas não remove rostos, placas, pessoas ou textos visíveis. A aplicação não implementa moderação automática ou detecção de dados pessoais. A operação real precisa estabelecer revisão e atendimento a denúncias antes da publicação de imagens.

## Armazenamento e infraestrutura

Localmente, fotos ficam fora do diretório estático e só são servidas por endpoint autorizado. No Azure, o contêiner Blob deve ser provisionado privado e o acesso anônimo à conta deve estar desabilitado. A identidade gerenciada do backend recebe somente o papel necessário no contêiner. Não são geradas URLs públicas/SAS pelo aplicativo.

Use TLS validado entre navegador, backend, PostgreSQL, Blob e Gemini. Restrinja a rede do banco/Blob e o acesso aos backups. Configure criptografia em repouso e políticas de chaves dos serviços gerenciados. O Compose local liga as portas apenas a `127.0.0.1`, exige senha própria do PostgreSQL e não constitui uma implantação pública pronta.

O usuário do banco usado pelo aplicativo precisa escrever ocorrências/histórico/sessões e executar as migrações previstas. Para uma operação mais restrita, separe credencial de migração e credencial de runtime em processo de entrega controlado. Nunca aponte testes de integração para o banco de produção.

## Uso de IA e transferência de dados

A IA recebe somente o texto da descrição, com instruções de sistema separadas e saída estruturada validada. Ela fornece uma sugestão revisável por humano, sem decidir atendimento público ou afirmar gravidade oficial. Sem chave ou com falha/timeout/limite/resposta inválida, o fallback é identificado como regras.

Antes de habilitar Gemini com relatos reais, avalie termos, retenção, localização/processamento e eventual transferência internacional aplicáveis à modalidade de API contratada. Informe o cidadão sobre o uso de fornecedor externo e estabeleça base legal e responsabilidades. Não prometa que o conteúdo não será armazenado ou usado pelo fornecedor sem verificar os termos efetivamente contratados.

## Retenção, titulares e incidentes

Não há nesta entrega um portal de exercício de direitos, rotina automática de anonimização/exclusão nem prazo de retenção definido por lei do projeto. A operação deve definir e documentar:

1. Um prazo necessário à finalidade para ocorrência, foto, histórico, sessões, logs e backups, com revisão periódica.
2. Canal para acesso/correção/eliminação quando aplicável, validação proporcional da solicitação e procedimento autorizado que remova dados e objetos relacionados.
3. Descarte e restauração de backups: soft delete/versionamento não equivalem a eliminação imediata; cópias restauradas precisam respeitar exclusões já aprovadas.
4. Processo de resposta a incidentes, preservação de evidências e comunicação conforme o caso e as orientações da ANPD.
5. Controle de acesso a banco, Blob, logs e pipelines, com revisão de permissões e trilha operacional.

A troca do hash no ambiente não deve ser presumida como encerramento imediato de todas as sessões já existentes; a equipe precisa ter um procedimento para revogá-las. Para maior escala, contas nominativas persistentes, recuperação de acesso, MFA/SSO, limitação distribuída e moderação são evoluções operacionais a validar.

## Verificação e evidências

Testes de autorização, CSRF, entradas inválidas, upload, token de uso único, segredo ausente em consultas e tratamento de respostas Gemini sustentam os controles técnicos. Consulte o relatório para os casos realmente executados. Nenhum teste isolado comprova conformidade integral com a LGPD; use dados sintéticos na avaliação acadêmica.
