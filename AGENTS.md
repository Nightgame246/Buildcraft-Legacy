# Repository Guidelines for AI Agents

## AI-Team

- **Claude Code** (`claude`) — Hauptentwickler. Architektur, komplexe Logik, Bug-Fixing, Planung, Code-Reviews.
- **Gemini CLI** (`gemini`) — Optionales Analyse-Tool. 1M-Token-Kontext für große Dateien, Asset-Vergleiche.
- **OpenCode** + **Ollama** (Qwen2.5-Coder 14B, lokal) — Fleißarbeit, Boilerplate, Routine-Portierungen. Tool-Calling fähig.

Der Entwickler (Fabi) koordiniert direkt — kein AI-Orchestrator.

## Wichtige Projekt-Regeln

- **Originalgetreu**: Der Port soll dem Original BuildCraft 1.12 so nah wie möglich kommen. Immer den Original-Code als Referenz nutzen (`/run/media/fabi/SSD/codeing/BuildCraft-1.12/`).
- **Git**: Immer auf `main` committen/pushen. Nie Feature-Branches zum Remote. Nie `.gitignore`-Dateien force-adden.
- **Sprache**: Antworten auf Deutsch.

## Project Structure

- `src/main/java/com/thepigcat/buildcraft` — Root-Package
  - `api/` — Abstrakte Basisklassen (Pipes, Engines, Blocks)
  - `content/` — Blocks, Block Entities, Items, Menus
  - `registries/` — DeferredRegister-Klassen (BC-Prefix)
  - `client/` — Renderer, Screens, Models
  - `networking/` — Payloads für Client/Server-Sync
  - `datagen/` — Daten-Generatoren
  - `mixins/` — Mixin-Patches
- `src/main/resources` — Assets und Daten
- `src/generated/resources` — Von `./gradlew runData` generiert, nicht manuell bearbeiten
- `TODO_NEXT_SESSION.md` — Aktuelle Aufgaben, nach Phasen sortiert

## Build Commands

- `./gradlew build` — Kompilieren und JAR bauen
- `./gradlew runClient` — Minecraft mit Mod starten (Testen)
- `./gradlew runServer` — Dedicated Server starten
- `./gradlew runData` — Daten neu generieren (nach Datagen-Änderungen)
- `./gradlew clean` — Build-Artefakte löschen

## Coding Style

- Java 21, 4-Space Indentation, UTF-8
- Packages: lowercase, Klassen: PascalCase, Methoden/Felder: camelCase, Konstanten: UPPER_SNAKE_CASE
- Registry-Klassen: `BC*`-Prefix (BCBlocks, BCItems, etc.)
- Bestehenden Stil in der Umgebung beibehalten

## Commits

- Kurze, imperative Commit-Messages (z.B. `fix: redstone engine power output`)
- Commits fokussiert halten — Refactors, Verhaltensänderungen und generierte Daten trennen
- `src/generated/resources` nach Datagen-Änderungen mit committen
