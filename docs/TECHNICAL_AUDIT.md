# Auditoria técnica do GameShelf

Base local de desenvolvimento: d2b760e, após a integração do diagnóstico em develop. A árvore possui localização via ResourceBundle, diagnóstico sanitizado, verificador de versões, WiX/jpackage, documentação de assinatura, trava por FileLock e CI para Windows. A versão pública permanece 0.1.0-alpha.1. A classificação sugerida é alpha em validação.

| Área | Estado | Evidência e limites |
|---|---|---|
| Monitoramento | VALIDADO APENAS POR SIMULAÇÃO | Intervalos monotônicos, Clock injetável, origem de processos substituível e identificação por PID e instante inicial. Uma falha na enumeração interrompe a continuidade. Uma lacuna superior a 10 s separa sessões. A persistência periódica continua em 5 s. |
| Banco e backups | IMPLEMENTADO E TESTADO | Transações, busy timeout, verificação de versão antes das alterações, VACUUM INTO, retirada da credencial e integrity_check da cópia. Restauração transacional com cópia de segurança. Testes usam SQLite real e arquivos temporários. |
| Credencial | IMPLEMENTADO E TESTADO | DPAPI no Windows para novas gravações. Migração de texto simples na abertura. Em outra conta uma chave indecifrável fica indisponível, podendo ser substituída. Backups produzidos por versões anteriores continuam sensíveis. |
| Modo offline | IMPLEMENTADO E TESTADO | Uma política comum impede iniciar consultas HTTP da Steam, SteamGridDB e atualizador. Transferências já iniciadas podem concluir. O cache e os launchers externos permanecem independentes. |
| Atividades simultâneas e favoritos | JÁ EXISTENTE E VALIDADO | Os testes cobrem a união de intervalos, identidade por plataforma e preservação de favoritos na reinstalação simulada. |
| Steam, Epic e Roblox | PENDENTE DE TESTE REAL | Descoberta por manifestos e caminhos locais, com fixtures. O leitor limita tamanho e profundidade. |
| Riot | EXPERIMENTAL | A detecção existente requer instalações reconhecidas. Caminhos personalizados e anticheat precisam de evidência de campo. |
| GOG, EA, Ubisoft, Xbox, Battle.net; importação de horas antigas Steam | NÃO IMPLEMENTADO | Cadastro manual disponível. Ainda falta demonstrar fontes de dados estáveis e associação inequívoca à conta e instalação. |
| Release ou assinatura nesta auditoria | NÃO APLICÁVEL | O script existente mantém o UUID do instalador e calcula SHA-256. A publicação e a assinatura não fazem parte desta execução. |

## Decisões e riscos residuais

O monitor enumera caminhos uma vez por amostra e usa o resultado tanto para sessões quanto para diagnóstico. Não guarda um nome genérico como prova de identidade e dispensa cache persistente de PID. Quando dois registros correspondem ao mesmo processo, o monitor omite a amostra ambígua e mantém ambos os cadastros. A fusão de históricos requer revisão individual. A mudança brusca do relógio separa sessões. Hooks permitem uma última tentativa de gravação, mas a recuperação após interrupção forçada depende do último checkpoint.

A migração preserva as tabelas existentes e estabelece user_version=1. A opção foreign_keys está habilitada nas conexões, sem reconstrução destrutiva de tabelas antigas. O journal permanece no modo existente. O backup usa um snapshot do SQLite; a restauração preserva a credencial do computador atual. Arte escolhida pelo usuário deve ser copiada separadamente. Restaurações grandes ainda podem atrasar consultas em memória durante a substituição.

As imagens locais, remotas e em cache passam por limite de bytes, verificação do formato JPEG/PNG e dimensões antes da decodificação. A biblioteca já virtualiza linhas e deduplica downloads. Modo offline e ações de restauração têm rótulos nos bundles português e inglês. O botão de jogar também aparece ao receber foco na grade.

Ainda faltam medição prolongada em repouso/bandeja, revisão completa de textos fixos, validação de instalação sobre versão anterior, fallback inequívoco para processos protegidos, validação em desktop real da sinalização da primeira janela pela segunda instância e validação ponta a ponta de todas as interações com rede. Recarga de CSS em produção não foi introduzida. Manter o runtime empacotado atualizado e repetir testes reais após atualizações.

## Reproduzir

Execute no Windows com JDK compatível com release 21:

    mvn "-Dgameshelf.uiTest=true" clean verify
    mvn "-Dgameshelf.uiTest=true" clean package
    mvn javafx:run

