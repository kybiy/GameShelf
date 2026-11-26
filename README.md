# GameShelf

> **Status do projeto (Alpha):** O GameShelf está em desenvolvimento ativo (`v0.1.0-alpha.1`). A detecção de jogos reais e o contador de horas ainda precisam de validação em computadores com jogos instalados.

Biblioteca de jogos para Windows 10 e 11, com interface JavaFX em português. Reúne jogos detectados no computador e cadastros manuais. O monitor registra sessões inclusive quando o jogo é iniciado fora do GameShelf.

## Download e instalação no Windows

Baixe a versão mais recente em **[GitHub Releases](https://github.com/kybiy/GameShelf/releases/latest)** (ou diretamente a [v0.1.0-alpha.1](https://github.com/kybiy/GameShelf/releases/tag/v0.1.0-alpha.1)):

- **Instalador (`GameShelf-*-win-x64.exe`)**: instalação por usuário (sem exigir administrador), com atalho no Menu Iniciar, opção de atalho na Área de Trabalho e desinstalador no Windows. Não requer Java instalado.
- **Versão portátil (`GameShelf-*-portable-win-x64.zip`)**: extraia o ZIP e execute `GameShelf.exe` diretamente, sem instalação.

Seus jogos, horas, favoritos, configurações e banco de dados ficam salvos separadamente em `%LOCALAPPDATA%\GameShelf` e são preservados ao atualizar o aplicativo.

> **Aviso do Windows SmartScreen:** os executáveis desta versão Alpha ainda não possuem assinatura digital (a solicitação de assinatura gratuita pela SignPath Foundation está **pendente de aprovação** — veja [CODE_SIGNING.md](CODE_SIGNING.md)). Ao abrir o instalador ou `GameShelf.exe` pela primeira vez, o Windows Defender SmartScreen pode exibir um aviso; clique em **Mais informações** e depois em **Executar assim mesmo** se desejar prosseguir. Consulte também a [Política de Privacidade (PRIVACY.md)](PRIVACY.md).

## Executar pelo código-fonte

Use um JDK 21 e Maven. A primeira compilação precisa de internet para obter as dependências. No NetBeans, abra a pasta que contém `pom.xml` e pressione **F6**. A ação **Run** usa o objetivo `javafx:run`.

```sh
mvn javafx:run
```

Para gerar e abrir o pacote no mesmo sistema operacional da compilação, execute os comandos abaixo. Mantenha a pasta `target/lib` ao lado do JAR ao copiá-lo.

```sh
mvn clean package
java -Xms16m -XX:+UseSerialGC -jar target/gameshelf-0.1.0.jar
```

O aplicativo usa JavaFX Controls 21.0.8 e SQLite JDBC 3.49.1.0. JUnit Jupiter é usado nos testes. O Windows mantém os controles nativos de movimentação, redimensionamento e maximização da janela.

## Usar a biblioteca

- **Todos os jogos** exibe capas em uma grade responsiva ou em lista. Pesquise pelo nome, filtre por plataforma e instalação e ordene por nome, tempo, sessão ou adição. Favoritos e recentes têm páginas próprias.
- Clique em uma capa para abrir os detalhes. O botão **Jogar** também aparece ao passar o mouse sobre a capa de um jogo instalado. O menu de contexto reúne favoritos, imagens, monitoramento e remoção de cadastros manuais.
- **Adicionar jogo** aceita executáveis e atalhos do Windows. Também é possível soltá-los na biblioteca. As opções adicionais incluem plataforma, pastas, argumentos (um por linha), executável monitorado, arte e horas iniciais. Para atalhos, o Windows aplica os argumentos e o diretório de trabalho definidos no próprio atalho. Escolha o executável do jogo para contabilizar suas sessões.
- As capas e os banners da Steam são buscados em segundo plano pelo AppID. O cache permite reutilização offline. Qualquer jogo aceita arquivos PNG ou JPEG escolhidos pelo usuário. Mantenha essas imagens locais nos caminhos cadastrados. Quando uma imagem está indisponível, a biblioteca usa uma ilustração própria.
- Para pesquisar outras artes, cadastre sua chave pessoal em **Configurações → SteamGridDB**. No menu do jogo, escolha **Pesquisar no SteamGridDB**, confirme a edição e escolha uma imagem. A chave é opcional e fica no banco local, protegida por DPAPI no Windows e excluída das novas cópias de backup.
- **Configurações** permite escolher idioma (`Sistema`, `Português (Brasil)` e `English (United States)`) sem reiniciar, tema, destaque, dimensões das capas, animações, menu recolhido, busca inicial, comportamento de bandeja, tamanho do cache e canal de atualizações (`Estável` ou `Experimental`). Em janelas estreitas, o menu recolhe automaticamente até que você escolha uma preferência.
- **Estatísticas** mostra atividade diária e mensal, calendário, ranking e distribuição de horas por plataforma. O detalhe do jogo inclui sessão em andamento, últimos 7 e 30 dias e histórico com horário e duração.
- **Verificador de atualizações** consulta o GitHub Releases em segundo plano (sem token, respeitando limites da API) e exibe notas da versão, data, tamanho dos arquivos e botões seguros para download oficial.
- **Diagnóstico** reúne informações do sistema, inspeção detalhada dos detectores de cada plataforma (incluindo Steam e Epic), monitoramento de processos, integridade não destrutiva do SQLite, consumo de JVM Heap / RAM / CPU, logs filtráveis com rotação e exportação sanitizada em TXT e JSON.

## Detecção e tempo de jogo

| Plataforma | Como funciona |
| --- | --- |
| Steam | Lê `libraryfolders.vdf` e os manifestos ACF, inclusive em discos secundários. Inicia o jogo pelo AppID. |
| Epic Games | Lê os manifestos locais `.item` e usa o protocolo Epic para abrir o jogo. |
| Roblox | Identifica o cliente instalado pelo navegador e acompanha as pastas de versões. O botão Jogar abre o catálogo oficial. |
| Riot Games | Detecção experimental de League of Legends e VALORANT na pasta padrão por unidade. O monitor usa o executável da partida. |
| Outras plataformas | Cadastro manual por executável ou atalho. |

O monitor verifica os processos aproximadamente a cada dois segundos. Steam e Epic usam a pasta da instalação e exclusões de launchers comuns. Jogos manuais usam o caminho do executável monitorado. Para jogos com launchers intermediários, processos protegidos ou runtimes compartilhados, a identificação depende dos caminhos disponíveis no Windows. Um `javaw.exe` compartilhado pode precisar de uma instalação de runtime exclusiva para identificar o jogo corretamente.

Múltiplos processos do mesmo jogo contam uma vez. Cada jogo aberto acumula o próprio tempo. Os gráficos de atividade geral unem períodos simultâneos. O total de horas dos jogos inclui horas antigas e iniciais. Os gráficos por data usam somente sessões observadas.

Uma lacuna de monitoramento superior a dez segundos inicia uma nova sessão. As gravações periódicas, a cada cinco segundos, preservam o tempo e a última observação juntos. Após uma interrupção, o intervalo com o aplicativo fechado fica fora da contagem. Fechar a janela pode manter o monitor ativo na bandeja. **Sair do GameShelf** encerra o processo.

## Dados e migração

O banco é `%LOCALAPPDATA%\GameShelf\library.db` e as imagens baixadas ficam na subpasta `cache`. Se `LOCALAPPDATA` estiver ausente, o aplicativo usa `~/.gameshelf`. Uma trava impede duas instâncias de gravarem na mesma biblioteca.

Na primeira abertura, o GameShelf importa o antigo `library.properties` em uma única transação. Preserva IDs, caminhos, favoritos, estado de instalação, executável de monitoramento e segundos acumulados. O arquivo original permanece disponível. Uma falha de migração impede a abertura e permite corrigir o arquivo e tentar novamente. Horas antigas sem horário conhecido permanecem apenas no total.

Em **Configurações → Dados locais**, exporte a biblioteca ou as sessões em CSV, ou salve um backup SQLite. Para restaurar manualmente, encerre o aplicativo pela bandeja, guarde uma cópia do banco atual e coloque o backup no diretório de dados com o nome `library.db`. As imagens escolhidas em arquivos locais devem ser copiadas separadamente.

## Organização

| Arquivos principais | Responsabilidade |
| --- | --- |
| `Main`, `GameShelfApp`, `DesktopTray` | Inicialização, instância única e bandeja |
| `MainWindow`, `LibraryView`, `ActivityView`, `SettingsView`, `GameActions` | Janela, biblioteca virtualizada, detalhes, estatísticas, preferências e diálogos |
| `ui/Ui`, `ui/Poster`, `ui/Artwork`, `GridDb`, `resources/css/gameshelf.css` | Componentes, ilustrações, imagens, catálogo e tema |
| `Library`, `GameDatabase`, `Game`, `Session` | Dados da biblioteca, transações, migração e sessões |
| `Playtime`, `Activity` | Monitoramento e agregação de intervalos |
| `Detector`, `ManifestParser`, `Launcher` | Descoberta, leitura de manifestos e inicialização |

## Validação

```sh
mvn clean test
mvn clean package
```

Os testes exercitam os detectores com instalações temporárias, a associação de processos, a persistência, a migração, o backup e o cálculo de períodos simultâneos. Para testar a interface em uma sessão gráfica, execute o comando abaixo. As capturas ficam em `target/ui-test`. Nenhum jogo de exemplo é cadastrado na biblioteca do usuário.

```sh
mvn "-Dgameshelf.uiTest=true" test
```

Acrescente `"-Dgameshelf.liveArt=true"` para incluir o download real de capas e de um banner da Steam no teste gráfico. A abertura de launchers reais, permissões de processos e a bandeja dependem da instalação local. A integração opcional com SteamGridDB exige uma chave válida.

## Desenvolver em outro computador

Instale o JDK 21 pelo instalador do [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21). Habilite as opções para definir JAVA_HOME e adicionar o Java ao PATH. Instale o Apache Maven ou use o Maven incluído no NetBeans. Confira o JDK ativo com `mvn -version`.

Com o repositório público no GitHub, execute:

```sh
git clone https://github.com/kybiy/GameShelf.git
cd GameShelf
mvn clean package
mvn javafx:run
```

O projeto abre com uma biblioteca vazia quando não há jogos instalados. Os dados pessoais são criados automaticamente em cada computador (`%LOCALAPPDATA%\GameShelf\library.db` ou `~/.gameshelf`). As bibliotecas nativas são resolvidas pelo Maven. A integração da barra de título do Windows utiliza JNA.

Para continuar o desenvolvimento, atualize a branch com `git pull --ff-only`, revise as alterações com `git diff`, faça o commit dos arquivos desejados e execute `git push`.

## Limitações conhecidas

- **Modo de demonstração**: o aplicativo distribuído não possui um modo de demonstração selecionável na interface; os testes gráficos (`-Dgameshelf.uiTest=true`) criam jogos de exemplo apenas em um banco temporário.
- **Processos protegidos e runtimes compartilhados**: jogos com *anti-cheat* restritivo, *launchers* intermediários ou runtimes compartilhados (como `javaw.exe`) podem exigir configuração manual do executável monitorado.
- **Plataformas experimentais**: a detecção da Riot Games (League of Legends e VALORANT) considera a pasta padrão por unidade e o executável da partida; a validação com instalações reais de jogos ainda é limitada.
- **Sistema operacional**: a detecção automática de lojas e a integração de barra de título nativa visam Windows 10 e 11.

## Roadmap de desenvolvimento

- Validação em campo dos detectores e do contador de horas com instalações reais de jogos.
- Ampliação da cobertura de detectores para novas lojas e plataformas.
- Melhorias contínuas na experiência de biblioteca, estatísticas e gerenciamento de capas.

## Gerar executáveis para Windows

Para gerar o instalador (`.exe`), a versão portátil (`.zip`) e o arquivo `SHA256SUMS.txt` em `dist/`:

1. Tenha instalados JDK 21+, Apache Maven e [WiX Toolset 3.11+](https://wixtoolset.org/) (ou extraia `wix314-binaries.zip` em `%LOCALAPPDATA%\Programs\wix314`).
2. Execute no PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1
```

Para gerar uma versão com sufixo de pré-lançamento (como `0.1.0-alpha.1`), informe `-ReleaseVersion`:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1 -ReleaseVersion "0.1.0-alpha.1"
```

## Contribuição, privacidade e segurança

- **Reportar bugs ou enviar feedback**: abra uma issue em [GitHub Issues](https://github.com/kybiy/GameShelf/issues) usando os modelos disponíveis. Nunca anexe seu `library.db` pessoal, chaves do SteamGridDB ou logs com informações sensíveis.
- **Contribuir com código**: leia o guia em [CONTRIBUTING.md](CONTRIBUTING.md) para entender o fluxo de branches (`main`, `develop`, `feat/*`, `fix/*`, `refactor/*`, `docs/*`), execução de testes e abertura de Pull Requests.
- **Privacidade**: consulte [PRIVACY.md](PRIVACY.md) para detalhes sobre armazenamento local, conexões externas e exclusão de dados.
- **Assinatura de código**: consulte [CODE_SIGNING.md](CODE_SIGNING.md) (solicitação à SignPath Foundation pendente de aprovação).
- **Vulnerabilidades**: consulte [SECURITY.md](SECURITY.md).

## Licença

Este projeto está licenciado sob a [Licença MIT](LICENSE).

---

## 🇺🇸 English Overview

**GameShelf** is an open-source universal game library and playtime tracker for Windows 10 and 11 (x64), built with **Java 21**, **JavaFX 21**, and **SQLite**.

- **Bilingual UI (`pt-BR` / `en-US`)**: switches live between Brazilian Portuguese and English (with automatic Windows locale detection on first run) without restarting or interrupting playtime tracking.
- **Update Checker**: queries public GitHub Releases (`kybiy/GameShelf`) asynchronously with semantic versioning (`Stable` and `Experimental` channels) and opens official downloads in your browser.
- **Diagnostics Panel**: inspects platform detectors (Steam, Epic Games, Riot Games experimental, Local games + Roblox, plus unimplemented stores), active process monitoring, non-destructive SQLite integrity, JVM Heap / process RAM / CPU usage, rotating logs, and sanitized TXT/JSON report export.
- **Build & Test**:
  ```sh
  mvn -B clean test
  mvn -B clean package
  ```
- **License**: [MIT License](LICENSE). See [PRIVACY.md](PRIVACY.md), [CODE_SIGNING.md](CODE_SIGNING.md), and [CONTRIBUTING.md](CONTRIBUTING.md).

## Estabilidade e recuperação

Em Configurações, o modo offline impede novas consultas do próprio aplicativo. O comando **Restaurar backup** valida o arquivo e salva uma cópia de segurança do banco atual antes da substituição. A chave DPAPI pertence ao usuário do Windows; ao migrar para outro PC, cadastre a chave novamente. Os relatórios de desenvolvimento estão em [auditoria técnica](docs/TECHNICAL_AUDIT.md) e [validação doméstica](docs/MANUAL_TEST_PLAN.md).
