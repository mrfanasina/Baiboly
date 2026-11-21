# Liquid Glass sur les cartes de l'accueil — notes de travail

État au 19/11/2026, branche `testfeatures` (modifications **non commitées**).

## Objectif

Deux styles de fond pour les cartes de l'accueil, choisis dans
**Paramètres → « Endrik'ireo karatra »** :

| Style (`card_background_style`) | Rendu |
|---|---|
| `image` (défaut, « Sary ») | photo décorative par carte, comme avant |
| `glass` (« Liquid Glass ») | vrai flou du fond de l'écran + teinte translucide |

La préférence est stockée dans `shared_prefs/app_settings.xml`, gérée par
`AppBackgrounds.getCardBackgroundStyle()` / `setCardBackgroundStyle()`.

## Fichiers touchés

**Nouveaux**

- `ui/common/GlassBlur.java` — bitmap flouté : `backdrop(...)` construit une
  version réduite (max 360 px) du fond, applique le voile demandé, la saturation
  et un box blur 3 passes ; cache LRU (8 entrées).
- `ui/common/GlassSurfaceDrawable.java` — dessine le verre : fond flouté,
  teinte, reflet haut, ombre bas, bordure ; recalcule l'alignement à chaque
  dessin depuis `getLocationInWindow`.
- `ui/common/GlassCardHelper.java` — réglages/couleurs (`Config`), pose du
  verre, `remove()`, `styleRow()` pour les lignes internes.

**Modifiés**

- `ui/common/AppBackgrounds.java` — aiguillage : branche verre
  (`GlassCardHelper.apply`) sinon `remove` + photo via
  `card.setBackground(new PhotoBackgroundDrawable(...))` (chemin d'origine).
- `ui/home/HomeFragment.java` — espacement des lignes (Mofonaina, Novakiana,
  Perikopa) et `GlassCardHelper.styleRow(...)`.
- `SettingsFragment.java`, `res/layout/fragment_settings.xml` — entrée et
  sélecteur de style des cartes (`cardCardStyle`).
- `res/layout/fragment_home.xml` — retrait des fonds opaques qui bloquaient la
  translucidité.
- `res/layout/item_home_row.xml` — lignes plus aérées (padding 14 dp,
  minHeight 56 dp).

## Comment le verre est rendu

1. La photo de fond de l'écran est floutée une fois (par photo + taille), avec
   un voile réduit à 45 % de celui de l'écran et une saturation augmentée :
   le verre doit paraître plus vif que le fond qui l'entoure.
2. Le verre est posé **sur une vue dont les limites sont celles de la carte** :
   l'enfant unique de la carte (`GlassCardHelper.targetOf`), sinon une couche
   enfant dédiée (`ensureLayer`) quand la carte a plusieurs enfants ou que son
   enfant a déjà un fond.
3. `prepareCard()` neutralise ce que la carte dessine elle-même : fond Material
   transparent, ombre 0, trait 0, photo « Sary » retirée.
4. Le fond est dessiné via un `BitmapShader` en `CLAMP` : hors du bitmap, le
   bord est prolongé, jamais étiré.

## Pièges déjà rencontrés (à ne pas réintroduire)

- **`card.setBackground(...)` pour le verre** : le rendu n'était pas fiable sur
  `MaterialCardView` → d'où la vue cible.
- **Couche `match_parent` dans une carte `wrap_content`** d'un
  `NestedScrollView` : spec `UNSPECIFIED`, la couche peut faire 0 de haut ou la
  hauteur du viewport. C'est ce qui produisait un panneau qui débordait sous
  les lignes.
- **`srcRect.intersect(...)` + repli « tout le bitmap »** : la zone lue était
  raccourcie puis étirée sur la carte → aplat de couleur, aucune transparence.
  Supprimé, remplacé par le shader CLAMP.
- **Fond de secours** : `Config.fallbackColor` (blanc cassé en clair, quasi
  noir en sombre, 95 % opaque) est peint quand le bitmap flouté n'a pas pu être
  construit. Une carte blanche/noire = chemin de secours, pas un bug de teinte.

## Mise au point : étapes

Le verre est décomposé en étapes indépendantes, pilotées par un seul drapeau
dans `GlassSurfaceDrawable` :

```java
private static final boolean BLUR_ENABLED = false; // étape 1 en cours
```

**Étape 1 — transparence seule (`BLUR_ENABLED = false`, état actuel)**
Aucun bitmap de fond n'est dessiné, aucun fond opaque : la carte n'est qu'une
teinte translucide (`0x14FFFFFF` clair, `0x1A000000` sombre) + sa bordure.
Objectif : vérifier que la photo de l'écran est bien visible à travers la carte
et que la zone de la carte est correctement positionnée.

**Étape 2 — flou (`BLUR_ENABLED = true`)**
Rétablit le fond flouté, le reflet et l'ombre ; on vérifie alors que le motif
apparaît, qu'il est aligné et qu'il défile sous les cartes.

**Étape 3 — réglages esthétiques**
Teinte, saturation, reflet et bordure, puis lisibilité du texte.

Teintes neutres de référence une fois l'effet validé : `0x1AFFFFFF` clair /
`0x26000000` sombre, sans fond de secours opaque.

## Carte de test « Favoris » (ajoutée pour la mise au point)

À la fin de l'accueil, `cardFavorites` (`fragment_home.xml`) contient un
carrousel horizontal (`favoritesCarousel`, un `HorizontalScrollView`) de
petites cartes de versets (`item_favorite_verse.xml`). Les données sont des
**données de démonstration** dans `HomeFragment.setupFavoritesCarousel()` :
4 versets (Salamo 27:11, 34:1, 34:4, 34:6). Un clic ouvre `VersesActivity` avec
la référence en extra `reading`.

À faire : brancher la vraie source de favoris, puis supprimer le `TextView`
`txtFavoritesTodo` (et son texte) ainsi que le commentaire `TODO(favoris)`.
Tout est supprimable sans impact : la carte, la méthode et le layout d'item.

## Points ouverts

- Le fond flouté s'aligne-t-il bien pendant le défilement (le fond doit
  « glisser » sous les cartes) ?
- Lisibilité du texte sur les zones claires de la photo : prévoir un voile
  local sous le texte plutôt qu'une teinte globale plus opaque.
- Coût du premier dessin : le blur est calculé dans `draw()` ; à déplacer hors
  du thread de rendu si des saccades apparaissent.

## Vérification

```bash
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
./gradlew.bat :app:assembleDebug --console=plain
```

APK : `app/build/outputs/apk/debug/app-debug.apk`. Aucune dépendance Gradle
ajoutée, minSdk 23 respecté, aucun ID XML renommé (ViewBinding intact).
