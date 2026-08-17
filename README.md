# VirtualDiapo

VirtualDiapo recrée une projection manuelle de diapositives numériques sur une TV. Le serveur sur l’ordinateur conserve les collections JPEG et le Player Android TV les projette à la télécommande.

## Contenu actuel

- `desktop/virtualdiapo-core` : modèle métier Java sans dépendance à Spring ;
- `desktop/virtualdiapo-desktop` : application JavaFX, serveur Spring Boot, catalogue SQLite et stockage des JPEG ;
- `player-android` : Player Kotlin/Compose pour Android TV.

La découverte mDNS ne fait pas encore partie de cet incrément.

## Prérequis

- JDK 21 ;
- Android SDK avec la plateforme Android 36 pour compiler le Player ;
- un appareil ou émulateur Android TV accessible avec ADB pour le test réel.

## Démarrer le serveur

Depuis `desktop` :

```shell
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run -pl virtualdiapo-desktop -am
```

Le serveur écoute sur toutes les interfaces, port `8080`. Vérification locale :

```shell
curl http://localhost:8080/api/v1/server
curl http://localhost:8080/api/v1/collections
```

Les données sont conservées par défaut dans `~/.virtualdiapo` (`virtualdiapo.db` et le dossier
`images`). Pour importer une collection, voir [le contrat HTTP](docs/api-v1.md#importer-une-collection-jpeg).

Sur macOS, l’adresse Wi-Fi est généralement donnée par :

```shell
ipconfig getifaddr en0
```

Le Mac et la TV doivent être sur le même réseau. macOS peut demander l’autorisation d’accepter les connexions entrantes lors du premier lancement.

## Gérer les collections avec l’interface graphique

Arrêter d’abord toute instance du serveur déjà active, puis depuis `desktop` :

```shell
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn install
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl virtualdiapo-desktop javafx:run
```

La fenêtre démarre également le serveur. Elle permet de :

1. créer une collection en renseignant son titre, sa description et son année ;
2. sélectionner plusieurs JPEG ;
3. prévisualiser chaque image ;
4. modifier leur ordre avec « Monter » et « Descendre » ;
5. sélectionner une collection existante pour modifier ses informations ou la supprimer ;
6. enregistrer : le résultat est immédiatement disponible sur la TV.

## Compiler et installer le Player

Ouvrir `player-android` dans Android Studio, ou utiliser le wrapper :

```shell
cd player-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Dans le Player :

1. conserver `10.0.2.2:8080` dans l’émulateur Android, ou saisir l’adresse du Mac sur une vraie TV ;
2. choisir « Charger le projecteur » ;
3. choisir une collection dans la liste ;
4. utiliser droite ou validation pour avancer ;
5. utiliser gauche pour revenir ;
6. utiliser retour pour revenir à la liste des collections.

Le changement applique 180 ms de noir, joue un claquement mécanique local, puis révèle l’image préchargée. Les diapositives précédente, courante et suivante sont maintenues dans le cache du Player.

## API de démonstration

- `GET /api/v1/server`
- `GET /api/v1/collections`
- `GET /api/v1/collections/{id}`
- `POST /api/v1/collections`
- `PUT /api/v1/collections/{id}`
- `DELETE /api/v1/collections/{id}`
- `GET /api/v1/images/{id}.jpg`

## Tests

```shell
cd desktop
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test

cd ../player-android
./gradlew test
```
