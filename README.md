# ChunkClaim

Minecraft-Plugin (Paper, 26.3): Chunks mit Dias aus dem Inventar claimen, Schutz gegen Fremde,
Einstellungen und Zugriffsrechte pro Chunk, Admin-Rechte.

## Installation
1. Repository auf GitHub hochladen → Tab **Actions** → Lauf **Build** → unten bei *Artifacts* die `ChunkClaim` herunterladen (ZIP, darin die `ChunkClaim.jar`).
2. `ChunkClaim.jar` in den `plugins`-Ordner des Servers legen, Server neu starten.
3. Einstellungen: `plugins/ChunkClaim/config.yml`, danach im Spiel `/chunk admin reload`.

## Befehle
| Befehl | Was er macht |
| --- | --- |
| `/chunk claim` | Chunk claimen, auf dem du stehst (Dias aus dem Inventar) |
| `/chunk unclaim` | Chunk freigeben, die Hälfte des Preises kommt zurück |
| `/chunk info` | Besitzer, Einstellungen, Zugriff |
| `/chunk list` | deine Chunks |
| `/chunk set [break\|place\|interact] [enable\|disable] [all]` | was Fremde dürfen |
| `/chunk trust [Spieler] [all]` / `/chunk untrust [Spieler] [all]` | Zugriff geben / entziehen |
| `/chunk admin unclaim` | Chunk ohne Rückzahlung löschen |
| `/chunk admin bypass [enable\|disable]` | Schutz umgehen |
| `/chunk admin reload` | config.yml neu laden |

## Preise (Standard)
Chunk 1–4: 32 Dias · Chunk 5–8: 64 Dias · ab Chunk 9: 128 Dias · maximal 16 Chunks.
Alles in `config.yml` änderbar.

## Noch nicht dabei
Betreten sperren, Schutz vor Tieren/Fahrzeugen/Kolben, Webkarte, Dia-Konto.
