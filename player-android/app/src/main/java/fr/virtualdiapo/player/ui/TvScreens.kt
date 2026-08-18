package fr.virtualdiapo.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.virtualdiapo.player.R
import fr.virtualdiapo.player.model.CollectionSummary
import fr.virtualdiapo.player.ui.theme.VirtualDiapoColors
import kotlinx.coroutines.delay

private const val DISCOVERY_TIMEOUT_MS = 10_000L
private val CarouselLabelFont = FontFamily(Font(R.font.courier_prime_regular, FontWeight.Normal))

@Composable
fun SplashScreen() {
    CinematicBackground {
        BrandBlock(symbolWidth = 190.dp)
    }
}

@Composable
fun DiscoveryScreen(
    connecting: Boolean,
    unavailable: Boolean,
    retryKey: Int,
    onTimeout: () -> Unit,
) {
    LaunchedEffect(retryKey, unavailable) {
        if (!connecting && !unavailable) {
            delay(DISCOVERY_TIMEOUT_MS)
            onTimeout()
        }
    }
    CinematicBackground {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandSymbol(150.dp)
            Text(
                if (connecting) "Connexion en cours…" else "Recherche du serveur…",
                color = VirtualDiapoColors.Cream,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                color = VirtualDiapoColors.Amber,
                trackColor = VirtualDiapoColors.WarmSlate,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(30.dp))
            ProgressSteps(if (connecting) 1 else 0)
        }
    }
}

