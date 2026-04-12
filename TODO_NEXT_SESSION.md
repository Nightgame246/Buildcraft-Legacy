# TODO Next Session

## Phase B — Item Pipes Speziallogik (ABGESCHLOSSEN)
Alle Item Pipes haben funktionale Kernmechanik. Siehe Archiv unten.

## Phase C — Energy/Fluid Pipes
- [ ] Kinesis Rendering originalgetreuer machen (pro Section eigener Power-Level + Flow-Offset statt globalem Blockstate)
- [ ] Energy Losses: lossRate in KinesisPipeBE feinjustieren
- [ ] Fluid Pipes ingame nachtesten (nach Phase-D-Fixes, da Engine/Tank als Testinfrastruktur nötig)

### Fehlende Fluid-Pipe-Materialien
#### Einfach (nur BCPipes-Eintrag + Textur)
- [ ] Sandstone Fluid Pipe — Nur Pipe-zu-Pipe (Logik existiert in FluidPipeBlock)
- [ ] Quartz Fluid Pipe — Wie Stone, Trennungsregeln existieren

#### Klein (einfache BE-Erweiterung)
- [ ] Void Fluid Pipe — Zerstört Fluid

#### Mittel (eigene Block/BE-Klasse)
- [ ] Iron Fluid Pipe — Gerichteter Output
- [ ] Clay Fluid Pipe — Nearest-First Routing

#### Groß (GUI + Speziallogik)
- [ ] Diamond Fluid Pipe — Fluid-Filter pro Seite mit GUI

## Phase D — Energy & Factory (Bugs)
- [ ] Redstone Engine funktioniert nicht (User-Report)
- [ ] Tank-Verhalten prüfen (User vermutet Bugs)
- [ ] Engine Animation: Alle Motoren flüssig stampfend wenn aktiv?

## Phase E — Silicon (kommt später)
- [ ] Emzuli Pipe — Gate-gesteuertes Emerald, braucht Gates/Triggers

## Polish — Bekannte GUI/Interaktionsprobleme
- [ ] Daizuli Pipe: Wrench-Interaktion funktioniert nicht (Target setzen/Farbe wechseln) — Code ist korrekt, useItemOn wird vermutlich nicht aufgerufen, Root Cause unklar
- [ ] Emerald Pipe: GUI-Textur ist 161px hoch, Standard-MC braucht 166px — Textur erweitern oder Layout anpassen
- [ ] Emerald Pipe: Toggle-Button Style (aktuell MC-Button, könnte originalgetreuere Textur-Sprites nutzen)

## Future — Pipe Erweiterungen
- [ ] Diamond Pipe: Round-Robin / proportionaler Split (braucht Pipe-Base Multi-Output-Support)
- [ ] Obsidian Pipe: Energy-Gating (Engine-Power bestimmt Suction-Range, analog Original MJ-System)
- [ ] Daizuli Pipe: Dedizierte Blocked-Textur fuer Target-Direction
- [ ] Gate-Integration fuer Lapis/Daizuli (getActiveColor()), Emerald (getFilterMode())

## Aufräumen
- [ ] 8 unbenutzte `pipe_energy_*.json` in `src/main/resources/assets/buildcraft/models/block/` löschen

## Hinweise
- Bei Pipe-Textur-Änderungen in BCPipes.java: Config in `run/config/buildcraft/pipes/` löschen
- Nach Modell-Änderungen: `./gradlew runData` ausführen
- Immer auf `main` committen/pushen, nie Feature-Branches oder ignorierte Dateien

## Erledigt (Archiv)
- [x] Stripe Pipe Stack-Fix & Bounce-Back
- [x] Energie-Upgrade für Wooden/Emerald Item Pipes
- [x] Implementierung & Registrierung aller Kinesis-Rohre
- [x] Datagen für Kinesis-Rohre
- [x] Engine-Fix: BCConfig Defaults für Produktion (waren 0!)
- [x] KinesisPipeBERenderer: TESR entityCutout + UV-Scroll, Full Brightness, Lerp-Smoothing, sqrt-Radius
- [x] POWER_LEVEL Blockstate-Property mit Lichtemission
- [x] Kinesis Pipe: extractEnergy() entfernt (passiv wie Original BC)
- [x] Fluid Pipes Basis: FluidPipeBE, ExtractingFluidPipeBE, Blocks, Renderer, Payload, Registration
- [x] Fluid Pipes: 4 Materialien (Wooden, Cobblestone, Stone, Gold)
- [x] Fluid Pipes: copyWithAmount(0) Bug-Fix
- [x] Fluid Pipes: Proportionale Extraktion (mB = FE × fluidExtractionRate)
- [x] Fluid Pipes: Separate BlockEntityTypes (FLUID_PIPE + EXTRACTING_FLUID_PIPE)
- [x] ItemPipeBlock: isItemPipe() filtert Fluid/Kinesis aus
- [x] Original BC Texturen für Fluid + Kinesis Pipes übernommen
- [x] Iron Pipe Rendering-Fix (Output-Ring)
- [x] Diamond Pipe Sortier-Logik + GUI
- [x] Kinesis Pipe Rendering ingame getestet (funktional OK, originalgetreues Rendering noch offen in Phase C)
- [x] Gold Pipe: Eigene BE-Klasse mit originalgetreuer Beschleunigung (SPEED_TARGET=0.25, SPEED_DELTA=0.07)
- [x] Obsidian Pipe: Single-Face Suction Enforcement (getOpenFace())
- [x] Daizuli Pipe: Directional Color Routing mit Wrench-on-Face + Gate-Hook (getActiveColor())
- [x] Emerald Pipe: Whitelist/Blacklist Toggle-GUI + ToggleFilterModePayload
- [x] Diamond Pipe: Registrierung korrekt (nicht EXTRACTING), Split als Future-TODO
- [x] Iron Pipe: Wrench-Rotation + Output-Blocking (bereits fertig)
- [x] Lapis Pipe: Farb-System funktional (Gate-Integration Phase E)
