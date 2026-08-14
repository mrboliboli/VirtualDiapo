# Contrat HTTP v1 — incrément de démonstration

Toutes les routes sont relatives à l’adresse du serveur. Le Player reconstruit les URL d’images depuis cette adresse et le champ `imagePath`.

## Informations serveur

`GET /api/v1/server`

```json
{
  "name": "VirtualDiapo",
  "apiVersion": 1
}
```

## Collections

`GET /api/v1/collections` retourne une liste de résumés :

```json
[
  {
    "id": "12e7c19d-0200-4a75-b0af-000000000001",
    "title": "Auvergne 2026",
    "description": "Murol — Sancy — Chaudefour",
    "year": 2026,
    "slideCount": 3
  }
]
```

`GET /api/v1/collections/{id}` retourne les diapositives dans leur ordre de projection :

```json
{
  "id": "12e7c19d-0200-4a75-b0af-000000000001",
  "title": "Auvergne 2026",
  "description": "Murol — Sancy — Chaudefour",
  "year": 2026,
  "slides": [
    {
      "id": "12e7c19d-0200-4a75-b0af-000000000101",
      "position": 0,
      "imagePath": "/demo/auvergne-1.svg"
    }
  ]
}
```

Le contrat est volontairement manuel pour cet incrément. Il sera enrichi seulement lorsque les vrais profils d’images et le cache serveur seront introduits.

## Importer une collection JPEG

`POST /api/v1/collections` accepte un formulaire `multipart/form-data` :

- `title` (obligatoire) ;
- `description` (facultatif) ;
- `year` (facultatif) ;
- `images` (une ou plusieurs images JPEG, dans l’ordre de projection).

Exemple :

```shell
curl -X POST http://localhost:8080/api/v1/collections \
  -F 'title=Vacances 2026' \
  -F 'description=Notre séjour' \
  -F 'year=2026' \
  -F 'images=@/chemin/photo-01.jpg' \
  -F 'images=@/chemin/photo-02.jpg'
```

La réponse `201 Created` reprend le format détaillé d’une collection. Les images sont servies par
`GET /api/v1/images/{id}.jpg`.
