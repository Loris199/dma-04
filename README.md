# DMA - Laboratoire 4
*Loris Marzullo, Zaid Schouwey, Léonard Jouve*

## 1. Scan BLE - Ajout d’un filtre

Dans `MainActivity.scanLeDevice()`, un `ScanFilter` limite le scan aux périphériques annonçant le service custom SYM (`3c0a1000-281d-4b48-b2a7-f15579a1c38f`) via `setServiceUuid()`. La liste affichée dans la `RecyclerView` ne contient ainsi que des appareils susceptibles d’être compatibles avec l’application.

Les annonces BLE étant limitées en taille, un périphérique n’annonce pas toujours tous ses services : ce filtre constitue un premier tri avant la vérification complète après connexion (manipulation 2).

## 2. Exploration des services et caractéristiques sur le périphérique connecté

**`isRequiredServiceSupported()`** parcourt les services découverts après connexion. On conserve les références vers :

- *Current Time Service* (`00001805-0000-1000-8000-00805f9b34fb`) et sa caractéristique *Current Time* (`00002a2b-0000-1000-8000-00805f9b34fb`)
- *Service SYM* (`3c0a1000-281d-4b48-b2a7-f15579a1c38f`) et ses caractéristiques `int` (`3c0a1001-…`), *Temperature* (`3c0a1002-…`) et *BTN* (`3c0a1003-…`)

La méthode retourne `true` uniquement si tous ces éléments sont présents ; sinon la librairie coupe la connexion (`REASON_NOT_SUPPORTED`).

**`initialize()`** est appelée une fois la compatibilité confirmée. On active les notifications sur *Current Time* et *BTN*, avec des callbacks qui décodent les octets reçus (format Current Time selon la spécification, compteur de clics sur 1 byte) et les transmettent au `ViewModel` via `DMAServiceListener`.

## 3. Mise en place de l’API de communication avec le périphérique

Les méthodes métier sont dans `DMABleManager`, appelées depuis `BleViewModel` si une connexion est active :

| Méthode | Opération BLE | Données |
|---------|---------------|---------|
| `readTemperature()` | Lecture de `3c0a1002-…` | UInt16LE ÷ 10 → °C, callback `temperatureUpdate()` |
| `writeInteger(value)` | Écriture sur `3c0a1001-…` | Entier encodé en UInt32LE (4 octets) |
| `writeCurrentTime()` | Écriture sur `00002a2b-…` | Heure courante du téléphone au format Current Time (10 octets) |

Les notifications (heure, compteur de boutons) sont gérées dans `initialize()` et remontent via `dateUpdate()` et `clickCountUpdate()`. `BleViewModel` implémente `DMAServiceListener` et expose les valeurs en `LiveData`.

## 4. Conception de l’interface utilisateur

Le `BleConnectedFragment` (`fragment_connected.xml`) permet d’utiliser toutes les fonctionnalités des deux services :

- **Température** : bouton « Lire » → `readTemperature()` ; affichage dans un `TextView`
- **Boutons** : compteur mis à jour automatiquement par notification
- **Heure** : affichage par notification ; bouton « Mettre à jour » → `setTime()` (écriture de l’heure du smartphone)
- **Entier** : `EditText` + bouton « Envoyer » → `sendValue()` (graphique sur le Pixl.js)

La déconnexion est accessible depuis le menu de la barre d’outils.

## 5. Questions théoriques

### 5.1 Température en entier × 10 plutôt qu’en float

Encoder la température en **UInt16** (valeur × 10) permet une représentation fixe de 2 octets. Un `float` occuperait 4 octets, nécessiterait un support flottant sur le microcontrôleur et introduirait des imprécisions d’arrondi. Les entiers sont plus simples à parser, à valider et à transmettre sur un protocole à faible débit.

### 5.2 Service de niveau de batterie

On peut s’appuyer sur le **Battery Service** standard du Bluetooth SIG :

| Élément | UUID | Opérations | Format des données |
|---------|------|------------|-------------------|
| **Battery Service** | `0000180f-0000-1000-8000-00805f9b34fb` (0x180F) | — | Regroupe les caractéristiques ci-dessous |
| **Battery Level** | `00002a19-0000-1000-8000-00805f9b34fb` (0x2A19) | **Lecture**, **notification** (optionnelle) | **1 octet**, entier non signé : pourcentage de charge restante (0–100 %) |

Le smartphone s’abonne aux notifications pour être informé des changements de niveau sans relire en permanence la caractéristique. Aucune écriture n’est requise côté central pour ce cas d’usage.
