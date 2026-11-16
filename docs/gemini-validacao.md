# Validação real da integração Gemini

A aplicação funciona sem chave, usando `REGRAS`. Para comprovar uma chamada ao Google, é necessário fornecer uma chave válida ao backend e obter `IA_GEMINI`. Resultado por regras não aprova este teste.

## Configurar o segredo

1. Gere uma chave no [Google AI Studio](https://aistudio.google.com/app/apikey), em um projeto com acesso à Gemini API. Disponibilidade, modelos e cotas dependem da conta e região.
2. Abra [Settings → Secrets and variables → Actions](https://github.com/AndreKenji1/Global-Solution-HydroGuard/settings/secrets/actions) no repositório.
3. Em **New repository secret**, use o nome exato `GEMINI_API_KEY` e cole o valor completo da chave no campo Secret. Salve. Não coloque o valor em chats, commits, capturas ou variáveis públicas.

O workflow recebe o segredo diretamente do GitHub. Não é necessário revelar seu valor ao assistente, e a API do GitHub não permite recuperá-lo em texto puro.

## Executar no GitHub

Em **Actions → Validar Gemini real → Run workflow**, selecione `main` e o modelo disponível na conta, inicialmente `gemini-2.5-flash`. O workflow é manual; o CI comum continua usando mocks e não consome a API real.

A compilação e os testes são executados sem a chave. Depois, somente o backend do teste recebe o segredo. A aplicação inicia em um diretório temporário com H2 explícito, sem alterar bancos existentes ou recursos Azure, e é encerrada ao concluir. O teste envia uma única descrição sintética, sem fotografias ou dados de pessoas.

A aprovação exige saúde do backend, CSRF válido, HTTP 201 no cadastro, `IA_GEMINI`, prioridade/categoria/justificativa válidas e consulta HTTP 200 com a mesma classificação persistida, sem campos privados. A categoria e a justificativa não precisam ter um texto predeterminado.

O artefato `gemini-real-result-<id da execução>` contém apenas um resultado sanitizado: confirmação, prioridade, tamanhos de texto e verificações. Chave, cookies, token de upload, protocolo, respostas completas e logs brutos da aplicação não são publicados.

## Executar localmente

Configure `GEMINI_API_KEY` como segredo do ambiente do processo Java, compile e inicie a aplicação em um banco de demonstração isolado com o perfil `local`. Use um terminal interativo ou gerenciador de segredos; não coloque a chave em argumentos de comandos. Então execute:

```bash
python tools/validar-gemini-real.py --base-url http://127.0.0.1:8080
```

O script não recebe nem lê a chave: ele testa a API do próprio HydroGuard, com cookie e CSRF. Ele cria uma ocorrência sintética, portanto use um ambiente de teste. `REGRAS`, `REGRAS_CONTINGENCIA`, erro HTTP, timeout, saída inválida ou divergência na consulta retornam código diferente de zero.

Em falha externa, confira a validade/restrições da chave, o modelo e as cotas no AI Studio. Não publique a chave nem copie respostas brutas para diagnosticar. Este procedimento preparado não comprova uma chamada real até que uma execução termine com `PASS` e `realGeminiConfirmed=true`.
