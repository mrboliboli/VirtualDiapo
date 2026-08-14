package fr.virtualdiapo.player.model

data class SlideCollection(
    val id: String,
    val title: String,
    val description: String?,
    val year: Int?,
    val slides: List<Slide>,
)

data class Slide(
    val id: String,
    val position: Int,
    val imageUrl: String,
)

