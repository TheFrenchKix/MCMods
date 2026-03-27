# Anti-AFK Mod

Mod Minecraft Fabric côté client simulant des mouvements réalistes pour prévenir l'expulsion des joueurs inactifs.

## Caractéristiques

- **Mouvements intelligents** : Machine à états qui simule un comportement humain (mouvements → micro-ajustements → pauses)
- **Détection d'obstacles** : Analyse les blocs autour du joueur pour éviter les murs et le vide
- **Saut automatique** : Franchit les obstacles détectés lors du mouvement
- **Sécurité** : Arrête immédiatement en cas de vide ou de danger
- **Visualisation** : Affiche les blocs détectés (obstacles et vides) via des outlines debug
- **Activation simple** : Touche **R** pour activer/désactiver

## Fonctionnement

Le mod utilise une machine à états pour simuler un comportement réaliste :

1. **IDLE** : Analyse les 4 directions et choisit la plus sûre
2. **MOVING** : Effectue le mouvement choisi (10-20 ticks) et vérifie périodiquement
3. **MICRO_ADJUST** : Effectue un court contre-mouvement (2-4 ticks) pour un effet naturel
4. **PAUSE** : Repos entre les cycles (5-15 ticks)

### Détection des obstacles

- **CLEAR** : Voie libre, mouvement possible
- **BLOCKED** : Mur ou bloc solide en chemin → activation du saut
- **VOID** : Pas de sol → abandon de la direction

## Installation

1. Installer [Fabric Loader](https://fabricmc.net/)
2. Télécharger depuis [Releases](../../releases) ou compiler localement
3. Placer le `.jar` dans le dossier `mods/`
4. Lancer Minecraft

## Compilation

```bash
./gradlew clean build
```

L'artifact compilé se trouve dans `build/libs/anti-afk-mod-1.0.0.jar`

## Versions

- **Minecraft** : 1.21.1
- **Fabric Loader** : 0.18.4
- **Fabric API** : 0.104.0+1.21.1
- **Java** : 17+

## Architecture

- `AntiAfkMod.java` : Point d'entrée, gestion des keybinds et événements
- `AntiAfkModule.java` : Machine à états, orchestration du comportement
- `DirectionManager.java` : Rotation intelligente des directions de mouvement
- `BlockAnalyzer.java` : Détection des obstacles et du vide
- `BlockHighlighter.java` : Visualisation des blocs détectés
