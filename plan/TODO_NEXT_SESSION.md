# Notizen für die nächste Session

## Kinesis Renderer Testen (Priorität 1)
- [ ] **In-Game testen**: KinesisPipeBERenderer mit den neuen Verbesserungen evaluieren:
  - Full Brightness (LightTexture.FULL_BRIGHT)
  - Client-seitige Smoothing (displayPower Lerp)
  - Kontinuierlicher sqrt-Radius statt diskreter Stufen
  - Richtungsabhängiger UV-Scroll
- [ ] **Falls nicht smooth genug**: Scroll-Speed anpassen (SCROLL_SPEED in Renderer), Textur glätten (Gradient statt harte Diagonalstreifen), oder power_flow.png ersetzen
- [ ] **Aufräumen**: 8 unbenutzte `pipe_energy_*.json` Dateien in `src/main/resources/assets/buildcraft/models/block/` können gelöscht werden (von alter Baked-Model-Approach)

## Phase C: Weiteres Polishing
- [ ] **Engine Animation**: Sicherstellen, dass alle Motoren (Redstone, Stirling, Combustion) flüssig stampfen, wenn sie aktiv sind.
- [ ] **Energy Losses**: Feinjustierung der `lossRate` in `KinesisPipeBE`.

## Phase C: Start Fluid Pipes
- [ ] **FluidPipeBE**: Erstellen der Basis-Klasse für Flüssigkeitstransport.
- [ ] **Fluid Capability**: Registrierung der `IFluidHandler` für alle Fluid-Rohrvarianten.
- [ ] **Fluid Extraction**: Das hölzerne Flüssigkeitsrohr auf Energie-basierte Extraktion umstellen (analog zum Item-Rohr).
- [ ] **Rendering**: Architektur für die Anzeige von Flüssigkeiten innerhalb der Rohre planen (Dynamic Rendering/Baked Models).

## Archiv (Erledigt)
- [x] Stripe Pipe Stack-Fix & Bounce-Back.
- [x] Energie-Upgrade für Wooden/Emerald Item Pipes.
- [x] Implementierung & Registrierung aller Kinesis-Rohre.
- [x] Datagen für Kinesis-Rohre abgeschlossen.
- [x] Engine-Fix: BCConfig Defaults für Produktion (waren 0!), maxTransfer auf capacity.
- [x] KinesisPipeBERenderer: Mehrere Iterationen (Overlay → Baked → TESR entityCutout + UV-Scroll).
- [x] POWER_LEVEL Blockstate-Property mit Lichtemission.
