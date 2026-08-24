<p align="center">
  <a href="./README.md">English</a> ·
  <a href="./README.pt-BR.md"><strong>Português (Brasil)</strong></a>
</p>

<p align="center">
  <img src="./publishing/tiktok-chaos-curseforge-icon.png" alt="Ícone do TikTok Chaos" width="220">
</p>

<h1 align="center">TikTok Chaos</h1>

<p align="center">
  <strong>Transforme uma LIVE pública do TikTok em uma experiência configurável, visual e controlada dentro do Minecraft.</strong>
</p>

<p align="center">
  <a href="https://github.com/Pexe171/ModTikTok/actions/workflows/build.yml"><img alt="Build" src="https://github.com/Pexe171/ModTikTok/actions/workflows/build.yml/badge.svg"></a>
  <img alt="Minecraft 1.16.5 até 1.21.1" src="https://img.shields.io/badge/Minecraft-1.16.5%20%E2%86%92%201.21.1-62B47A?logo=minecraft">
  <img alt="Forge e NeoForge" src="https://img.shields.io/badge/loaders-Forge%20%7C%20NeoForge-EF6C35">
  <img alt="Java 8 até 21" src="https://img.shields.io/badge/Java-8%20%7C%2017%20%7C%2021-ED8B00?logo=openjdk">
  <img alt="Versão 1.4.0" src="https://img.shields.io/badge/versão-1.4.0-E83E8C">
  <a href="./LICENSE"><img alt="Licença MIT" src="https://img.shields.io/badge/licença-MIT-66F0C8"></a>
</p>

TikTok Chaos é um mod cliente para **Minecraft Java 1.16.5 até 1.21.1**. Há builds Forge para 1.16.5, 1.18.2, 1.19.2, 1.20.1 e 1.21.1; o NeoForge é compatível com 1.21.1. Ele se conecta a uma LIVE pública do TikTok pelo nome de usuário e transforma curtidas, presentes, comentários, follows, compartilhamentos e inscrições em regras executadas dentro de um mundo singleplayer.

Tudo é configurado no próprio Minecraft pelo painel aberto com `F8`. Não é necessário instalar programa auxiliar, informar senha do TikTok, usar chave de API, contratar serviço pago ou instalar o JEI.

> [!IMPORTANT]
> TikTok Chaos é um projeto comunitário independente. Ele não possui afiliação ou aprovação do TikTok, ByteDance, Mojang, Microsoft, Forge ou NeoForge. A conexão com a LIVE usa uma implementação comunitária não oficial e pode precisar de atualizações quando o TikTok alterar seu protocolo.

## Por que usar o TikTok Chaos?

- **Configuração visual:** escolha mobs, itens e efeitos em galerias pesquisáveis, sem precisar decorar IDs.
- **Compatível com modpacks:** itens, efeitos e mobs com ovo de spawn registrados por mods compatíveis aparecem automaticamente.
- **Modo aleatório:** escolha `Aleatório (todos os mods)` para sortear outro mob, item ou efeito em cada ativação.
- **Editor de regras completo:** combine condições, cooldowns, quantidades e várias ações sem editar JSON manualmente.
- **Preparado para tráfego de LIVE:** filas limitadas, controle de velocidade, proteção contra eventos duplicados e registries em cache.
- **Padrões seguros:** pausa global, emergência `F9`, mobs temporários, rollback e ações de mundo desligadas por padrão.
- **Presets e combos:** presets locais, quatro modos de combo, escala declarativa, roleta ponderada e sequências temporizadas.
- **Ferramentas para LIVE:** simulador detalhado, metas/ranking da sessão e overlay OBS local opcional.

## Como funciona

```text
LIVE pública do TikTok
        │
        ▼
Conector Webcast comunitário
        │
        ▼
Evento da LIVE normalizado
        │
        ▼
Motor de regras configurável ──► fila de prioridade limitada
        │
        ▼
Thread do servidor integrado do Minecraft
        │
        ▼
Mob, item, efeito, sequência, meta, clima ou ação visual
```

