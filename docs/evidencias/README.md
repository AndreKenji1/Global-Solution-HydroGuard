# Evidências de execução

[github-ci.json](github-ci.json) registra o CI real concluído com sucesso em **16/11/2026**, no commit `142e5fe3a0cce80be1f9ef03943ca20f4c32b0fe`: 102 testes e build/upload da imagem Docker. O [run 43799275054](https://github.com/AndreKenji1/Global-Solution-HydroGuard/actions/runs/43799275054) e os identificadores dos artefatos permitem conferir a origem. O acesso aos links respeita as permissões do repositório e a retenção configurada dos artefatos.

O repositório também inclui o resumo JSON dos testes locais e a inspeção sanitizada do contêiner Docker. As capturas em [../imagens/](../imagens/) usam dados sintéticos. Os logs locais de `ENOSPC` são histórico do ambiente `vfs` e não representam uma pendência atual do build Docker, comprovado no GitHub.

[gemini-real.json](gemini-real.json) registra a **validação Gemini real aprovada** no [run 43973795166](https://github.com/AndreKenji1/Global-Solution-HydroGuard/actions/runs/43973795166), commit `32a8b46`, em **16/11/2026**. O backend usou `gemini-flash-lite-latest` com timeout de 20 segundos e obteve `IA_GEMINI` persistida. Inclui apenas prioridade, tamanhos de texto, checks e metadados de execução, além das falhas anteriores de modelo404/timeout. O CI desse commit, [run 43973200786](https://github.com/AndreKenji1/Global-Solution-HydroGuard/actions/runs/43973200786), passou 104 testes Java e seis Python. A primeira evidência de 102 testes permanece como histórico.

Esses resultados não comprovam deploy Azure, acesso Blob real ou aprovação/execução do CD. Logs brutos com dados sensíveis, credenciais, cookies, bancos e fotografias operacionais não devem entrar no Git. Resultados, comandos e limites estão no [relatório](../relatorio-validacao.md).
