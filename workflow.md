# Workflow: Buildcraft-Legacy portieren

## Welches Tool wofür?

| Tool | Befehl | Rolle |
|------|--------|-------|
| **Gemini CLI** | `gemini` | **Chef-Stratege & Orchestrator**: Analyse, Migrationspläne, Tool-Empfehlungen |
| **Claude Code** | `claude` | **Architekt**: Komplexe Logik, Capabilities, schwierige Bugs, API-Design |
| **Qwen3-Coder Agent** | `./qwen-agent.sh` | **Entwickler**: Boilerplate, Portieren von Standard-Klassen, Routine-Aufgaben |

---

## Der "Gemini-Zuerst" Ansatz (Orchestrierung)

Wenn du eine neue Aufgabe hast oder nicht weißt, wie du ein Problem angehen sollst, frage immer zuerst **Gemini**. Da Gemini den gesamten Kontext sieht, kann es entscheiden, welches Tool am effizientesten ist.

**Beispiel-Anfrage an Gemini:**
> "Ich möchte die *Auto-Workbench* portieren. Hier ist die Original-Klasse: [Link]. Erstelle einen Plan und sag mir, welche Teile ich mit Claude und welche mit Qwen umsetzen soll."

---

## Standard-Ablauf für ein neues Feature / Subsystem portieren

### Schritt 0 — Gemini: Strategie & Tool-Wahl
Gemini analysiert das Ziel und teilt die Arbeit auf:
- **Strategie**: Was sind die Abhängigkeiten?
- **Tool-Empfehlung**: "Nimm Claude für die TileEntity-Logik (komplex) und Qwen für die Block-Klasse und die JSON-Ressourcen (Routine)."

### Schritt 1 — Gemini: Migrationsplan erstellen
Gemini erstellt den detaillierten Plan (siehe `GEMINI.md`).

---

### Schritt 2 — Umsetzung (Execution)

#### A: Mit Claude Code (für die "harten" Brocken)
- Komplizierte Mathe-Logik (z.B. Laser, Quarry-Pfadfindung).
- Neue NeoForge-Systeme (Data Components, Networking).
- Debugging von Kompilierfehlern.

#### B: Mit Qwen3-Coder Agent (für das "Tagesgeschäft")
- Erstellen von Standard-Blöcken/Items.
- Ausfüllen von Datagen-Providern (Rezepte, Loot-Tables).
- Einfaches Umschreiben von 1.12 zu 1.21 Code.

---

### Schritt 3 — Verifikation & Sync
Nach der Arbeit eines Agenten:
1. `./gradlew build`
2. Bei Fehlern: Fehler an Claude (für Architektur) oder Qwen (für Syntax).
3. **WICHTIG**: Wenn sich die Architektur ändert, bitte einen Agenten (oder Gemini), die `CLAUDE.md` oder `GEMINI.md` zu aktualisieren, damit das restliche Team bescheid weiß.

---

## Faustregeln
- **Unklarheit?** → Gemini fragen (Orchestrierung).
- **Logik-Fehler?** → Claude.
- **Viel Schreibarbeit?** → Qwen-Agent.
- **Kontext-Verlust?** → Gemini bitten, die Projektdateien (`.md`) zu synchronisieren.