O conector recebe somente eventos de uma LIVE pública. Ele não entra na conta do TikTok, não publica comentários nem controla o perfil. O overlay OBS opcional abre apenas uma página HTTP somente leitura em `127.0.0.1`, protegida por um token aleatório da sessão.

## Editor visual de regras

No Minecraft 1.20.1 e 1.21.1, abra `F8` → **Regras** → **Editar** e selecione uma ação. Os ports 1.16.5–1.19.2 usam um painel compacto para conexão e simulação; as regras avançadas nessas versões continuam editáveis em `config/tiktok-chaos.json`.

| Ação | Seleção visual | Controles extras |
| --- | --- | --- |
| Invocar mob | Ícone do ovo de spawn, nome traduzido e ID | Quantidade e tempo de vida automático |
| Dar item | Modelo real do item, incluindo itens dos mods | Quantidade |
| Aplicar efeito | Ícone oficial, incluindo efeitos dos mods | Nível e duração |
| Teleporte curto | — | Raio |
| Clima temporário | — | Duração |
| Mensagem | — | Texto personalizado |
| Som, partículas e mensagem central | ID/Texto | Quantidade, duração e sequência |
| Canhão de presentes e fonte de curtidas | Item/— | Efeito apenas visual |
| Boss do espectador | Mob compatível | Limite separado de bosses |
| Caixa reversível | — | Confirmação dupla, limite e rollback |

Cada galeria possui:

- Pesquisa pelo nome traduzido, nome interno ou ID do registry.
- Pesquisa pelo namespace do mod, como `mekanism:`.
- Rolagem pelo mouse e detalhes ao passar o cursor.
- Entrada manual de `namespace:id` para usuários avançados.
- Um primeiro cartão chamado **Aleatório (todos os mods)**.

O catálogo é criado somente quando necessário e fica em cache. Apenas os cartões visíveis são desenhados a cada frame, evitando que modpacks grandes processem milhares de objetos repetidamente.

### Limites de compatibilidade

- **Itens:** todos os itens registrados, exceto o ar, podem aparecer. Itens que dependem de NBT/componentes especiais são entregues na forma registrada padrão.
- **Efeitos:** todos os efeitos registrados podem aparecer, inclusive efeitos adicionados por mods.
- **Mobs:** a entidade precisa possuir um ovo de spawn registrado. Isso exclui jogadores, projéteis, entidades técnicas e bosses vanilla sem ovo.

## Eventos da LIVE compatíveis

| Evento do TikTok | Dados disponíveis na regra |
| --- | --- |
| Curtidas | Meta acumulada |
| Presentes | ID do presente, faixa de diamantes/valor e repetição |
| Comentários | Comando explicitamente permitido, como `!zumbi` |
| Follow | Identidade do espectador |
| Compartilhamento | Identidade do espectador |
| Inscrição | Identidade do espectador |
| Entrada | Identidade do espectador |
| Estatísticas da sala | Quantidade de espectadores |
| Início/fim da LIVE | Estado da transmissão |

A quantidade recebida no presente repete o conjunto inteiro de ações configuradas. Por exemplo: se uma rosa invoca um zumbi, um evento do TikTok com `amount = 3` coloca três ações de zumbi na fila (respeitando `maxTriggersPerEvent`).

## Regras iniciais incluídas

