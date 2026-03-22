# 🛡️ AntiVPN

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Spigot](https://img.shields.io/badge/spigot-1.8~1.21-orange)
![Java](https://img.shields.io/badge/java-8-red)
![License](https://img.shields.io/badge/license-MIT-green)

Spigot/Bukkit 1.8~1.21 plugin that blocks VPN, Proxy and Tor before connecting. SQLite cache, nick/IP whitelist and country restriction. | Plugin que bloqueia VPN, Proxy e Tor antes de conectar. Cache SQLite, whitelist por nick/IP e restrição por país.

---

## 📦 Instalação

1. Baixe o `AntiVPN.jar` em [Releases](../../releases)
2. Coloque na pasta `plugins/` do seu servidor
3. Reinicie o servidor
4. Configure sua chave da API em `plugins/AntiVPN/config.yml`

---

## 🔑 API

O plugin usa o **[IPHub.info](https://iphub.info)** para detectar VPNs.

- Sem chave: **1.000 verificações/dia** (grátis)
- Com chave grátis: limite maior

Crie sua chave em: https://iphub.info

---

## ⚙️ Configuração

```yaml
api-provider: iphub
iphub-key: "SUA_CHAVE_AQUI"
iphub-block-level: 1   # 1 = VPN/Proxy | 2 = VPN/Proxy + suspeitos
brazil-only: false      # true = bloqueia IPs fora do Brasil
cache-duration-hours: 24
block-on-api-error: false
notify-admins: true
```

---

## 🕹️ Comandos

| Comando | Descrição | Permissão |
|---|---|---|
| `/antivpn reload` | Recarrega o config | `antivpn.admin` |
| `/antivpn cache` | Ver tamanho do cache | `antivpn.admin` |
| `/antivpn cache clear` | Limpa o cache | `antivpn.admin` |
| `/antivpn check <jogador>` | Verifica um jogador online | `antivpn.admin` |
| `/antivpn whitelist ip add <ip>` | Adiciona IP na whitelist | `antivpn.admin` |
| `/antivpn whitelist ip remove <ip>` | Remove IP da whitelist | `antivpn.admin` |
| `/antivpn whitelist nick add <nick>` | Adiciona nick na whitelist | `antivpn.admin` |
| `/antivpn whitelist nick remove <nick>` | Remove nick da whitelist | `antivpn.admin` |

---

## 🔐 Permissões

| Permissão | Descrição | Padrão |
|---|---|---|
| `antivpn.admin` | Acesso a todos os comandos | OP |
| `antivpn.bypass` | Entra mesmo com VPN ativa | OP |
| `antivpn.notify` | Recebe alertas de bloqueio no chat | OP |

---

## ✅ Funcionalidades

- 🚫 Bloqueia VPN, Proxy e Tor **antes** de entrar no servidor
- 🗄️ Cache em **SQLite** — persistente, não corrompe se o servidor cair
- 👤 Whitelist por **nick** e por **IP**
- 🌎 Restrição por **país** (ex: só Brasil)
- 🧠 Detecção de ISP residencial para evitar falsos positivos
- ⚡ Verificação assíncrona — zero lag no servidor
- 🔔 Notificação para admins quando alguém for bloqueado
- 🔌 Suporte a dois provedores: **IPHub** e **ProxyCheck.io**
- 🪶 Sem dependências pesadas — apenas Spigot API + SQLite JDBC

---

## 📊 Como funciona

```
Jogador tenta conectar
        ↓
Nick está na whitelist?  → SIM → Entra ✅
        ↓ NÃO
IP está na whitelist?    → SIM → Entra ✅
        ↓ NÃO
IP está no cache?        → SIM → É VPN? → SIM → Bloqueado ❌
        ↓ NÃO                           → NÃO → Entra ✅
Consulta a API
        ↓
É VPN/Proxy?             → SIM → Salva no cache + Bloqueado ❌
        ↓ NÃO
É do Brasil? (se ativo)  → NÃO → Bloqueado ❌
        ↓ SIM
Entra ✅
```

---

## 📌 Versão

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| API | Spigot/Bukkit 1.8 ~ 1.21 |
| Java | 8 |

---

## 👨‍💻 Autor

- Site: [obitouchiha.cloud](https://obitouchiha.cloud/)
- GitHub: [@eojoaodev](https://github.com/eojoaodev)

---

## 📄 Licença

MIT License — veja [LICENSE](LICENSE) para detalhes.
