# Privacy Policy — GameShelf

> *English version below | Versão em português brasileiro abaixo*

---

## 🇺🇸 English

### 1. Overview
**GameShelf** is an open-source, offline-first universal game library and playtime tracker for Windows. Your library, playtime history, and preferences belong to you and remain on your computer.

### 2. Telemetry and Personal Data Collection
**GameShelf does not collect, transmit, or sell any telemetry, analytics, crash reports, usage statistics, or personal data.** There is no GameShelf backend server or developer tracking service.

### 3. Data Stored Locally
All application data is stored locally in `%LOCALAPPDATA%\GameShelf` (or `~/.gameshelf` if `LOCALAPPDATA` is unset):
- **`library.db` (SQLite database)** containing:
  - **Game library (`game` table)**: game ID, title, platform, installation folder, executable path, launch URI, installation status, custom monitored executable, total playtime in seconds, and favorite flag.
  - **Session history (`session` table)**: session ID, game ID, start timestamp, end timestamp, and session duration in seconds.
  - **Application settings (`setting` table)**: UI preferences (theme, accent color, cover size, animations, sidebar state, initial search, tray behavior, image cache limit), date-added timestamps (`added.<gameId>`), custom artwork paths/URLs (`cover.<gameId>`, `banner.<gameId>`), and your optional SteamGridDB API key (`gridDbKey`).
- **Artwork cache (`cache/` folder)**: locally cached `.jpg` cover and banner images downloaded from Steam or SteamGridDB (default size limit: 256 MB, configurable in Settings).
- **Runtime files**: `instance.lock` (single-instance file lock) and, if present from an older version, the legacy `library.properties` file.

GameShelf also reads local game platform manifests (`libraryfolders.vdf`, `appmanifest_*.acf`, Epic `.item` manifests, Roblox versions, Riot Games default directories, and Windows Registry Steam paths) and monitors running Windows process names/paths locally in memory solely to detect installed games and track playtime.

### 4. External Network Connections
GameShelf only makes outbound HTTPS requests in the following cases:
1. **Steam CDN (`https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/<appid>/...`)**:
   - Automatically fetches cover (`library_600x900.jpg`) and banner (`library_hero.jpg`) images in the background for detected Steam games, using only the public numeric Steam AppID. No personal data or authentication is sent.
2. **SteamGridDB API & CDN (`https://www.steamgriddb.com/api/v2/...` and `https://cdn2.steamgriddb.com/...`)** *(strictly optional)*:
   - Contacted **only** when you manually enter your own personal SteamGridDB API key in **Configurações → SteamGridDB** and explicitly trigger **Pesquisar no SteamGridDB** for a game. Sends your `Authorization: Bearer <key>` header and the game search query to SteamGridDB, and downloads the selected static image from `cdn2.steamgriddb.com`.
3. **Roblox Web Catalog (`https://www.roblox.com/games`)**:
   - Opened in your default web browser **only** when you explicitly click **Jogar** on a detected Roblox entry.

### 5. API Key Storage
Your optional SteamGridDB API key (`gridDbKey`) is stored locally in plain text inside the `setting` table of `%LOCALAPPDATA%\GameShelf\library.db`. If you export a SQLite backup via **Configurações → Dados locais**, the `setting` table (including `gridDbKey`) is included in that backup file.

### 6. How to Delete Your Data
You have full control over your local data and can remove it at any time:
- **Delete everything**: close GameShelf completely (**Sair do GameShelf** in the system tray) and delete the `%LOCALAPPDATA%\GameShelf` folder (or `~/.gameshelf`).
- **Delete only your SteamGridDB API key**: open **Configurações → SteamGridDB**, clear the key field, and save.
- **Delete cached artwork**: delete the `%LOCALAPPDATA%\GameShelf\cache` folder while GameShelf is closed.
- **Delete manual backups/exports**: delete any SQLite backup or CSV export files you previously saved to disk.

---

## 🇧🇷 Português (Brasil)

### 1. Visão geral
O **GameShelf** é uma biblioteca universal de jogos e contador de horas de código aberto para Windows, com funcionamento prioritariamente local (*offline-first*). Sua biblioteca, histórico de sessões e preferências pertencem exclusivamente a você e permanecem no seu computador.