| Interação | Ação padrão no Minecraft |
| --- | --- |
| A cada 100 curtidas acumuladas | Invocar 1 zumbi temporário |
| Follow | Dar 4 pães |
| Compartilhamento | Invocar 1 esqueleto temporário |
| Inscrição | Dar maçã dourada e regeneração |
| Comentário `!zumbi` | Invocar 1 zumbi temporário |
| Comentário `!item` | Dar item aleatório do conjunto inicial conservador |
| Comentário `!sorte` | Aplicar efeito positivo aleatório inicial |
| Comentário `!azar` | Aplicar efeito negativo aleatório inicial |
| Presente com valor 1–9 | Invocar 1 zumbi temporário |
| Presente com valor 10–99 | Invocar 2 esqueletos temporários |
| Presente com valor 100–999 | Invocar 4 zumbis, lentidão e raio visual |
| Presente com valor 1000+ | Invocar 1 devastador, cegueira e raio visual |

Essas regras são exemplos editáveis, não comportamentos fixos. Elas podem ser pausadas, alteradas, excluídas ou substituídas pelo editor do jogo.

## Instalação

### Requisitos

| Minecraft | Loader | Versão do loader | Java |
| --- | --- | --- | --- |
| `1.16.5` | Forge | `36.2.34+`, abaixo da `37` | 8 |
| `1.18.2` | Forge | `40.3.0+`, abaixo da `41` | 17 |
| `1.19.2` | Forge | `43.5.0+`, abaixo da `44` | 17 |
| `1.20.1` | Forge | `47.4.10+`, abaixo da `48` | 17 |
| `1.21.1` | Forge | `52.1.0+`, abaixo da `53` | 21 |
| `1.21.1` | NeoForge | `21.1.133+`, na linha `21.1.x` | 21 |

Todas as builds são client-side, funcionam no servidor integrado singleplayer e não exigem mod auxiliar.

### Passos

1. Baixe `tiktok-chaos-1.4.0+mc<versão>-<loader>.jar`, escolhendo exatamente a versão do Minecraft e o loader da sua instância.
2. Coloque o JAR na pasta `mods` da instância.
3. Abra o Minecraft e entre em um mundo singleplayer.
4. Pressione `F8`.
5. Digite o usuário da pessoa que está em LIVE, sem `@`.
6. Clique em **Conectar**.

A pessoa precisa estar transmitindo naquele momento e a LIVE precisa ser pública. Transmissões privadas, com restrição de idade, região ou convidados podem não estar acessíveis.

## Painel do mod

A tela `F8` completa das versões 1.20.1 e 1.21.1 possui:

- **Conexão:** usuário, estado da conexão e reconexão automática.
- **Regras:** criação, edição, pausa e combinação de ações.
- **Histórico:** últimos eventos recebidos e ações executadas.
- **Segurança:** velocidade das ações, limite de mobs e tempo de vida.
- **Simulador:** teste rápido ou detalhado com usuário, presente, moedas, quantidade, curtidas e comentário.
- **Presets:** prévia e aplicação por substituição ou mesclagem, sempre com backup automático.
- **Sessão:** metas, ranking, privacidade de nomes, avatares temporários e overlay OBS local.

O HUD compacto mostra conexão, estado `ATIVO/PAUSADO`, último evento, fila e mobs. Metas, ranking e chat são opcionais.

## Modelo de segurança

- Comentários dos espectadores são tratados como dados e nunca executados como comandos do Minecraft, PowerShell, shell ou sistema operacional.
- Somente gatilhos de comentário configurados explicitamente são comparados.
- Mobs criados possuem limite global e tempo de vida configuráveis.
- As ações passam por uma fila limitada, priorizada e com controle de velocidade.
- `F9` pausa tudo, cancela sequências pendentes, remove entidades temporárias e restaura efeitos, clima e blocos controlados pelo mod.
- A proteção adaptativa reduz o ritmo quando detecta ticks lentos.
- Eventos repetidos ou duplicados são filtrados.
- Raios criados pelo mod são apenas visuais: não causam dano e não criam fogo.
- O mod não altera gamerules nem limpa inventários. A única ação de blocos incluída é reversível, começa desligada, exige confirmação dupla, ignora block entities e respeita limite/tempo de restauração.
- Avatares começam desligados, aceitam somente HTTPS de hosts permitidos, têm limites de arquivo/dimensão e cache apagado ao fim da sessão.
- O overlay OBS é somente leitura, escuta apenas em `127.0.0.1` e usa URL com token da sessão.
- Os pools aleatórios são montados uma vez, mas todos os limites continuam valendo para cada resultado.

