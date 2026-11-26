# Code Signing Policy — GameShelf

> *English version below | Versão em português brasileiro abaixo*

---

## 🇺🇸 English

### 1. Current Code Signing Status
An application for free open-source code signing through the **SignPath Foundation** is currently **pending approval**. Current Alpha releases (`v0.1.0-alpha.1` and earlier) are **not yet digitally signed** by SignPath. Once approved and integrated into our release pipeline, future signed releases will include the SignPath Foundation certificate and attribution.

### 2. Project Information & Eligibility
- **Project**: [GameShelf](https://github.com/kybiy/GameShelf) — Universal game library and playtime tracker for Windows, built with Java 21 and JavaFX.
- **License**: [MIT License](LICENSE) (OSI-approved open-source license, with no commercial dual-licensing and no proprietary code).
- **Privacy**: See [PRIVACY.md](PRIVACY.md) (no telemetry, no personal data collection, offline-first local storage).
- **Security**: See [SECURITY.md](SECURITY.md) for responsible vulnerability reporting.
- **Build Integrity**: All binaries submitted for signing will be built strictly from source code in this public repository (`kybiy/GameShelf`) without third-party proprietary or unwanted software components.

### 3. Team Roles and Responsibilities
To ensure transparent governance and secure release authorization under the SignPath Foundation code-signing model, repository responsibilities are assigned as follows:

- **Authors / Committers** *(authorized to author and commit changes to the repository)*:
  - [`kybiy`](https://github.com/kybiy) (Project Owner & Lead Maintainer)
  - Future collaborators granted explicit write access or contributing via reviewed Pull Requests.
- **Reviewers** *(authorized to review Pull Requests targeting protected branches such as `main`)*:
  - [`kybiy`](https://github.com/kybiy) (Project Owner)
  - Designated trusted maintainers with review permissions on `kybiy/GameShelf`.
- **Approvers** *(authorized to approve code signing requests in SignPath)*:
  - [`kybiy`](https://github.com/kybiy) (Project Owner — manual approval required for every release signing request).

All members holding Author, Reviewer, or Approver roles are required to enable Multi-Factor Authentication (MFA) on both GitHub and SignPath accounts.

### 4. SignPath Foundation Attribution
Once the application is approved and releases are signed by the SignPath Foundation, release pages and documentation will display the required attribution:
> *Free code signing provided by SignPath.io, certificate by SignPath Foundation*

---

## 🇧🇷 Português (Brasil)

### 1. Situação atual da assinatura digital
A solicitação de assinatura digital gratuita de código aberto através da **SignPath Foundation** encontra-se **pendente de aprovação**. As versões Alpha atuais (`v0.1.0-alpha.1` e anteriores) **ainda não possuem assinatura digital** da SignPath. Assim que a solicitação for aprovada e integrada ao fluxo de lançamento, as futuras versões assinadas passarão a utilizar o certificado da SignPath Foundation.

### 2. Informações do projeto e elegibilidade
- **Projeto**: [GameShelf](https://github.com/kybiy/GameShelf) — Biblioteca universal de jogos e contador de horas para Windows, desenvolvido em Java 21 e JavaFX.
- **Licença**: [Licença MIT](LICENSE) (licença de código aberto aprovada pela OSI, sem licenciamento duplo comercial e sem código proprietário).
- **Privacidade**: consulte [PRIVACY.md](PRIVACY.md) (sem telemetria, sem coleta de dados pessoais, armazenamento local).
- **Segurança**: consulte [SECURITY.md](SECURITY.md) para relato responsável de vulnerabilidades.
- **Integridade de compilação**: apenas binários compilados diretamente a partir do código-fonte deste repositório público (`kybiy/GameShelf`) serão submetidos para assinatura, livres de componentes proprietários ou software indesejado.

### 3. Responsáveis e papéis no repositório
Para atender aos requisitos de governança e aprovação de assinatura da SignPath Foundation, os papéis do projeto são definidos da seguinte forma:

- **Autores / Committers** *(responsáveis pelas alterações de código no repositório)*:
  - [`kybiy`](https://github.com/kybiy) (Proprietário do projeto e mantenedor principal)
  - Futuros colaboradores com acesso de gravação ou contribuições aceitas via Pull Request revisado.
- **Revisores (Reviewers)** *(responsáveis por revisar Pull Requests direcionados às branches protegidas, como `main`)*:
  - [`kybiy`](https://github.com/kybiy) (Proprietário do projeto)
  - Mantenedores de confiança designados com permissão de revisão em `kybiy/GameShelf`.
- **Aprovadores (Approvers)** *(responsáveis por autorizar manualmente cada solicitação de assinatura no SignPath)*:
  - [`kybiy`](https://github.com/kybiy) (Proprietário do projeto — aprovação manual obrigatória para cada release).

Todos os membros com papéis de Autor, Revisor ou Aprovador devem manter Autenticação Multifator (MFA) ativa tanto no GitHub quanto no SignPath.
