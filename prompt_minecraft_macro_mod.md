# Prompt — Création d'un mod Minecraft 1.21 Fabric : Système de Macros

## Contexte général

Tu es un développeur Java expert en modding Minecraft avec l'API Fabric. Tu vas créer un mod complet, propre et bien structuré pour Minecraft 1.21 (Java Edition), côté **client uniquement** (client-side mod).

Ce mod permet au joueur de :
1. **Enregistrer des macros** : capturer une séquence de waypoints (positions) et, à chaque waypoint, une liste de blocs à miner avec leur état attendu.
2. **Sauvegarder des macros** : persistance JSON dans le dossier config du mod.
3. **Exécuter des macros** : navigation automatique par pathfinding vers chaque waypoint, vérification des blocs avant de miner, skip si le bloc ne correspond plus.
4. **Gérer les macros** : interface utilisateur in-game + commandes client.

---

## Stack technique

- **Minecraft** : 1.21 (Java Edition)
- **Loader** : Fabric Loader 0.15+
- **API** : Fabric API 0.100+
- **Java** : 21
- **Build** : Gradle 8 avec Loom 1.6+
- **Sérialisation** : Gson (inclus dans Minecraft)
- **Aucune dépendance externe autre que Fabric API**

---

## Structure du projet

```
src/main/java/com/example/macromod/
├── MacroMod.java                    # Point d'entrée principal
├── MacroModClient.java              # Point d'entrée client
│
├── model/
│   ├── Macro.java                   # Modèle : une macro complète
│   ├── MacroStep.java               # Modèle : un waypoint + ses blocs
│   ├── BlockTarget.java             # Modèle : un bloc cible avec état attendu
│   └── MacroState.java              # Enum : état d'exécution
│
├── manager/
│   ├── MacroManager.java            # CRUD macros + persistance JSON
│   └── MacroExecutor.java           # Machine à états + boucle d'exécution
│
├── pathfinding/
│   ├── PathFinder.java              # Algorithme A* client-side
│   ├── PathNode.java                # Nœud du graphe A*
│   └── MovementHelper.java          # Simulation du mouvement joueur
│
├── recording/
│   ├── MacroRecorder.java           # Gestion du mode enregistrement
│   └── RecordingState.java          # Enum : état de l'enregistrement
│
├── ui/
│   ├── MacroScreen.java             # Écran principal de gestion des macros
│   ├── MacroEditScreen.java         # Écran d'édition d'une macro
│   ├── MacroStepWidget.java         # Widget d'une étape dans la liste
│   └── HudOverlay.java              # HUD d'état d'exécution
│
├── command/
│   └── MacroCommands.java           # Commandes /macro ...
│
├── config/
│   ├── ModConfig.java               # Configuration générale du mod
│   └── ConfigManager.java           # Chargement/sauvegarde config
│
└── util/
    ├── BlockUtils.java              # Utilitaires blocs (reach, identité)
    ├── PlayerUtils.java             # Utilitaires joueur (direction, mouvement)
    └── JsonUtils.java               # Adaptateurs Gson pour types Minecraft
```

```
src/main/resources/
├── fabric.mod.json
├── macromod.mixins.json
└── assets/macromod/
    └── lang/
        └── fr_fr.json               # Traductions françaises
        └── en_us.json               # Traductions anglaises
```

---

## Modèles de données

### `BlockTarget.java`

```java
public class BlockTarget {
    public BlockPos pos;
    public String blockId;          // Ex : "minecraft:coal_ore"
    public NbtCompound blockNbt;    // État NBT optionnel pour blocs complexes
    public boolean mined;           // Marqué true après minage réussi
}
```

### `MacroStep.java`

```java
public class MacroStep {
    public String label;             // Nom lisible, ex : "Veine de charbon 1"
    public BlockPos destination;     // Waypoint de navigation
    public List<BlockTarget> targets; // Blocs à miner à cette étape
    public int radius;               // Rayon de recherche optionnel autour du waypoint
}
```

### `Macro.java`

