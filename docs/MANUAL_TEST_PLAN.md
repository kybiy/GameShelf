# Validação doméstica — pendente

Use uma biblioteca de teste ou faça backup primeiro. Anote a versão, o fuso horário, o total inicial e a duração medida com um cronômetro. Aguarde pelo menos duas varreduras após iniciar ou fechar cada jogo. Revise o diagnóstico local antes de compartilhar seu relatório sanitizado.

| Cenário e procedimento | Evidência a conferir e critério de aprovação |
|---|---|
| Abrir um jogo pela Steam, com GameShelf já aberto | Manifesto e plataforma corretos, processo associado ao jogo e exatamente uma sessão. Diferença de até dois intervalos de varredura ao comparar com o cronômetro. |
| Abrir um jogo pela Epic, fechar somente o jogo | Manifesto identificado, sessão encerrada e tempo estável enquanto a loja permanece aberta. |
| Abrir cliente Riot, entrar em partida e sair | Fonte Riot e executável corretos. O cliente ocioso não soma horas. Registrar caminho personalizado que exigir cadastro manual. |
| Executar um título com anti-cheat | Conferir erros de acesso e PID observado. Sem interferência no jogo. Se o caminho estiver protegido, registrar a limitação e verificar que nenhum outro jogo recebeu horas. |
| Abrir um launcher intermediário, depois iniciar o jogo | Comparar processos e sessão antes e depois. O tempo do launcher isolado não deve ser somado. |
| Adicionar um executável com espaços e argumentos | Conferir diretório, início correto e identidade do processo. Um jogo iniciado por atalho externo também deve ser contado. |
| Abrir dois jogos durante períodos sobrepostos | Conferir dois jogos ativos e duas sessões. A atividade global deve corresponder à união dos intervalos. |
| Suspender por dois minutos durante o jogo e retomar | Comparar timestamps e duração. Nova sessão após a lacuna, sem crédito pelo período suspenso. |
| Reiniciar o GameShelf com o jogo aberto | Conferir último salvamento, sessões distintas e ausência de tempo durante a parada. Repetir com interrupção inesperada em dados descartáveis. |
| Minimizar para a bandeja por 30 minutos | Conferir threads, acessos negados, processo e tempo acumulado. Medir CPU e memória também com a janela aberta, nas mesmas condições. |
| Desinstalar, verificar a biblioteca e reinstalar | Conferir a identidade estável, a plataforma, favorito, artes e horas. Aprovar se o mesmo registro reaparecer. |
| Exportar backup, modificar um favorito, restaurar | Conferir integridade do banco, favorito anterior, sessões, credencial local e presença da cópia de segurança. Repetir com um arquivo inválido e conferir que os dados permanecem acessíveis. |
| Atualizar o instalador a partir da versão anterior | Salvar diagnóstico e backup, encerrar pela bandeja, instalar sobre a versão anterior e abrir. Conferir versão e integridade, jogos, horas, chaves e preferências antes/depois. Testar separadamente o comportamento com instância aberta. |

Registrar cada execução como aprovada, reprovada ou bloqueada, com duração observada e passos reproduzíveis. Somente depois desses testes classificar uma plataforma como validada com jogos reais.
