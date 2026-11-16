# Evidências de execução

[github-ci.json](github-ci.json) registra o CI real concluído com sucesso em **16/11/2026**, no commit `142e5fe3a0cce80be1f9ef03943ca20f4c32b0fe`: 102 testes e build/upload da imagem Docker. O [run 43799275054](https://github.com/AndreKenji1/Global-Solution-HydroGuard/actions/runs/43799275054) e os identificadores dos artefatos permitem conferir a origem. O acesso aos links respeita as permissões do repositório e a retenção configurada dos artefatos.

O repositório também inclui o resumo JSON dos testes locais e a inspeção sanitizada do contêiner Docker. As capturas em [../imagens/](../imagens/) usam dados sintéticos. Os logs locais de `ENOSPC` são histórico do ambiente `vfs` e não representam uma pendência atual do build Docker, comprovado no GitHub.

Metadados de CI não comprovam deploy Azure, acesso Blob real, aprovação/execução do CD ou Gemini com chave real. Logs brutos com dados sensíveis, credenciais, cookies, bancos e fotografias operacionais não devem entrar no Git. Resultados, comandos e limites estão no [relatório](../relatorio-validacao.md).