@Composable
fun DiscoveryFailureScreen(
    message: String?,
    initialAddress: String,
    onRetry: () -> Unit,
    onConnect: (String) -> Unit,
) {
    var manual by rememberSaveable { mutableStateOf(false) }
    var address by remember(initialAddress) { mutableStateOf(initialAddress) }
    CinematicBackground {
        Column(
            modifier = Modifier.fillMaxWidth(0.62f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("!", color = VirtualDiapoColors.Amber, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(
                "Serveur VirtualDiapo introuvable",
                color = VirtualDiapoColors.Cream,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Vérifiez que VirtualDiapo est ouvert\nsur votre ordinateur.",
                color = VirtualDiapoColors.Cream.copy(alpha = .82f),
                fontSize = 18.sp,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    message,
                    color = VirtualDiapoColors.Error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            TvPrimaryButton("Réessayer", onRetry)
            Spacer(Modifier.height(12.dp))
            TvSecondaryButton(if (manual) "Masquer la connexion manuelle" else "Connexion manuelle") {
                manual = !manual
            }
            if (manual) {
                Spacer(Modifier.height(20.dp))
                Text("Adresse du serveur", color = VirtualDiapoColors.Cream, fontSize = 16.sp)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(.72f).padding(top = 8.dp),
                )
                Spacer(Modifier.height(14.dp))
                TvPrimaryButton("Se connecter") { onConnect(address) }
            }
        }
    }
}

@Composable
fun CarouselHomeScreen(
    collections: List<CollectionSummary>,
    onOpen: (CollectionSummary) -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    selectedIndex = selectedIndex.coerceIn(collections.indices)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VirtualDiapoColors.DeepBlack)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                    Key.DirectionRight -> selectedIndex = (selectedIndex + 1).coerceAtMost(collections.lastIndex)
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> onOpen(collections[selectedIndex])
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        Image(
            painterResource(R.drawable.tv_carousel_stage_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "Mes carrousels",
            color = VirtualDiapoColors.Cream,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * .055f),
        )
        RemoteInstructions(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * CarouselHomeLayout.INSTRUCTIONS_TOP_FRACTION),
        )
        val sideWidth = (maxWidth * CarouselHomeLayout.SIDE_WIDTH_FRACTION)
            .coerceIn(CarouselHomeLayout.MIN_SIDE_WIDTH, CarouselHomeLayout.MAX_SIDE_WIDTH)
        val centerWidth = sideWidth * CarouselHomeLayout.CENTER_SCALE
        val gap = (maxWidth * CarouselHomeLayout.GAP_FRACTION).coerceAtMost(CarouselHomeLayout.MAX_GAP)
        Row(
            modifier = Modifier
                .fillMaxWidth(CarouselHomeLayout.SAFE_WIDTH_FRACTION)
                .align(Alignment.Center)
                .offset(y = CarouselHomeLayout.GROUP_OFFSET_Y),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            collections.getOrNull(selectedIndex - 1)?.let {
                CarouselDisplay(it, CarouselPosition.LEFT, sideWidth, Modifier.offset(y = CarouselHomeLayout.sideOffset(sideWidth)))
            } ?: Spacer(Modifier.width(sideWidth))
            CarouselDisplay(
                collections[selectedIndex],
                CarouselPosition.FRONT,
                centerWidth,
                Modifier
                    .offset(y = CarouselHomeLayout.CENTER_OFFSET_Y)
                    .clickable { onOpen(collections[selectedIndex]) },
            )
            collections.getOrNull(selectedIndex + 1)?.let {
                CarouselDisplay(it, CarouselPosition.RIGHT, sideWidth, Modifier.offset(y = CarouselHomeLayout.sideOffset(sideWidth)))
            } ?: Spacer(Modifier.width(sideWidth))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun RemoteInstructions(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteKeySymbol("←")
            RemoteKeySymbol("→")
            Text("Choisir", color = VirtualDiapoColors.Cream.copy(alpha = .66f), fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteKeySymbol("OK", wide = true)
            Text("Projeter", color = VirtualDiapoColors.Cream.copy(alpha = .66f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun RemoteKeySymbol(label: String, wide: Boolean = false) {
    Box(
        modifier = Modifier
            .width(if (wide) 31.dp else 24.dp)
            .height(24.dp)
            .border(1.dp, VirtualDiapoColors.Champagne.copy(alpha = .58f), RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = .26f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = VirtualDiapoColors.Cream.copy(alpha = .76f),
            fontSize = if (wide) 10.sp else 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CarouselDisplay(
    collection: CollectionSummary,
    position: CarouselPosition,
    width: Dp,
    modifier: Modifier,
) {
    val selected = position == CarouselPosition.FRONT
    val label = position.labelTransform
    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(CarouselHomeLayout.CAROUSEL_ASPECT_RATIO),
    ) {
        collection.coverImageUrl?.let { cover ->
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = width * CarouselHomeLayout.PHOTO_OFFSET_Y)
                    .width(width * CarouselHomeLayout.PHOTO_WIDTH_FRACTION)
                    .aspectRatio(CarouselHomeLayout.PHOTO_ASPECT_RATIO)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
        Image(
            painterResource(R.drawable.tv_carousel_body_front),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        val titleTypography = position.titleTypography(collection.title.length)
        Text(
            text = collection.title,
            color = VirtualDiapoColors.DeepBlack,
            fontSize = titleTypography.fontSize,
            lineHeight = titleTypography.lineHeight,
            fontFamily = CarouselLabelFont,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = titleTypography.maxLines,
            softWrap = titleTypography.maxLines > 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = width * label.offsetX, y = width * label.offsetY)
                .width(width * label.width)
                .graphicsLayer {
                    rotationZ = label.rotationZ
                },
        )
        Text(
            text = "${collection.slideCount} diapo${if (collection.slideCount > 1) "s" else ""}",
            color = Color(0xFF21170F),
            fontSize = if (selected) 15.sp else 12.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(Color.Black.copy(alpha = .9f), Offset(1.5f, 1.5f), 3f),
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = if (selected) 20.dp else 14.dp)
                .fillMaxWidth(.55f),
        )
    }
}

private data class CarouselLabelTransform(
    val width: Float,
    val offsetX: Float,
    val offsetY: Float,
    val rotationZ: Float,
)

private data class CarouselTitleTypography(
    val fontSize: androidx.compose.ui.unit.TextUnit,
    val lineHeight: androidx.compose.ui.unit.TextUnit,
    val maxLines: Int,
)

private enum class CarouselPosition(val labelTransform: CarouselLabelTransform) {
    LEFT(CarouselLabelTransform(.43f, 0f, .455f, 1.2f)),
    FRONT(CarouselLabelTransform(.43f, 0f, .455f, 0f)),
    RIGHT(CarouselLabelTransform(.43f, 0f, .455f, -1.2f)),
    ;

    fun titleTypography(length: Int) = when (this) {
        FRONT -> when {
            length <= 16 -> CarouselTitleTypography(14.sp, 15.sp, 1)
            length <= 24 -> CarouselTitleTypography(12.sp, 13.sp, 1)
            length <= 40 -> CarouselTitleTypography(9.sp, 10.sp, 2)
            else -> CarouselTitleTypography(8.sp, 9.sp, 2)
        }
        LEFT, RIGHT -> when {
            length <= 15 -> CarouselTitleTypography(12.sp, 13.sp, 1)
            length <= 22 -> CarouselTitleTypography(10.sp, 11.sp, 1)
            length <= 40 -> CarouselTitleTypography(8.sp, 9.sp, 2)
            else -> CarouselTitleTypography(7.sp, 8.sp, 2)
        }
    }
}

private object CarouselHomeLayout {
    const val CAROUSEL_ASPECT_RATIO = 1.5f
    const val SAFE_WIDTH_FRACTION = .92f
    const val SIDE_WIDTH_FRACTION = .255f
    const val CENTER_SCALE = 1.15f
    const val GAP_FRACTION = .012f
    const val PHOTO_WIDTH_FRACTION = .188f
    const val PHOTO_OFFSET_Y = .055f
    const val PHOTO_ASPECT_RATIO = 1.09f
    const val INSTRUCTIONS_TOP_FRACTION = .15f
    val MIN_SIDE_WIDTH = 240.dp
    val MAX_SIDE_WIDTH = 500.dp
    val MAX_GAP = 24.dp
    val GROUP_OFFSET_Y = 93.dp
    val CENTER_OFFSET_Y = 34.dp
    val CENTER_PERSPECTIVE_DROP = 44.dp

    fun sideOffset(sideWidth: Dp): Dp = CENTER_OFFSET_Y +
        sideWidth * ((CENTER_SCALE - 1f) / (2f * CAROUSEL_ASPECT_RATIO)) -
        CENTER_PERSPECTIVE_DROP
}

@Composable
fun CarouselLoadingScreen(title: String) {
    CinematicBackground {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandSymbol(140.dp)
            Text(
                "Préparation du carrousel",
                color = VirtualDiapoColors.Cream,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(title, color = VirtualDiapoColors.Champagne, fontSize = 19.sp, modifier = Modifier.padding(top = 8.dp))
            CircularProgressIndicator(
                color = VirtualDiapoColors.Amber,
                trackColor = VirtualDiapoColors.WarmSlate,
                modifier = Modifier.padding(top = 28.dp).size(44.dp),
            )
        }
    }
}

@Composable
private fun CinematicBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(VirtualDiapoColors.DeepBlack)) {
        Image(
            painterResource(R.drawable.tv_cinematic_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)))
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = { content() })
    }
}

@Composable
private fun BrandBlock(symbolWidth: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BrandSymbol(symbolWidth)
        Text(
            "VIRTUALDIAPO",
            color = VirtualDiapoColors.Cream,
            fontSize = 38.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 6.sp,
        )
        Text(
            "Les souvenirs se partagent ensemble.",
            color = VirtualDiapoColors.Cream.copy(alpha = .82f),
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun BrandSymbol(width: Dp) {
    AsyncImage(
        model = R.raw.virtualdiapo_symbol_light,
        contentDescription = "VirtualDiapo",
        modifier = Modifier.width(width).aspectRatio(220f / 150f),
    )
}

@Composable
private fun ProgressSteps(active: Int) {
    val labels = listOf("Recherche du serveur", "Connexion", "Préparation")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(
                            if (index <= active) VirtualDiapoColors.Amber else VirtualDiapoColors.WarmSlate,
                            RoundedCornerShape(50),
                        ),
                )
                Text(label, color = VirtualDiapoColors.Cream.copy(alpha = if (index <= active) 1f else .5f), modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun TvPrimaryButton(label: String, onClick: () -> Unit) {
    FocusAwareButton(label, true, onClick)
}

@Composable
private fun TvSecondaryButton(label: String, onClick: () -> Unit) {
    FocusAwareButton(label, false, onClick)
}

@Composable
private fun FocusAwareButton(label: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = VirtualDiapoColors.Champagne, contentColor = VirtualDiapoColors.DeepBlack),
            modifier = Modifier
                .width(280.dp)
                .onFocusChanged { focused = it.isFocused }
                .border(if (focused) 4.dp else 0.dp, VirtualDiapoColors.Cream, shape),
        ) { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VirtualDiapoColors.Cream),
            modifier = Modifier
                .width(280.dp)
                .onFocusChanged { focused = it.isFocused }
                .border(if (focused) 4.dp else 0.dp, VirtualDiapoColors.Amber, shape),
        ) { Text(label, fontSize = 16.sp) }
    }
}
