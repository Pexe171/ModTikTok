# Contributing / Como contribuir

Thank you for helping improve TikTok Chaos. Contributions should keep the mod understandable, safe for LIVE use and compatible with Minecraft `1.21.1` + NeoForge `21.1.x`.

Obrigado por ajudar a melhorar o TikTok Chaos. As contribuições devem manter o mod compreensível, seguro para uso em LIVE e compatível com Minecraft `1.21.1` + NeoForge `21.1.x`.

## Development setup

1. Install JDK 21.
2. Fork and clone the repository.
3. Create a focused branch from `main`.
4. Run the complete validation before opening a pull request:

```bash
./gradlew test shadowJar
```

Windows PowerShell:

```powershell
.\gradlew.bat test shadowJar
```

The distributable JAR is created in `build/libs/` and must not be committed.

## Project expectations

- Keep LIVE comments as untrusted data; never execute them as commands.
- Keep expensive registry or network work away from the render loop and server tick hot paths.
- Preserve action-rate limits, queue bounds and mob cleanup behavior.
- Prefer localized, visual configuration over requiring users to memorize IDs.
- Add or update tests for rule, queue, configuration and event behavior.
- Update both `README.md` and `README.pt-BR.md` when user-facing behavior changes.
- Do not commit tokens, cookies, passwords, logs, crash reports, world saves or IDE files.

## Relatórios e pull requests

- Explique o problema e o resultado esperado.
- Mantenha cada pull request focado em uma mudança coerente.
- Informe como a alteração foi testada.
- Para erros do jogo, anexe `latest.log` ou o crash report, removendo dados pessoais e segredos.
- Capturas de tela ajudam com problemas visuais, mas não substituem os logs em crashes.

By contributing, you agree that your contribution is distributed under the project's [MIT License](./LICENSE).
