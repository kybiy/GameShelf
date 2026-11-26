# Contribuindo com o GameShelf

Obrigado pelo interesse em contribuir com o **GameShelf**! Este guia descreve o fluxo de trabalho para colaboradores e contribuidores externos.

## 1. Requisitos e configuração do ambiente

- **Sistema operacional**: Windows 10 ou 11 (alvo principal da aplicação).
- **Java**: JDK 21 (ex.: [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)) com `JAVA_HOME` e `PATH` configurados.
- **Build**: Apache Maven (ou o Maven integrado ao Apache NetBeans).

### Clonar ou fazer fork

- **Colaboradores com acesso direto**:
  ```sh
  git clone https://github.com/kybiy/GameShelf.git
  cd GameShelf
  ```
- **Contribuidores externos**: faça um *fork* no GitHub, clone o seu fork e adicione o repositório principal como `upstream`:
  ```sh
  git clone https://github.com/<seu-usuario>/GameShelf.git
  cd GameShelf
  git remote add upstream https://github.com/kybiy/GameShelf.git
  ```

### Abrir no NetBeans ou executar pelo terminal

- No **NetBeans**, abra a pasta que contém `pom.xml` e pressione **F6** (`javafx:run`).
- Pelo **terminal**:
  ```sh
  mvn javafx:run
  ```

Não é necessário possuir jogos instalados ou chaves de API para compilar, executar a interface ou rodar os testes.

## 2. Organização das branches

- `main`: código estável, protegido por Pull Request e verificações de CI.
- `develop`: branch de integração para novas funcionalidades e correções.
- `feat/<descricao>`: novas funcionalidades (ex.: `feat/gog-detector`).
- `fix/<descricao>`: correções de bugs (ex.: `fix/epic-manifest-path`).
- `refactor/<descricao>`: reorganização de código sem alteração de comportamento.
- `docs/<descricao>`: melhorias na documentação.

Crie novas branches a partir de `develop` (ou de `main` em hotfixes):

```sh
git checkout develop
git pull --ff-only origin develop
git checkout -b feat/minha-funcionalidade
```

## 3. Implementação e testes

Antes de abrir um Pull Request, garanta que o projeto compila e todos os testes passam sem depender de jogos locais ou credenciais pessoais:

```sh
mvn -B clean verify
```

Para validar também a interface em uma sessão gráfica (gera capturas em `target/ui-test` usando um banco temporário com jogos de exemplo):

```sh
mvn "-Dgameshelf.uiTest=true" test
```

## 4. Commits e Pull Requests

- Revise suas alterações com `git status` e `git diff` antes de fazer commit.
- **Nunca inclua** tokens, chaves do SteamGridDB, bancos `library.db`, arquivos `.env`, logs ou caminhos absolutos do seu computador.
- Escreva mensagens de commit claras e objetivas descrevendo o que foi alterado.
- Envie sua branch e abra um **Pull Request** direcionado à branch adequada (`develop` ou `main`), preenchendo o modelo de Pull Request.

```sh
git push -u origin feat/minha-funcionalidade
```

## 5. Reportando bugs

Antes de abrir uma issue, verifique se o problema já foi reportado. Ao criar uma nova issue, utilize os modelos disponíveis em `.github/ISSUE_TEMPLATE/` e inclua passos para reproduzir, comportamento esperado e versão do Java/Windows. Nunca anexe seu `library.db` pessoal ou chaves de API.