```java
public class Macro {
    public String id;                // UUID unique
    public String name;              // Nom affiché
    public String description;       // Description optionnelle
    public long createdAt;           // Timestamp création
    public long updatedAt;           // Timestamp dernière modification
    public List<MacroStep> steps;
    public MacroConfig config;       // Paramètres d'exécution spécifiques
}
```

### `MacroConfig.java`

```java
public class MacroConfig {
    public boolean loop;              // Rejouer en boucle
    public boolean skipMismatch;      // Skip si bloc différent (défaut : true)
    public boolean stopOnDanger;      // Stop si vie basse ou mobs proches
    public int miningDelay;           // Délai entre chaque minage (ms)
    public int moveTimeout;           // Timeout navigation vers waypoint (ticks)
    public float arrivalRadius;       // Distance pour considérer waypoint atteint
}
```

---

## MacroManager — Persistance

- Dossier de stockage : `FabricLoader.getInstance().getConfigDir().resolve("macromod/macros/")`
- Une macro = un fichier `{id}.json`
- Chargement au démarrage du client, sauvegarde à chaque modification
- Méthodes requises :
  - `loadAll()` : lit tous les fichiers JSON du dossier
  - `save(Macro macro)` : écrit le fichier JSON de la macro
  - `delete(String id)` : supprime le fichier
  - `getAll()` : retourne la liste en mémoire
  - `getById(String id)` : retourne une macro par son ID
  - `create(String name)` : crée une nouvelle macro vide et la sauvegarde
  - `duplicate(String id)` : duplique une macro existante

**Adaptateurs Gson nécessaires** (dans `JsonUtils.java`) :
- `BlockPos` ↔ `{x, y, z}`
- `BlockState` ↔ String id + properties map
- `NbtCompound` ↔ JSON object
- `Identifier` ↔ String

---

## MacroRecorder — Enregistrement

État interne : `IDLE | RECORDING | PAUSED`

Méthodes :
- `startRecording(String macroName)` : crée une nouvelle macro, passe en mode RECORDING
- `addWaypoint()` : capture `player.getBlockPos()` + label auto-généré, crée un `MacroStep`
- `addBlockTarget(BlockPos pos)` : lors du survol/clic d'un bloc, capture `BlockState` + `blockId` et l'ajoute au step courant
- `pauseRecording()` / `resumeRecording()`
- `stopRecording()` : finalise et sauvegarde la macro
- `cancelRecording()` : annule sans sauvegarder

**Intégration events** :
- `ClientPlayerInteractBlockEvent` : détecter le clic sur un bloc en mode enregistrement
- `ClientTickEvent` : afficher le HUD d'enregistrement
- Écouter `ClientPlayerBlockBreakEvents` pour capture automatique optionnelle

---

## PathFinder — Algorithme A*

Implémentation client-side d'un A* 3D pour naviguer dans le monde Minecraft.

**`PathNode.java`** :
```java
public class PathNode {
    public BlockPos pos;
    public PathNode parent;
    public double g;   // Coût depuis départ
    public double h;   // Heuristique (distance euclidienne vers cible)
    public double f;   // g + h
}
```

**`PathFinder.java`** :
- Méthode principale : `List<BlockPos> findPath(BlockPos start, BlockPos goal, ClientWorld world)`
- Mouvements autorisés : 4 directions horizontales + diagonales + saut (1 bloc vers le haut) + descente (1-2 blocs)
- Vérifications pour chaque nœud :
  - Le bloc destination est solide (on peut marcher dessus)
  - Les 2 blocs au-dessus sont non-solides (le joueur peut tenir debout)
  - Détection et contournement des blocs dangereux (lave, feu, cactus)
- Limite : `MAX_NODES = 2000` pour éviter les freezes
- Fallback : si aucun chemin trouvé, retourner null et logguer un warning
- **Optimisation** : utiliser une `PriorityQueue` pour open list, `HashMap<BlockPos, PathNode>` pour closed list

