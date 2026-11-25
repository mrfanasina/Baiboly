# Formats JSON de l'application

Deux fichiers distincts, chacun son rôle (rien d'autre à maintenir) :

| Fichier | Rôle |
|---|---|
| `update.json` | mise à jour de l'APK (une seule version publiée) |
| `downloads.json` | les fichiers à télécharger : lectures perikopa, Aina, etc. |

Les lectures téléchargées depuis `downloads.json` sont **importées dans
SQLite** (`Baiboly_mg.db`, `aina_mofonaina.db`) :

- elles restent disponibles **hors ligne** ;
- elles ne sont **jamais supprimées automatiquement**, même si le réseau
  disparaît ou si l'URL n'est plus publiée — y compris après un an ;
- seul l'utilisateur peut les supprimer (écran Downloads ou transfert).

---

## 1. `update.json` — mise à jour de l'APK

Format déjà en place, inchangé :

```json
{
  "schema_version": 1,
  "version_code": 5,
  "version_name": "5.3",
  "apk_url": "https://github.com/mrfanasina/Baiboly-Releases/releases/download/5.3/Baiboly-v5.3.apk",
  "file_size": 38945634,
  "sha256": "…",
  "published_at": "2026-11-15",
  "forced": false,
  "min_supported_version_code": 1,
  "changelog": [
    "Fanampiana Diksioneran'ny Baiboly sy fanatsarana ny endriky ny application"
  ]
}
```

| Champ | Obligatoire | Rôle |
|---|---|---|
| `version_code` | oui | `versionCode` de l'APK publié |
| `version_name` | oui | version affichée (`5.3`) |
| `apk_url` | oui | lien direct de l'APK |
| `file_size` | non | taille en octets |
| `sha256` | non | empreinte du fichier |
| `published_at` | non | date `yyyy-MM-dd` |
| `forced` | non | mise à jour imposée |
| `changelog` | non | liste des nouveautés |

C'est **le seul fichier qui déclenche la carte « Fanavaozana »** et la
notification de mise à jour. Il ne publie que la dernière version ; les
anciennes restent sur les releases GitHub. Si tu veux les proposer au
téléchargement, garde-les dans `downloads.json` (elles n'y déclenchent
aucune mise à jour).

---

## 2. `downloads.json` — fichiers à télécharger

Même format que le fichier déjà en place (catégories + items). Modèle
minimal : `docs/downloads.template.json`.

```json
{
  "schema_version": 1,
  "updated_at": "2026-11-25",
  "categories": [
    {
      "id": "vakiteny",
      "title": "Vakiteny sy Perikopa",
      "order": 1,
      "items": [
        {
          "id": "perikopa-2028",
          "title": "Perikopa 2028",
          "description": "Vakiteny isam-andro · taona 2028",
          "type": "perikopa",
          "dataset": "perikopa",
          "year": 2028,
          "url": "https://data.fjkm.mg/updates/perikopa/2028.json",
          "file_size": 66622,
          "published_at": "2027-11-01",
          "enabled": true
        }
      ]
    }
  ]
}
```

### Champs d'un élément

| Champ | Obligatoire | Rôle |
|---|---|---|
| `id` | recommandé | identifiant stable |
| `title` | oui | titre affiché |
| `description` | non | sous-titre affiché |
| `type` | oui | `perikopa`, `aina`, `mofonaina`, `data`, `apk` |
| `url` | oui | lien du fichier (absolu de préférence) |
| `file_size` | non | taille en octets |
| `version` | non | version affichée |
| `published_at` | non | date `yyyy-MM-dd` |
| `dataset` | lectures | `perikopa` ou `aina` (déduit du `type` sinon) |
| `year` | lectures | année couverte (2028, 2040…) |
| `enabled` | non | `false` pour masquer sans supprimer l'entrée |

- La catégorie (`categories[].title`) devient un en-tête dans l'écran
  Downloads.
- `url` relative : résolue avec `base_url` (champ facultatif du fichier)
  ou la `BASE_URL` configurée. Une URL `https://…` est utilisée telle quelle.
- Les `apk` listés ici sont téléchargeables individuellement, mais la
  mise à jour automatique reste pilotée par `update.json`.

### Ajouter 2029, 2040… sans mise à jour de l'app

Ajouter un item dans la catégorie des lectures, puis déposer le fichier à
l'URL indiquée :

```json
{
  "id": "perikopa-2040",
  "title": "Perikopa 2040",
  "type": "perikopa",
  "dataset": "perikopa",
  "year": 2040,
  "url": "https://data.fjkm.mg/updates/perikopa/2040.json",
  "enabled": true
}
```

L'application relit `downloads.json` à chaque ouverture de l'écran
Downloads (et au retour du réseau) : elle télécharge, importe dans la base,
et l'année devient disponible le moment venu — **sans nouvelle version de
l'application**.

---

## 3. Format du fichier de lectures (perikopa)

Modèle : `docs/perikopa.template.json` — un seul exemple, à compléter avec
les 365 jours de l'année.

```json
{
  "format": "baiboly-perikopa",
  "version": 1,
  "year": 2028,
  "months": [
    {
      "month_name": "Janoary",
      "theme": "LOHAHEVITRA NY VOLANA",
      "days": [
        {
          "date": "2028-01-01",
          "name": "Fetin'ny Fampisehoana",
          "season": "epifania",
          "readings": ["Sal 111:1-10", "Jao 1:1-5", "Efe 3:20-21"]
        }
      ]
    }
  ]
}
```

| Champ | Obligatoire | Rôle |
|---|---|---|
| `year` | recommandé | année couverte |
| `months[]` | oui | un objet par mois |
| `months[].month_name` | recommandé | « Janoary »… (déduit de la date si absent) |
| `months[].theme` | non | lohahevitra du mois |
| `months[].days[]` | oui | jours du mois |
| `days[].date` | **oui** | `yyyy-MM-dd` |
| `days[].name` | non | nom du dimanche / de la fête |
| `days[].season` | non | saison liturgique (`epifania`, `karemy`, `paska`…) |
| `days[].readings[]` | oui | références des lectures, dans l'ordre |

`readings` peut aussi s'appeler `verses`. Chaque lecture est une chaîne
(`"Jao 1:1-5"`) telle que l'application la comprend déjà.

### Import

- mois : `perikopa_months (year, month_name, theme)` — mis à jour si existant ;
- jour : `perikopa_days (month_id, date, name, season_id)` — **remplacé**
  si la date existe déjà (les lectures du jour sont réécrites) ;
- lectures : `perikopa_readings (day_id, verse)` ;
- saison : réutilisée si elle existe déjà, sinon créée.

---

## 4. Format du fichier Aina sy Fahasalamana

Modèle : `docs/aina.template.json` — identique au format de
`assets/mofonaina.json`.

```json
{
  "format": "baiboly-aina",
  "version": 1,
  "cover": {
    "title": "AINA SY FAHASALAMANA",
    "subtitle": "Mofonaina isan'andro",
    "institution": "LYCEE FJKM RANDZAVOLA IVOHIDAHY – FIANARANTSOA",
    "author": "Rolph RANDRIAMANANTSOA"
  },
  "days": [
    {
      "id": "2028-01-01",
      "date_iso": "2028-01-01",
      "day_index": 1,
      "day_name_mg": "ASABOTSY",
      "day_name_en": "Saturday",
      "month_mg": "JANOARY",
      "month_fr": "Janvier",
      "year": 2028,
      "bible_reference": "Sal. 121:1–8",
      "theme_slogan": "Ny Tompo no mpiambina anao",
      "meditation": "…",
      "message_today": "…",
      "prayer": "…"
    }
  ]
}
```

| Champ | Obligatoire | Rôle |
|---|---|---|
| `days[].id` | **oui** | identifiant unique (la date ISO par convention) |
| `days[].date_iso` | oui | `yyyy-MM-dd`, utilisé pour « anio » |
| `days[].day_index` | non | numéro du jour dans le mois |
| `days[].day_name_mg` / `_en` | non | nom du jour |
| `days[].month_mg` / `_fr` | non | nom du mois |
| `days[].year` | non | année |
| `days[].bible_reference` | non | verset du jour |
| `days[].theme_slogan` | non | thème |
| `days[].meditation` | non | méditation |
| `days[].message_today` | non | message du jour |
| `days[].prayer` | non | prière |

`INSERT OR REPLACE` sur `aina_mofonaina_days (id)` : un jour déjà présent
est mis à jour, les autres sont ajoutés. `cover` est informatif.

---

## 5. Pourquoi un import ne disparaît pas

1. Le JSON est téléchargé, puis importé dans SQLite.
2. La ligne de téléchargement est marquée `imported`, et le jeu de données
   est enregistré dans `data_sets`.
3. Le fichier JSON temporaire est supprimé : le contenu vit dans la base.
4. Quand le réseau revient, l'écran Downloads ne supprime que les fichiers
   **non importés** dont l'URL n'est plus dans `downloads.json`.
5. Aucune expiration n'existe : même après un an sans réseau, les lectures
   restent dans la base et s'affichent à leur année.

Suppression possible uniquement sur action de l'utilisateur (bouton
« Hamafa », ou suppression d'un envoi dans le transfert). Une
désinstallation de l'application efface évidemment la base.

---

## 6. Transfert entre téléphones

Dans **Famindrana → Handefa**, les jeux de données présents dans la base
apparaissent comme « Perikopa 2027 (BD) » / « Aina sy Fahasalamana (BD) » :
ils sont **reconstruits en JSON depuis SQLite** au moment de l'envoi (rien
n'est stocké en double). Le récepteur les importe directement dans sa base.

Les fichiers déjà téléchargés (JSON, APK) apparaissent aussi dans la liste ;
un appui long permet de les supprimer du téléphone.
