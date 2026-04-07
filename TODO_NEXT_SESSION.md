# TODO Next Session

## Phase B — Item Pipes Speziallogik (unabhängig)
### Quick Fixes
- [ ] Diamond Pipe — Aktuell fälschlicherweise als EXTRACTING registriert
- [ ] Iron Pipe — Wrench-on-Face statt rotate (~66 Zeilen Original)
- [ ] Gold Pipe — Redstone-gesteuerte Beschleunigung 2x-8x (~32 Zeilen Original)

### Mittel
- [ ] Lapis Pipe — Farb-System (16 Farben, Rechtsklick-Cycling, ~115 Zeilen Original)
- [ ] Daizuli Pipe — Directional + Colour Routing, abhängig von Lapis (~133 Zeilen Original)
- [ ] Emerald Pipe — Filter-GUI für Whitelist/Blacklist (~233 Zeilen Original)

### Groß
- [ ] Obsidian Pipe — Single-Open-Face, Entity-Pickup, Drop-Cooldown (~231 Zeilen Original)

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
