# Smoke opcional da interface

Este teste complementa a suíte Maven e usa a API, o PostgreSQL e o navegador reais. Ele cria **uma ocorrência sintética com PNG**, muda seu status para `EM_ANALISE` e a mantém no banco para inspeção. Execute em uma instância de teste com uma conta temporária do perfil `ADMIN`; não use dados ou credenciais de produção.

## Preparação

Inicie a aplicação conforme o README principal. Em outro terminal, com Node.js 20 ou superior:

```sh
cd tests/ui
npm ci
npx playwright install chromium
```

No Linux, se faltarem bibliotecas do navegador, instale as dependências documentadas pelo Playwright (`npx playwright install --with-deps chromium` em um ambiente autorizado a instalar pacotes).

Configure `HYDRO_SMOKE_URL` com a URL da instância de teste (padrão `http://127.0.0.1:8080`), `HYDRO_SMOKE_USERNAME` e `HYDRO_SMOKE_PASSWORD` com as credenciais da conta temporária. A senha é a utilizada para gerar o hash BCrypt configurado no backend; o hash não deve ser usado como senha no formulário.

No Bash, uma leitura interativa evita registrar a senha no histórico:

```sh
export HYDRO_SMOKE_URL=http://127.0.0.1:8080
read -r -p 'Usuário temporário: ' HYDRO_SMOKE_USERNAME
export HYDRO_SMOKE_USERNAME
read -r -s -p 'Senha temporária: ' HYDRO_SMOKE_PASSWORD
export HYDRO_SMOKE_PASSWORD
npm test
unset HYDRO_SMOKE_PASSWORD
```

No PowerShell:

```powershell
$env:HYDRO_SMOKE_URL = 'http://127.0.0.1:8080'
$env:HYDRO_SMOKE_USERNAME = Read-Host 'Usuário temporário'
$senha = Read-Host 'Senha temporária' -AsSecureString
$env:HYDRO_SMOKE_PASSWORD = [System.Net.NetworkCredential]::new('', $senha).Password
npm test
Remove-Item Env:HYDRO_SMOKE_PASSWORD
```

## Resultado

O comando retorna código `0` e um resumo `PASS` quando os fluxos são aprovados. Salva capturas em `test-results/ui/`, ignorado pelo Git. É possível trocar o diretório por `HYDRO_SMOKE_OUTPUT`. As capturas usam o endereço sintético criado pelo teste e podem mostrar o nome da conta temporária; não as publique caso essa identificação seja sensível.

O teste verifica dashboard do banco, pré-visualização e upload de PNG, consulta pelo protocolo sem endereço, autenticação com CSRF, filtros de status/prioridade/pesquisa, leitura da foto privada, histórico completo com autoria, atualização de status, logout e layouts de 1440×1000 e 390×844 sem overflow. Também verifica erros JavaScript e requisições HTTP inesperadas. O `401` inicial em `/api/auth/me` representa a sessão anônima esperada e é registrado separadamente.

Se já houver Chromium instalado, configure `CHROMIUM_EXECUTABLE_PATH` com seu caminho para usá-lo em vez do navegador baixado pelo Playwright. O teste não exige chave Gemini; use `GEMINI_API_KEY` vazio na instância de teste para obter a classificação previsível por regras.