**`MovementHelper.java`** :
- `moveTowards(PlayerEntity player, BlockPos target)` : calcule le yaw cible, simule les inputs de mouvement
- `simulateJump(PlayerEntity player)` : déclenche un saut si nécessaire
- `isArrived(PlayerEntity player, BlockPos target, float radius)` : vérifie l'arrivée
- Utiliser `GameOptions` pour simuler les keybindings (`forwardKey`, `jumpKey`, `sneakKey`)
- **Ne jamais utiliser de téléportation** — déplacement purement via simulation d'inputs

---

## MacroExecutor — Machine à états

```
IDLE
  └─(start)─► PATHFINDING
                └─(path found)─► MOVING
                     └─(arrived)─► MINING
                          └─(all blocks done / skipped)─► NEXT_STEP
                               └─(more steps)─► PATHFINDING
                               └─(no more steps)─►
                                    ├─(loop=true)─► PATHFINDING (step 0)
                                    └─(loop=false)─► COMPLETED ─► IDLE
MOVING ──(timeout)──► ERROR ─► IDLE
MINING ──(stuck > 5s)──► SKIP_BLOCK ─► continuer mining
Tout état ──(stop())──► IDLE
```

**Méthodes publiques** :
- `start(String macroId)` : initialise et démarre
- `stop()` : arrêt propre, libère les inputs simulés
- `pause()` / `resume()`
- `getState()` : état courant
- `getProgress()` : `{stepIndex, totalSteps, blocksMinedInStep, totalBlocksInStep}`

**Appelé à chaque `ClientTickEvent.END_CLIENT_TICK`**

**Logique mining** :
1. Pour chaque `BlockTarget` du step courant (non encore miné) :
   - Récupérer `world.getBlockState(target.pos)`
   - Comparer avec `target.blockId` : si différent ET `skipMismatch=true` → marquer comme skipped, continuer
   - Si correspondant → orienter le regard (`player.setYaw`, `player.setPitch`) vers le bloc
   - Appeler `MinecraftClient.getInstance().interactionManager.attackBlock(pos, direction)` en boucle jusqu'à destruction
   - Respecter `miningDelay` entre blocs
2. Marquer `BlockTarget.mined = true` une fois détruit (vérifier que `world.getBlockState(pos).isAir()`)

---

## HUD Overlay

Rendu via `HudRenderCallback` (ou `DrawContext` en 1.21).

Affiche en haut à gauche (position configurable) :
- **Nom de la macro** en cours
- **État** : `🔴 Enregistrement` / `🟢 En cours` / `⏸ En pause` / `✅ Terminé` / `❌ Erreur`
- **Progression** : `Étape 3 / 7 — Blocs : 4 / 6`
- **Waypoint courant** : coordonnées de destination
- **Barre de progression** simple (rectangle plein)
- En mode recording : liste des derniers blocs ajoutés

Condition d'affichage : uniquement si macro active ou en cours d'enregistrement.

---

## MacroScreen — Interface principale

Écran accessible via keybind (défaut : `M`).

**Layout** :
- **Panneau gauche** : liste scrollable des macros sauvegardées
  - Chaque entrée : nom, date de création, nombre d'étapes, boutons (▶ Run, ✏ Edit, 🗑 Delete, 📋 Duplicate)
- **Panneau droit** : prévisualisation de la macro sélectionnée
  - Détails : nom, description, config
  - Liste des étapes avec leurs blocs cibles
- **Barre du bas** : bouton "Nouvelle macro", "Importer JSON", "Exporter JSON"

---

## MacroEditScreen — Édition

