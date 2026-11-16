# language: pt
# Especificação documental; estes cenários não são executados por Cucumber.
Funcionalidade: Registro e acompanhamento cidadão de vazamentos
  Como cidadão
  Quero registrar vazamentos e acompanhar seu andamento
  Para contribuir com o uso responsável da água em espaços públicos

  Cenário: Cadastrar relato válido sem chave Gemini
    Dado que o navegador obteve o CSRF e mantém o cookie da mesma sessão
    E não existe chave Gemini configurada
    Quando envia endereço "Rua das Flores, 120" e descrição "Cano rompido com água jorrando na rua"
    Então a API responde 201 com status "ABERTA" e prioridade "ALTA"
    E a origem é "REGRAS"
    E a resposta informa protocolo UUID e token de upload individual
    E a ocorrência e o histórico inicial são persistidos

  Cenário: Rejeitar dados inválidos antes de persistir
    Dado que há CSRF válido
    Quando o cidadão envia descrição vazia ou endereço curto
    Então a API responde 400 com erros de validação
    E nenhuma ocorrência é criada

  Cenário: Negar cadastro sem CSRF
    Quando uma requisição tenta cadastrar um relato sem CSRF válido
    Então a API responde 403
    E nenhuma ocorrência é criada

  Cenário: Consultar protocolo preservando minimização dos dados
    Dado que existe uma ocorrência cadastrada
    Quando o cidadão consulta seu protocolo completo
    Então recebe status, prioridade, classificação e histórico de status e datas
    E não recebe endereço exato, descrição original, id interno, token de upload ou autores administrativos

  Cenário: Indicadores refletirem o banco sem expor relatos
    Dado que há ocorrências com diferentes status e prioridades
    Quando o cidadão consulta o dashboard
    Então recebe contagens agregadas do banco
    E não recebe endereços, protocolos ou fotografias

  Cenário: Administrador autenticado atualizar status
    Dado que uma conta com perfil "ADMIN" autenticou com senha válida
    E o navegador obteve novo CSRF após o login
    Quando altera uma ocorrência para "EM_ANALISE"
    Então a API responde 200
    E a mudança é persistida com data e autor no histórico
    E o acompanhamento pelo protocolo mostra o novo status

  Cenário: Analista consultar sem permissão de alteração
    Dado que uma conta com perfil "ANALISTA" está autenticada
    Quando consulta a listagem, os detalhes ou a foto
    Então o acesso de leitura é permitido
    Quando tenta atualizar um status com CSRF válido
    Então a API responde 403
    E preserva o status anterior

  Cenário: Proteger detalhes e listagem sem autenticação
    Dado que o visitante não autenticou
    Quando solicita listagem, detalhe interno ou foto
    Então a API responde 401

  Cenário: Filtrar página administrativa por status e prioridade
    Dado que a gestão está autenticada
    Quando consulta status "ABERTA", prioridade "ALTA", busca "Centro" e página zero
    Então recebe somente registros compatíveis na página solicitada
    E a resposta informa totais de registros e páginas

  Cenário: Foto válida usar autorização individual uma única vez
    Dado que uma ocorrência acabou de ser criada
    E o token de upload ainda não foi usado nem expirou
    Quando envia JPEG ou PNG válido com CSRF e token corretos
    Então a imagem é decodificada e reencodificada sem metadados originais
    E a foto fica vinculada à ocorrência
    E o token de upload é consumido
    Quando tenta usá-lo novamente
    Então a API responde 403

  Esquema do Cenário: Rejeitar fotografia insegura
    Dado que há CSRF e autorização válidos para uma ocorrência
    Quando é enviado um arquivo com <problema>
    Então a API responde <codigo>
    E nenhuma foto inválida é vinculada
    Exemplos:
      | problema                         | codigo |
      | conteúdo que não é imagem        | 415    |
      | formato SVG, GIF ou WebP         | 415    |
      | mais de 4 MiB                      | 413    |
      | mais de 4096 px por lado ou 12 MP     | 422    |

  Cenário: Gemini classificar texto com sucesso
    Dado que há uma chave configurada de forma segura no backend
    E a API Gemini devolve JSON completo e válido conforme o schema
    Quando o cidadão cadastra um relato
    Então a classificação contém categoria, prioridade e justificativa
    E a origem é "IA_GEMINI"
    E nenhuma fotografia é enviada à Gemini

  Esquema do Cenário: Fallback preservar cadastro com falha Gemini
    Dado que há chave configurada
    E a integração apresenta <falha>
    Quando o cidadão cadastra um relato válido
    Então a ocorrência é criada com classificação por regras
    E a origem é "REGRAS_CONTINGENCIA"
    Exemplos:
      | falha                        |
      | resposta JSON inválida       |
      | timeout                      |
      | autenticação 401 ou 403        |
      | limite 429                    |
      | erro 5xx                      |
      | chamadas concorrentes cheias |

  Cenário: Dados persistirem após reiniciar a aplicação
    Dado que uma ocorrência e sua foto foram persistidas
    Quando a aplicação reinicia usando o mesmo banco e armazenamento
    Então a consulta pelo protocolo continua retornando o registro
    E a gestão autenticada consegue consultar a foto