### 2. Telemetria e coleta de dados pessoais
**O GameShelf não coleta, envia, vende nem compartilha telemetria, métricas de uso, relatórios de erro ou quaisquer dados pessoais.** Não existe servidor próprio do GameShelf nem serviço de rastreamento dos desenvolvedores.

### 3. Dados armazenados localmente
Todos os dados do aplicativo ficam gravados localmente em `%LOCALAPPDATA%\GameShelf` (ou `~/.gameshelf`, caso `LOCALAPPDATA` não esteja definido):
- **Banco de dados SQLite (`library.db`)**, composto por:
  - **Biblioteca de jogos (tabela `game`)**: identificador do jogo, nome, plataforma, pasta de instalação, caminho do executável, URI de inicialização, estado de instalação, executável monitorado personalizado, total de segundos jogados e marcação de favorito.
  - **Histórico de sessões (tabela `session`)**: identificador da sessão, identificador do jogo, instante de início, instante de término e duração em segundos.
  - **Configurações (tabela `setting`)**: preferências da interface (tema, cor de destaque, tamanho das capas, animações, menu recolhido, busca inicial, comportamento de bandeja e limite do cache de imagens), data de adição (`added.<gameId>`), caminhos/URLs personalizados de capa e banner (`cover.<gameId>`, `banner.<gameId>`) e a chave opcional do SteamGridDB (`gridDbKey`).
- **Cache de imagens (pasta `cache/`)**: arquivos `.jpg` de capas e banners baixados da Steam ou do SteamGridDB (limite padrão de 256 MB, ajustável em Configurações).
- **Arquivos auxiliares**: `instance.lock` (trava de instância única) e, quando existente de versões anteriores, o arquivo legado `library.properties`.

O aplicativo também lê manifestos locais das plataformas de jogos (`libraryfolders.vdf`, `appmanifest_*.acf`, manifestos `.item` da Epic, pastas de versão do Roblox, pastas padrão da Riot Games e caminhos da Steam no Registro do Windows) e consulta processos em execução localmente em memória apenas para detectar jogos instalados e contabilizar o tempo de jogo.

### 4. Conexões externas
O GameShelf realiza conexões HTTPS externas somente nas seguintes situações:
1. **CDN da Steam (`https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/<appid>/...`)**:
   - Busca automaticamente em segundo plano as imagens de capa (`library_600x900.jpg`) e banner (`library_hero.jpg`) para jogos Steam detectados, enviando apenas o AppID numérico público. Nenhuma informação pessoal ou autenticação é enviada.
2. **API e CDN do SteamGridDB (`https://www.steamgriddb.com/api/v2/...` e `https://cdn2.steamgriddb.com/...`)** *(estritamente opcional)*:
   - Acionada **somente** quando você cadastra sua chave pessoal em **Configurações → SteamGridDB** e executa manualmente **Pesquisar no SteamGridDB** em um jogo. Envia o cabeçalho `Authorization: Bearer <chave>` e o termo de pesquisa do jogo, e baixa a imagem estática selecionada de `cdn2.steamgriddb.com`.
3. **Catálogo web do Roblox (`https://www.roblox.com/games`)**:
   - Aberto no seu navegador padrão **somente** quando você clica em **Jogar** em uma entrada detectada do Roblox.

### 5. Armazenamento de chaves de API
Sua chave opcional do SteamGridDB (`gridDbKey`) é salva localmente em texto simples na tabela `setting` do arquivo `%LOCALAPPDATA%\GameShelf\library.db`. Caso você gere um backup SQLite manual em **Configurações → Dados locais**, a tabela `setting` (incluindo `gridDbKey`) também estará presente nesse arquivo de backup.

### 6. Como excluir seus dados
Você pode remover seus dados locais a qualquer momento:
- **Excluir todos os dados**: encerre completamente o GameShelf (**Sair do GameShelf** na bandeja do sistema) e exclua a pasta `%LOCALAPPDATA%\GameShelf` (ou `~/.gameshelf`).
- **Excluir apenas a chave do SteamGridDB**: abra **Configurações → SteamGridDB**, limpe o campo da chave e salve.
- **Excluir apenas o cache de imagens**: exclua a pasta `%LOCALAPPDATA%\GameShelf\cache` com o aplicativo fechado.
- **Excluir backups/exportações**: remova manualmente quaisquer backups `.db` ou exportações `.csv` que você tenha salvo em outras pastas.