Conteúdo aleatório de todos os mods pode incluir itens, efeitos ou criaturas propositalmente caóticos fornecidos pelo jogo ou por outro mod. Faça backup de mundos importantes antes de usar regras aleatórias muito amplas.

## Configuração

As configurações e regras ficam em:

```text
<instância-do-minecraft>/config/tiktok-chaos.json
```

O arquivo é salvo localmente. O histórico dos eventos da LIVE permanece somente na memória durante a sessão atual do Minecraft.

Presets importados/exportados ficam em `config/tiktok-chaos/presets/`. As dez versões anteriores da configuração são mantidas em `config/tiktok-chaos.json.backups/`.

## Solução de problemas

1. Confira se o usuário está correto e sem `@`.
2. Confirme que a pessoa está ao vivo e a transmissão é pública.
3. Use **Desconectar** e **Conectar** novamente ou aguarde a reconexão automática.
4. Use a aba **Simulador** para separar problemas das regras do Minecraft de problemas da conexão com o TikTok.
5. Procure por `TikTok Chaos` em `logs/latest.log`.

Transmissões muito grandes podem agrupar ou omitir eventos individuais de curtida. Restrições de rede, região ou mudanças no protocolo do TikTok também podem impedir a conexão.

## Compilar o código-fonte

Clone o repositório e use o JDK 21 para compilar os alvos 1.21.1:

```powershell
git clone https://github.com/Pexe171/ModTikTok.git
cd ModTikTok
$env:JAVA_HOME='C:\caminho\para\jdk-21'
.\gradlew.bat test shadowJar :forge:test :forge:shadowJar
```

Linux/macOS:

```bash
./gradlew test shadowJar :forge:test :forge:shadowJar
```

O distribuível NeoForge será criado em `build/libs/`; o Forge ficará em `forge/build/libs/`. Use os JARs sem o classificador `thin`.

As builds Forge legadas usam um wrapper Gradle 8.8 separado e precisam dos toolchains JDK 8 e JDK 17 instalados:

```powershell
.\ports\forge-legacy\gradlew.bat -p ports\forge-legacy `
  :forge-1.16.5:test :forge-1.16.5:verifyJava8Bytecode `
  :forge-1.18.2:test :forge-1.18.2:shadowJar `
  :forge-1.19.2:test :forge-1.19.2:shadowJar `
  :forge-1.20.1:test :forge-1.20.1:shadowJar
```

Os JARs distribuíveis são criados em `ports/forge-legacy/forge-<versão-do-minecraft>/build/libs/`.

## Documentos do projeto

- [English](./README.md)
- [Histórico de alterações](./CHANGELOG.md)
- [Como contribuir](./CONTRIBUTING.md)
- [Suporte e relato de erros](./SUPPORT.md)
- [Política de segurança](./SECURITY.md)
- [Publicação no CurseForge](./PUBLISHING.md)
- [Avisos de terceiros](./src/main/resources/META-INF/THIRD_PARTY_NOTICES.txt)

## Créditos

**Idealizado e desenvolvido por [Pexe171](https://github.com/Pexe171).**

TikTok Chaos incorpora o conector comunitário [TikTokLiveJava](https://github.com/jwdeveloper/TikTok-Live-Java) e os componentes necessários em tempo de execução. A build 1.16.5 usa o [JvmDowngrader](https://github.com/unimined/JvmDowngrader) para continuar compatível com Java 8. As atribuições completas estão nos avisos de terceiros presentes no código-fonte e dentro do JAR.

Distribuído sob a [Licença MIT](./LICENSE).
