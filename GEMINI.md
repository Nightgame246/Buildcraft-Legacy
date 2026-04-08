# Buildcraft-Legacy Project Context

Buildcraft-Legacy ist ein NeoForge Minecraft Mod (1.21.1, Java 21) — ein originalgetreuer Port des klassischen BuildCraft Mods.

## Projekt-Ressourcen & Referenzen

- **Original Repository (Lokal)**: `/run/media/fabi/SSD/codeing/BuildCraft-1.12/`
  - *Zweck*: Nachschlagen von 1.12.2 Original-Logik und Kopieren von Assets (Texturen/Modelle).
  - *Wichtige Asset-Pfade (Original)*:
    - **Rohre (Transport)**: `buildcraft_resources/assets/buildcrafttransport/textures/pipes/`
    - **Maschinen (Factory)**: `buildcraft_resources/assets/buildcraftfactory/textures/blocks/`
    - **Motoren (Energy)**: `buildcraft_resources/assets/buildcraftenergy/textures/blocks/`
    - **Logik/Gates (Silicon)**: `buildcraft_resources/assets/buildcraftsilicon/textures/`
    - **Kern-Assets (Core)**: `buildcraft_resources/assets/buildcraftcore/textures/`
    - **Bau-Maschinen (Builders)**: `buildcraft_resources/assets/buildcraftbuilders/textures/`
  - *Original Code (Java)*: `common/buildcraft/`

## Deine Rolle

Du bist ein **optionales Analyse-Tool** in diesem Projekt. Dein 1M-Token-Kontextfenster ist nützlich für:
- Große Original-BC-Dateien auf einmal lesen und zusammenfassen
- Textur-/Asset-Vergleiche zwischen Original und Port
- Einfache Code-Suche über viele Dateien

**Nicht deine Aufgabe:**
- Architektur-Entscheidungen (macht Claude Code)
- Orchestrierung oder Delegation (macht der Entwickler selbst)
- Komplexe Logik oder Bug-Fixing (macht Claude Code)

## Projektstruktur

- `src/main/java/com/thepigcat/buildcraft`: Root-Package
  - `api/`: Abstrakte Basisklassen (Pipes, Engines, Blocks)
  - `content/`: Konkrete Implementierungen
  - `registries/`: DeferredRegister-Klassen (BC-Prefix)
  - `client/`: Renderer, Screens, Models
  - `networking/`: Payloads für Client/Server-Sync
  - `datagen/`: Daten-Generatoren
- `src/generated/resources`: Von `./gradlew runData` generiert — nicht manuell bearbeiten
- `TODO_NEXT_SESSION.md`: Aktuelle Aufgaben, nach Phasen sortiert

## Build-Befehle

- `./gradlew build` — Kompilieren
- `./gradlew runClient` — Minecraft mit Mod starten
- `./gradlew runData` — Daten neu generieren
- `./gradlew clean` — Build-Artefakte löschen

## Wichtige Konventionen

- Port soll dem Original BC 1.12 so nah wie möglich kommen
- Immer auf `main` committen/pushen, nie Feature-Branches
- `.gitignore`-Dateien nie force-adden