- **Champ nom** et **description**
- **Configuration** (checkboxes + sliders) : loop, skipMismatch, stopOnDanger, miningDelay
- **Liste des étapes** : drag & drop pour réordonner, bouton "Ajouter étape depuis position actuelle"
- **Pour chaque étape** : label éditable, coordonnées, liste des blocs (avec possibilité d'en ajouter/supprimer manuellement)
- Bouton "Sauvegarder" et "Annuler"

---

## Commandes client (`/macro`)

Enregistrées via `ClientCommandManager.DISPATCHER` au `ClientCommandRegistrationCallback`.

```
/macro list                         → Liste toutes les macros avec ID et nom
/macro run <nom_ou_id>              → Lance l'exécution d'une macro
/macro run <nom_ou_id> --loop       → Lance en mode boucle
/macro stop                         → Arrête l'exécution en cours
/macro pause                        → Met en pause
/macro resume                       → Reprend
/macro status                       → Affiche état + progression dans le chat
/macro record start <nom>           → Démarre l'enregistrement
/macro record waypoint [label]      → Ajoute un waypoint à la position actuelle
/macro record block                 → Ajoute le bloc visé comme cible
/macro record stop                  → Finalise et sauvegarde
/macro record cancel                → Annule l'enregistrement
/macro delete <nom_ou_id>           → Supprime une macro
/macro info <nom_ou_id>             → Affiche les détails d'une macro
/macro export <nom_ou_id>           → Affiche le JSON dans le chat (pour copier-coller)
```

Autocomplétion (`suggests`) sur les noms de macros pour les commandes qui en ont besoin.

---

## Configuration du mod (`config/macromod.json`)

```json
{
  "hudPosition": "TOP_LEFT",
  "hudScale": 1.0,
  "hudVisible": true,
  "defaultMiningDelay": 50,
  "defaultMoveTimeout": 200,
  "defaultArrivalRadius": 1.5,
  "defaultStopOnDanger": true,
  "defaultSkipMismatch": true,
  "maxPathNodes": 2000,
  "recordingAutoAddBlocks": false,
  "keybindOpenGui": "key.keyboard.m",
  "keybindAddWaypoint": "key.keyboard.v",
  "keybindToggleRecording": "key.keyboard.r",
  "keybindStopMacro": "key.keyboard.period"
}
```

---

## Sécurités et robustesse

Implémenter les protections suivantes dans `MacroExecutor` :

1. **Anti-chute** : si le bloc sous le joueur est du vide ou de la lave pendant MOVING → pause automatique + message d'avertissement
2. **Détection de stuck** : si le joueur n'a pas bougé de plus de 0.5 blocs en 3 secondes pendant MOVING → recalculer le chemin ou passer à l'étape suivante
3. **Stop sur danger** (si `stopOnDanger=true`) :
   - Vie < 6 cœurs (healthPoints < 12.0)
   - Mob hostile dans un rayon de 8 blocs
   - Feu sur le joueur
4. **Timeout navigation** : si `moveTimeout` ticks écoulés sans arriver → ERROR state + log
5. **Vérification world** : si le chunk du waypoint n'est pas chargé → attendre le chargement (max 100 ticks) avant de naviguer
6. **Libération des inputs** : dans `stop()`, s'assurer de relâcher tous les keybindings simulés pour éviter que le joueur reste bloqué à avancer

---

## Améliorations à implémenter

### Fonctionnalités avancées

1. **Mode "scan"** : lors de l'enregistrement, scanner automatiquement les blocs dans un rayon R autour du waypoint et proposer une sélection par type de bloc.

2. **Filtre par type de bloc** : au lieu de cibler des blocs spécifiques, permettre de cibler "tous les blocs de type X dans le rayon du waypoint" (ex : tous les `coal_ore` dans un rayon de 5 blocs).

3. **Import/Export JSON** : via un `TextFieldWidget` dans l'UI ou via commande `/macro export`, permettre de partager des macros entre joueurs.

4. **Statistiques d'exécution** : après chaque run, afficher dans le chat :
   - Nombre de blocs minés vs skippés
   - Temps d'exécution total
   - Distance totale parcourue

5. **Queue de macros** : pouvoir enchaîner plusieurs macros (`MacroQueue`) qui s'exécutent séquentiellement.

6. **Bookmarks de position** : système léger de sauvegarde de coordonnées nommées, réutilisables lors de la création de macros.

7. **Conditions d'arrêt personnalisées** : arrêter la macro si l'inventaire est plein, si un item spécifique est récupéré N fois, si l'heure du jeu est > X.

8. **Smooth camera** : pendant l'exécution, le regard du joueur pivote progressivement vers les blocs cibles au lieu de snapper instantanément.

9. **Preview de chemin** : afficher en overlay (via `WorldRenderEvents`) le chemin calculé par A* sous forme de particules ou de lignes colorées.

10. **Undo/Redo** lors de l'édition d'une macro dans l'UI.

### Qualité de code

- Utiliser `@Environment(EnvType.CLIENT)` sur toutes les classes client
- Logger via `LoggerFactory.getLogger(MacroMod.MOD_ID)` — pas de `System.out.println`
- Toutes les interactions avec `MinecraftClient` dans le thread de rendu → utiliser `MinecraftClient.getInstance().execute(() -> ...)` si appelé depuis un autre thread
- Javadoc sur toutes les méthodes publiques
- `MacroExecutor` est un singleton accessible via `MacroMod.getExecutor()`
- `MacroManager` est un singleton accessible via `MacroMod.getManager()`

---

## fabric.mod.json

```json
{
  "schemaVersion": 1,
  "id": "macromod",
  "version": "1.0.0",
  "name": "Macro Mod",
  "description": "Enregistrez, sauvegardez et exécutez des macros de minage automatique.",
  "authors": ["TonNom"],
  "environment": "client",
  "entrypoints": {
    "client": ["com.example.macromod.MacroModClient"]
  },
  "mixins": ["macromod.mixins.json"],
  "depends": {
    "fabricloader": ">=0.15.0",
    "fabric-api": ">=0.100.0",
    "minecraft": "~1.21"
  }
}
```

---

## Mixins nécessaires

Fichier `macromod.mixins.json` :

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.macromod.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "ClientPlayerEntityMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

`ClientPlayerEntityMixin.java` : injecter dans `ClientPlayerEntity.tickMovement()` pour permettre la simulation d'inputs de mouvement sans passer par `KeyBinding.setKeyPressed()` (qui est fragile). Utiliser un `@Inject` sur `tickMovement` pour appliquer les vecteurs de mouvement calculés par `MovementHelper`.

---

## Ordre d'implémentation recommandé

1. **Setup Gradle + fabric.mod.json + structure de packages** (vide)
2. **Modèles de données** (`Macro`, `MacroStep`, `BlockTarget`, enums)
3. **JsonUtils** (adaptateurs Gson pour BlockPos, BlockState, etc.)
4. **MacroManager** (CRUD + persistance JSON — testable sans Minecraft)
5. **MacroRecorder** (enregistrement basique via commandes)
6. **HudOverlay** (affichage état enregistrement)
7. **MacroCommands** (commandes `/macro record` + `/macro list`)
8. **MovementHelper** (mouvement basique sans pathfinding — téléportation en debug)
9. **MacroExecutor** (machine à états, mining, sans pathfinding d'abord)
10. **PathFinder A\*** (navigation réelle)
11. **MacroScreen + MacroEditScreen** (UI complète)
12. **ConfigManager** (configuration persistante)
13. **Améliorations** (protections, statistiques, filtres bloc, etc.)

---

## Consignes générales

- Écrire du code **complet et fonctionnel**, pas de `// TODO` ni de `// ...`
- Tous les fichiers doivent être dans le bon package
- Respecter scrupuleusement l'API Fabric 1.21 (les noms de méthodes ont changé entre versions — vérifier `ClientPlayerEntity`, `MinecraftClient`, `ClientWorld`)
- Ne jamais modifier le code serveur, tout est client-side
- Les `Screen` doivent hériter de `net.minecraft.client.gui.screen.Screen`
- Utiliser `Text.translatable(...)` pour tous les textes affichés (i18n)
- Les keybindings sont enregistrés dans `MacroModClient.onInitializeClient()`
- Commencer par générer les fichiers un par un, dans l'ordre d'implémentation ci-dessus

Génère maintenant le projet complet, fichier par fichier.