O roteiro doméstico está em MANUAL_TEST_PLAN.md. Os testes gráficos usam uma biblioteca temporária. O resultado de build local não valida a execução hospedada do GitHub Actions ou o instalador assinado.

## Inventário desta alteração

- Modificado: .github/workflows/ci.yml
- Modificado: PRIVACY.md
- Modificado: README.md
- Modificado: src/main/java/br/com/gameshelf/Detector.java
- Modificado: src/main/java/br/com/gameshelf/GameDatabase.java
- Modificado: src/main/java/br/com/gameshelf/GameShelfApp.java
- Modificado: src/main/java/br/com/gameshelf/GridDb.java
- Modificado: src/main/java/br/com/gameshelf/Launcher.java
- Modificado: src/main/java/br/com/gameshelf/Library.java
- Modificado: src/main/java/br/com/gameshelf/LibraryView.java
- Modificado: src/main/java/br/com/gameshelf/MainWindow.java
- Modificado: src/main/java/br/com/gameshelf/ManifestParser.java
- Modificado: src/main/java/br/com/gameshelf/Playtime.java
- Modificado: src/main/java/br/com/gameshelf/SettingsView.java
- Modificado: src/main/java/br/com/gameshelf/service/diagnostics/DiagnosticReport.java
- Modificado: src/main/java/br/com/gameshelf/service/diagnostics/PlatformDiagnostics.java
- Modificado: src/main/java/br/com/gameshelf/service/diagnostics/ProcessDiagnostics.java
- Modificado: src/main/java/br/com/gameshelf/service/update/UpdateService.java
- Modificado: src/main/java/br/com/gameshelf/ui/Artwork.java
- Modificado: src/main/java/br/com/gameshelf/ui/DiagnosticsView.java
- Modificado: src/main/resources/css/gameshelf.css
- Modificado: src/main/resources/i18n/messages.properties
- Modificado: src/main/resources/i18n/messages_en_US.properties
- Modificado: src/main/resources/i18n/messages_pt_BR.properties
- Modificado: src/test/java/br/com/gameshelf/DiagnosticsTest.java
- Criado: .github/dependabot.yml
- Criado: docs/MANUAL_TEST_PLAN.md
- Criado: docs/TECHNICAL_AUDIT.md
- Criado: src/main/java/br/com/gameshelf/Credential.java
- Criado: src/main/java/br/com/gameshelf/NetworkPolicy.java
- Criado: src/test/java/br/com/gameshelf/StabilityTest.java

### Triagem de distribuição e dependências

O instalador existente usa o UUID de upgrade fixo e o projeto já documenta assinatura/SmartScreen. A ficha de validação inclui upgrade com biblioteca existente. Em cada atualização do runtime ou do WiX, repetir a geração e o upgrade em Windows limpo antes da distribuição. O workflow usa Java 21, Maven, testes gráficos, upload de artefatos e manifesto de hashes. Para conferir um arquivo no PowerShell, compare o resultado de Get-FileHash -Algorithm SHA256 com a entrada correspondente do manifesto. A origem da distribuição também deve ser autenticada.

Permanece pendente uma análise de vulnerabilidades de dependências baseada em advisories atualizados. O Dependabot foi configurado para propor atualizações mensais. O script de distribuição e o suporte de atualização no lugar dependem de teste do instalador real. Ainda não foi aferido o impacto de bibliotecas com milhares de sessões na duração da restauração.

O projeto Maven compila sem o IDE; nbactions.xml apenas configura as ações do NetBeans. A revisão não mudou a versão pública, o UUID de upgrade ou o formato de argumentos do lançador. O esquema v1 mantém compatibilidade de dados com a importação de properties e recusa bancos que declarem uma versão superior.

## Resultados locais

Com a interface gráfica habilitada, clean verify e clean package passaram no Windows. Cada execução final teve 26 testes aprovados, 0 falhas, 0 erros e 0 ignorados. O teste de fumaça contém 23 verificações internas. O teste de diagnóstico que antes esperava duas sessões para um executável ambíguo passou a exigir conflito informado e zero sessões, seguido de uma sessão quando a ambiguidade é removida.

Na amostra de WindowTest durante o verify, o processo de testes reportou 64.798.264 bytes de heap, 386.494.464 bytes de RAM de processo e 22 threads. Durante o package, reportou 70.338.288 bytes, 379.060.224 bytes e 22 threads. As amostras incluem JUnit, JavaFX e a criação de telas. O consumo prolongado do aplicativo em repouso e o custo de uma varredura com jogos reais continuam pendentes.
