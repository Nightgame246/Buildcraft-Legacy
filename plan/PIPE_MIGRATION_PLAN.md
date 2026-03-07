# Migrationsplan: Buildcraft Rohrsystem

Dieser Plan dokumentiert die schrittweise Portierung aller Buildcraft-Rohre von 1.12.2 auf NeoForge 1.21.1.

## Phase A: Einfache Item-Rohre (In Arbeit)
**Status:** Gestartet (Tool: Qwen-Agent)
**Ziel:** Alle "Standard"-Rohre ohne spezialisierte Logik hinzufügen.
- [ ] **Stein-Rohr**: Wie Pflasterstein, verbindet sich aber nicht damit.
- [ ] **Sandstein-Rohr**: Verbindet sich nicht mit Maschinen/Inventaren.
- [ ] **Quarz-Rohr**: Geringer Widerstand.
- [ ] **Void-Rohr**: Löscht Items (oder Flüssigkeiten) beim Eintreten.

## Phase B: Spezialisierte Item-Rohre (Geplant)
**Status:** Ausstehend (Tool: Claude Code)
**Ziel:** Rohre mit aktiver Logik oder GUI-Interaktion.
- [ ] **Eisen-Rohr**: Einbahnstraße (mit Hammer/Wrench rotierbar).
- [ ] **Obsidian-Rohr**: Saugt Items vom Boden auf.
- [ ] **Smaragd-Rohr**: Fortschrittliches Extrahieren mit Filtern.
- [ ] **Ton-Rohr**: Priorisiert benachbarte Inventare.
- [ ] **Lapis/Daizuli**: Farb-basiertes Routing.
- [ ] **Streifen-Rohr**: Welt-Interaktion (Blöcke setzen/brechen).

## Phase C: Flüssigkeits- & Energie-Systeme (Geplant)
**Status:** Ausstehend (Tool: Claude Code + Qwen-Agent)
**Ziel:** Komplette Portierung der Kinesis- (Energie) und Fluid-Rohre.
- [ ] **Fluid-Architektur**: Basis-Klassen für Fluid-Rohre (NeoForge Fluid-Cap).
- [ ] **Kinesis-Architektur**: MJ-Transport (Minecraft Joules) via NeoForge Energy-Cap.
- [ ] **Varianten**: Holz, Stein, Gold, Diamant für beide Systeme.

## Phase D: Logik & Automatisierung (Geplant)
**Status:** Ausstehend (Tool: Claude Code)
- [ ] **Struktur-Rohre**: Nur für Gates und Wires.
- [ ] **Gates/Trigger/Actions**: Das Herzstück der Buildcraft-Automatisierung.

---
*Hinweis: Dieser Plan wurde von Gemini (Orchestrator) erstellt. Für jedes Subsystem sollte ein detaillierter Migrationsplan angefordert werden.*
