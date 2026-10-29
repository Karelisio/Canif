# Canif

Boîte à outils Android tout-en-un, comme un couteau suisse : des dizaines d'outils pratiques dans une seule appli rapide, légère et soignée.
100 % hors ligne (sauf les taux de change), sans compte, sans pub, sans tracking, sans backend.

Kotlin · Jetpack Compose · Material 3 Expressive · Room · DataStore · Glance · CameraX · ML Kit (embarqué) · PdfBox-Android.

## Structure

```
app/src/main/java/com/karelisio/canif/
├── core/            i18n (FR/EN), réglages DataStore, registre d'outils, autorisations
├── data/            Room (favoris, récents, préréglages, cache des taux, minuteurs)
├── ui/              thème, composants animés, accueil, navigation, onboarding, réglages
├── tools/<cat>/     un package par catégorie d'outils
├── system/          raccourcis, tuiles, widgets
└── update/          mise à jour in-app depuis GitHub Releases
```

### Ajouter un outil

1. Créer l'écran dans `tools/<categorie>/`.
2. Ajouter une entrée `Tool(...)` dans la liste de la catégorie (`id`, nom FR/EN, catégorie, mots-clés FR+EN, icône, capteurs/autorisations requis, `content`).

C'est tout : l'accueil, la recherche, les favoris, les raccourcis et le menu Partager l'utilisent automatiquement.

## Compiler en local

Prérequis : JDK 17+ et le SDK Android (platform 36).

```bash
./gradlew :app:assembleDebug        # APK de debug (id com.karelisio.canif.debug)
./gradlew :app:testDebugUnitTest    # tests unitaires
```

Un `assembleRelease` local sans keystore est signé avec la clé de debug (pratique pour tester R8).

## Publier une version

La CI (`.github/workflows/release.yml`) tourne à chaque push sur `main` :

1. lit la version dans le fichier `VERSION` (format `X.Y.Z`) ;
2. si le tag `vX.Y.Z` n'existe pas encore : construit l'APK release **signé** (R8 activé), vérifie la signature et publie une **GitHub Release** `vX.Y.Z` avec l'APK et le changelog ;
3. sinon : simple build de contrôle.

`versionCode` = `github.run_number`, `versionName` = contenu de `VERSION`.

Pour sortir une version : modifier `VERSION`, ajouter une section `## [X.Y.Z]` dans `CHANGELOG.md` (sinon les messages de commit depuis le dernier tag servent de notes), pousser sur `main`.

L'appli vérifie `releases/latest` au lancement (au plus toutes les 6 h), affiche le changelog, télécharge l'APK et l'installe via PackageInstaller.
Le dépôt est public : les APK sont publiés directement dans ses Releases.

## Étapes manuelles (une seule fois)

### 1. Créer le keystore (à garder précieusement, toujours le même)

```bash
keytool -genkeypair -v -keystore canif.jks -alias canif \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 canif.jks > canif.jks.b64     # macOS : base64 -i canif.jks -o canif.jks.b64
```

Si le keystore change, Android refusera les mises à jour (il faudra désinstaller l'appli).

### 2. Secrets GitHub

*Settings → Secrets and variables → Actions → New repository secret* :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | contenu de `canif.jks.b64` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias (ex. `canif`) |
| `KEY_PASSWORD` | mot de passe de la clé |

Rien de sensible n'est commité (`*.jks`, `*.keystore`, `*.b64` sont ignorés).

### 3. Sur le téléphone

- Installer le premier APK depuis la page Releases (autoriser l'installation depuis le navigateur).
- Au premier lancement, l'onboarding demande chaque autorisation ; « Plus tard » est toujours possible (Réglages → Autorisations).
- Honor / MagicOS : accepter « Pas d'optimisation batterie » et, dans *Réglages → Batterie → Lancement d'applis*, passer Canif en gestion manuelle (tout autoriser) pour le métronome et les minuteurs en arrière-plan.

## Vie privée

Aucune donnée ne quitte le téléphone. Seules requêtes réseau : taux de change (Frankfurter / BCE) et vérification des mises à jour (API GitHub).
