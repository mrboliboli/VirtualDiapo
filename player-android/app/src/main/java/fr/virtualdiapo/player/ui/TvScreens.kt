package fr.virtualdiapo.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
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
import androidx.compose.ui.text.input.ImeAction
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
import fr.virtualdiapo.player.projection.ProjectionOptions
import fr.virtualdiapo.player.projection.ProjectionPreferences
import fr.virtualdiapo.player.projection.AutoAdvancePolicy
import fr.virtualdiapo.player.network.DiscoveredServer
import fr.virtualdiapo.player.network.DiscoveryStatus
import fr.virtualdiapo.player.network.ServerAddressValidator
import fr.virtualdiapo.player.network.ServerConfiguration
import fr.virtualdiapo.player.network.ServerMode
import fr.virtualdiapo.player.network.ServerPreferences
import fr.virtualdiapo.player.network.VirtualDiapoApiClient
import fr.virtualdiapo.player.network.AvailabilityRequestPolicy
import fr.virtualdiapo.player.network.AvailabilityRequestSnapshot
import fr.virtualdiapo.player.ui.theme.VirtualDiapoColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope

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
    activeAddress: String,
    servers: List<DiscoveredServer>,
    discoveryStatus: DiscoveryStatus,
    onConfigurationChanged: (ServerConfiguration) -> Unit,
    onServerConnected: (String) -> Unit,
    onOpen: (CollectionSummary) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { ProjectionPreferences(context) }
    val serverPreferences = remember { ServerPreferences(context) }
    val apiClient = remember { VirtualDiapoApiClient() }
    val scope = rememberCoroutineScope()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusZone by rememberSaveable { mutableStateOf(CarouselFocusZone.CAROUSEL) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var selectedSetting by rememberSaveable { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(preferences.load()) }
    var serverConfiguration by remember { mutableStateOf(serverPreferences.load()) }
    var manualAddress by remember { mutableStateOf(serverConfiguration.manualAddress) }
    var availability by remember { mutableStateOf(ServerAvailability.NOT_TESTED) }
    var availabilityJob by remember { mutableStateOf<Job?>(null) }
    var availabilityGeneration by remember { mutableLongStateOf(0L) }
    var manualEditing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun updateOptions(updated: ProjectionOptions) {
        options = updated
        preferences.save(updated)
    }
    fun updateServerConfiguration(updated: ServerConfiguration) {
        serverConfiguration = updated
        serverPreferences.save(updated)
        onConfigurationChanged(updated)
    }
    fun invalidateAvailability() {
        availabilityGeneration++
        availabilityJob?.cancel()
        availabilityJob = null
    }
    fun effectiveMdnsTarget(): String? =
        activeAddress.takeIf { address -> servers.any { it.address == address } }
            ?: servers.firstOrNull()?.address
    fun checkAddress(address: String, mode: ServerMode, reconnect: Boolean) {
        invalidateAvailability()
        val snapshot = AvailabilityRequestSnapshot(availabilityGeneration, mode, address)
        availability = ServerAvailability.CHECKING
        availabilityJob = scope.launch {
            try {
                apiClient.checkServer(address)
                val currentTarget = when (mode) {
                    ServerMode.MDNS -> effectiveMdnsTarget()
                    ServerMode.MANUAL -> ServerAddressValidator.normalize(manualAddress)
                }
                if (!AvailabilityRequestPolicy.isCurrent(
                        snapshot,
                        availabilityGeneration,
                        serverConfiguration.mode,
                        currentTarget,
                    )) return@launch
                availability = ServerAvailability.AVAILABLE
                if (reconnect) onServerConnected(address)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (!AvailabilityRequestPolicy.isCurrent(
                        snapshot,
                        availabilityGeneration,
                        serverConfiguration.mode,
                        when (mode) {
                            ServerMode.MDNS -> effectiveMdnsTarget()
                            ServerMode.MANUAL -> ServerAddressValidator.normalize(manualAddress)
                        },
                    )) return@launch
                availability = ServerAvailability.UNAVAILABLE
            }
        }
    }
    fun switchServerMode(target: ServerMode) {
        invalidateAvailability()
        updateServerConfiguration(serverConfiguration.copy(mode = target))
        val targetAddress = when (target) {
            ServerMode.MDNS -> effectiveMdnsTarget()
            ServerMode.MANUAL -> ServerAddressValidator.normalize(manualAddress)
        }
        if (targetAddress == null) {
            availability = ServerAvailability.NOT_TESTED
            return
        }
        checkAddress(targetAddress, mode = target, reconnect = true)
    }
    fun saveAndTestManualAddress() {
        val normalized = ServerAddressValidator.normalize(manualAddress) ?: return
        manualAddress = normalized
        updateServerConfiguration(
            serverConfiguration.copy(mode = ServerMode.MANUAL, manualAddress = normalized),
        )
        manualEditing = false
        focus.requestFocus()
        checkAddress(normalized, mode = ServerMode.MANUAL, reconnect = true)
    }
    selectedIndex = selectedIndex.coerceIn(collections.indices)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VirtualDiapoColors.DeepBlack)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                if (settingsVisible) {
                    if (manualEditing) {
                        if (event.key == Key.Back) {
                            manualEditing = false
                            focus.requestFocus()
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    val focusLayout = SettingsFocusLayout(
                        autoAdvanceEnabled = options.autoAdvanceEnabled,
                        serverMode = serverConfiguration.mode,
                    )
                    when (event.key) {
                        Key.DirectionUp -> selectedSetting = (selectedSetting - 1).coerceAtLeast(0)
                        Key.DirectionDown -> selectedSetting =
                            (selectedSetting + 1).coerceAtMost(focusLayout.maximumIndex)
                        Key.DirectionLeft, Key.DirectionRight -> when (selectedSetting) {
                            focusLayout.durationIndex -> {
                                val delta = if (event.key == Key.DirectionLeft) -1 else 1
                                updateOptions(
                                    options.copy(
                                        autoAdvanceDelaySeconds = AutoAdvancePolicy.adjustDelay(
                                            options.autoAdvanceDelaySeconds,
                                            delta,
                                        ),
                                    ),
                                )
                            }
                            focusLayout.serverModeIndex -> {
                                val mode = if (serverConfiguration.mode == ServerMode.MDNS) ServerMode.MANUAL else ServerMode.MDNS
                                switchServerMode(mode)
                            }
                        }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            when (selectedSetting) {
                                0 -> updateOptions(options.copy(soundEnabled = !options.soundEnabled))
                                1 -> updateOptions(options.copy(fadeEnabled = !options.fadeEnabled))
                                focusLayout.autoAdvanceIndex -> updateOptions(
                                    options.copy(autoAdvanceEnabled = !options.autoAdvanceEnabled),
                                )
                                focusLayout.serverModeIndex -> {
                                    val mode = if (serverConfiguration.mode == ServerMode.MDNS) ServerMode.MANUAL else ServerMode.MDNS
                                    switchServerMode(mode)
                                }
                                focusLayout.manualAddressIndex -> manualEditing = true
                                focusLayout.manualTestIndex -> saveAndTestManualAddress()
                            }
                        }
                        Key.Back -> {
                            invalidateAvailability()
                            settingsVisible = false
                        }
                        else -> Unit
                    }
                    return@onPreviewKeyEvent true
                }
                if (focusZone == CarouselFocusZone.SETTINGS) {
                    when (event.key) {
                        Key.DirectionDown -> focusZone = moveCarouselFocus(
                            focusZone,
                            CarouselFocusDirection.DOWN,
                        )
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            selectedSetting = 0
                            settingsVisible = true
                        }
                        Key.Back -> return@onPreviewKeyEvent false
                        else -> Unit
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.DirectionLeft -> selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                    Key.DirectionRight -> selectedIndex = (selectedIndex + 1).coerceAtMost(collections.lastIndex)
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> onOpen(collections[selectedIndex])
                    Key.DirectionUp -> focusZone = moveCarouselFocus(
                        focusZone,
                        CarouselFocusDirection.UP,
                    )
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (settingsVisible) Modifier.clearAndSetSemantics { }
                    else Modifier,
                ),
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
            modifier = Modifier.align(Alignment.TopCenter).padding(top = viewportHeight * .055f),
        )
        SettingsButton(
            focused = focusZone == CarouselFocusZone.SETTINGS,
            onClick = {
                focusZone = CarouselFocusZone.SETTINGS
                selectedSetting = 0
                settingsVisible = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    end = maxOf(48.dp, viewportWidth * .05f),
                    top = maxOf(32.dp, viewportHeight * .045f),
                ),
        )
        RemoteInstructions(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = viewportHeight * CarouselHomeLayout.INSTRUCTIONS_TOP_FRACTION),
        )
        val sideWidth = (viewportWidth * CarouselHomeLayout.SIDE_WIDTH_FRACTION)
            .coerceIn(CarouselHomeLayout.MIN_SIDE_WIDTH, CarouselHomeLayout.MAX_SIDE_WIDTH)
        val centerWidth = sideWidth * CarouselHomeLayout.CENTER_SCALE
        val gap = (viewportWidth * CarouselHomeLayout.GAP_FRACTION).coerceAtMost(CarouselHomeLayout.MAX_GAP)
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
        if (settingsVisible) {
            ProjectionSettingsScreen(
                soundEnabled = options.soundEnabled,
                fadeEnabled = options.fadeEnabled,
                autoAdvanceEnabled = options.autoAdvanceEnabled,
                autoAdvanceDelaySeconds = options.autoAdvanceDelaySeconds,
                selectedIndex = selectedSetting,
                serverMode = serverConfiguration.mode,
                manualAddress = manualAddress,
                detectedAddress = effectiveMdnsTarget(),
                discoveryStatus = discoveryStatus,
                availability = availability,
                manualEditing = manualEditing,
                onManualEditingChanged = { manualEditing = it },
                onManualDone = ::saveAndTestManualAddress,
                onManualAddressChanged = {
                    invalidateAvailability()
                    manualAddress = it
                    availability = ServerAvailability.NOT_TESTED
                },
                onSoundChanged = { updateOptions(options.copy(soundEnabled = it)) },
                onFadeChanged = { updateOptions(options.copy(fadeEnabled = it)) },
                onAutoAdvanceChanged = {
                    updateOptions(options.copy(autoAdvanceEnabled = it))
                    if (!it) selectedSetting = SettingsFocusLayout(
                        autoAdvanceEnabled = false,
                        serverMode = serverConfiguration.mode,
                    ).autoAdvanceIndex
                },
                onAutoAdvanceDelayChanged = {
                    updateOptions(options.copy(autoAdvanceDelaySeconds = it))
                },
            )
        }
    }
    BackHandler(enabled = settingsVisible) {
        if (manualEditing) {
            manualEditing = false
            focus.requestFocus()
        } else {
            invalidateAvailability()
            settingsVisible = false
        }
    }
    LaunchedEffect(settingsVisible, activeAddress, servers, discoveryStatus) {
        val detected = effectiveMdnsTarget()
        if (settingsVisible && serverConfiguration.mode == ServerMode.MDNS && detected != null) {
            checkAddress(detected, mode = ServerMode.MDNS, reconnect = false)
        }
    }
    LaunchedEffect(Unit) {
        focusZone = CarouselFocusZone.CAROUSEL
        focus.requestFocus()
    }
}

@Composable
private fun SettingsButton(
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "Focus réglages",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Réglages de projection"
                role = Role.Button
                onClick(label = "Ouvrir les réglages de projection") {
                    onClick()
                    true
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                if (focused) Color(0xFF15171A).copy(alpha = .88f) else Color.Black.copy(alpha = .28f),
                RoundedCornerShape(10.dp),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) VirtualDiapoColors.Amber else VirtualDiapoColors.Champagne.copy(alpha = .28f),
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SettingsIcon,
            contentDescription = null,
            tint = VirtualDiapoColors.Cream.copy(alpha = if (focused) 1f else .72f),
            modifier = Modifier.size(22.dp),
        )
    }
}

private val SettingsIcon: ImageVector by lazy {
    ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).apply {
        group(translationX = -1f) {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19.43f, 12.98f)
            curveTo(19.47f, 12.66f, 19.5f, 12.34f, 19.5f, 12f)
            curveTo(19.5f, 11.66f, 19.47f, 11.33f, 19.42f, 11.02f)
            lineTo(21.54f, 9.37f); lineTo(19.54f, 5.91f); lineTo(17.05f, 6.91f)
            curveTo(16.54f, 6.51f, 15.98f, 6.18f, 15.37f, 5.93f)
            lineTo(15f, 3.27f); lineTo(11f, 3.27f); lineTo(10.63f, 5.93f)
            curveTo(10.02f, 6.18f, 9.46f, 6.51f, 8.95f, 6.91f)
            lineTo(6.46f, 5.91f); lineTo(4.46f, 9.37f); lineTo(6.58f, 11.02f)
            curveTo(6.53f, 11.33f, 6.5f, 11.66f, 6.5f, 12f)
            curveTo(6.5f, 12.34f, 6.53f, 12.66f, 6.58f, 12.98f)
            lineTo(4.46f, 14.63f); lineTo(6.46f, 18.09f); lineTo(8.95f, 17.09f)
            curveTo(9.46f, 17.49f, 10.02f, 17.82f, 10.63f, 18.07f)
            lineTo(11f, 20.73f); lineTo(15f, 20.73f); lineTo(15.37f, 18.07f)
            curveTo(15.98f, 17.82f, 16.54f, 17.49f, 17.05f, 17.09f)
            lineTo(19.54f, 18.09f); lineTo(21.54f, 14.63f); close()
            moveTo(13f, 15.5f)
            curveTo(11.07f, 15.5f, 9.5f, 13.93f, 9.5f, 12f)
            curveTo(9.5f, 10.07f, 11.07f, 8.5f, 13f, 8.5f)
            curveTo(14.93f, 8.5f, 16.5f, 10.07f, 16.5f, 12f)
            curveTo(16.5f, 13.93f, 14.93f, 15.5f, 13f, 15.5f); close()
        }
        }
    }.build()
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

enum class ServerAvailability { NOT_TESTED, CHECKING, AVAILABLE, UNAVAILABLE }

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
fun ProjectionSettingsScreen(
    soundEnabled: Boolean,
    fadeEnabled: Boolean,
    autoAdvanceEnabled: Boolean,
    autoAdvanceDelaySeconds: Int,
    selectedIndex: Int,
    serverMode: ServerMode,
    manualAddress: String,
    detectedAddress: String?,
    discoveryStatus: DiscoveryStatus,
    availability: ServerAvailability,
    manualEditing: Boolean,
    onManualEditingChanged: (Boolean) -> Unit,
    onManualDone: () -> Unit,
    onManualAddressChanged: (String) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onFadeChanged: (Boolean) -> Unit,
    onAutoAdvanceChanged: (Boolean) -> Unit,
    onAutoAdvanceDelayChanged: (Int) -> Unit,
) {
    val focusLayout = SettingsFocusLayout(autoAdvanceEnabled, serverMode)
    val manualFieldFocus = remember { FocusRequester() }
    val itemBringRequesters = remember { List(7) { BringIntoViewRequester() } }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(serverMode, autoAdvanceEnabled, selectedIndex, manualEditing) {
        itemBringRequesters.getOrNull(selectedIndex)?.bringIntoView()
        if (selectedIndex == focusLayout.manualAddressIndex && manualEditing) {
            manualFieldFocus.requestFocus()
            keyboard?.show()
        } else if (!manualEditing) {
            keyboard?.hide()
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .58f)),
        contentAlignment = Alignment.Center,
    ) {
        val cardWidth = (maxWidth * .58f).coerceAtMost(840.dp)
        val safeInset = if (maxHeight >= 720.dp) 32.dp else 24.dp
        Column(
            modifier = Modifier
                .width(cardWidth)
                .heightIn(max = maxHeight - safeInset * 2)
                .background(Color(0xFF101216).copy(alpha = .96f), RoundedCornerShape(14.dp))
                .border(1.dp, VirtualDiapoColors.WarmSlate, RoundedCornerShape(14.dp))
                .padding(horizontal = 30.dp, vertical = 24.dp),
        ) {
            Text(
                "Réglages",
                color = VirtualDiapoColors.Cream,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = false,
                    ),
            ) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("PROJECTION")
            Spacer(Modifier.height(10.dp))
            Box(Modifier.bringIntoViewRequester(itemBringRequesters[0])) {
                ProjectionSettingRow(
                    label = "Son de transition",
                    checked = soundEnabled,
                    selected = selectedIndex == 0,
                    onChanged = onSoundChanged,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.bringIntoViewRequester(itemBringRequesters[1])) {
                ProjectionSettingRow(
                    label = "Fondu entre les diapositives",
                    checked = fadeEnabled,
                    selected = selectedIndex == 1,
                    onChanged = onFadeChanged,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.bringIntoViewRequester(itemBringRequesters[focusLayout.autoAdvanceIndex])) {
                ProjectionSettingRow(
                    label = "Avance automatique",
                    checked = autoAdvanceEnabled,
                    selected = selectedIndex == focusLayout.autoAdvanceIndex,
                    onChanged = onAutoAdvanceChanged,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.then(
                    focusLayout.durationIndex?.let { Modifier.bringIntoViewRequester(itemBringRequesters[it]) }
                        ?: Modifier,
                ),
            ) {
                AutoAdvanceDurationRow(
                    seconds = autoAdvanceDelaySeconds,
                    enabled = autoAdvanceEnabled,
                    selected = selectedIndex == focusLayout.durationIndex,
                    onDelayChanged = onAutoAdvanceDelayChanged,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(VirtualDiapoColors.WarmSlate.copy(alpha = .70f)))
            Spacer(Modifier.height(18.dp))
            SectionLabel("SERVEUR")
            Spacer(Modifier.height(10.dp))
            Box(Modifier.bringIntoViewRequester(itemBringRequesters[focusLayout.serverModeIndex])) {
                ServerModeControl(serverMode = serverMode, focused = selectedIndex == focusLayout.serverModeIndex)
            }
            Spacer(Modifier.height(12.dp))
            if (serverMode == ServerMode.MDNS) {
                ReadOnlyServerAddress(
                    value = detectedAddress ?: when (discoveryStatus) {
                        DiscoveryStatus.SEARCHING -> "Recherche automatique…"
                        DiscoveryStatus.STOPPED -> "Découverte arrêtée"
                        else -> "Aucune adresse détectée"
                    },
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .bringIntoViewRequester(itemBringRequesters[focusLayout.manualAddressIndex!!]),
                ) {
                    OutlinedTextField(
                        value = manualAddress,
                        onValueChange = onManualAddressChanged,
                        label = { Text("Adresse du serveur") },
                        placeholder = { Text("192.168.1.20:8080") },
                        singleLine = true,
                        enabled = true,
                        readOnly = !manualEditing,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboard?.hide()
                                focusManager.clearFocus(force = true)
                                onManualEditingChanged(false)
                                onManualDone()
                            },
                        ),
                        shape = RoundedCornerShape(9.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = .20f),
                            unfocusedContainerColor = Color.Black.copy(alpha = .20f),
                            focusedBorderColor = VirtualDiapoColors.Amber,
                            unfocusedBorderColor = VirtualDiapoColors.WarmSlate,
                            cursorColor = VirtualDiapoColors.Amber,
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(manualFieldFocus),
                    )
                    if (selectedIndex == focusLayout.manualAddressIndex && !manualEditing) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .border(3.dp, VirtualDiapoColors.Amber, RoundedCornerShape(9.dp)),
                        )
                    }
                }
                val syntaxInvalid = manualAddress.isNotBlank() && ServerAddressValidator.normalize(manualAddress) == null
                if (syntaxInvalid) {
                    Text(
                        "! Adresse invalide — exemple : 192.168.1.20:8080",
                        color = VirtualDiapoColors.Error,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.bringIntoViewRequester(itemBringRequesters[focusLayout.manualTestIndex!!])) {
                    ManualTestButton(
                        enabled = ServerAddressValidator.normalize(manualAddress) != null,
                        focused = selectedIndex == focusLayout.manualTestIndex,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val syntaxInvalid = serverMode == ServerMode.MANUAL &&
                manualAddress.isNotBlank() &&
                ServerAddressValidator.normalize(manualAddress) == null
            if (!syntaxInvalid) {
                AvailabilityText(
                    serverMode = serverMode,
                    discoveryStatus = discoveryStatus,
                    hasDetectedAddress = detectedAddress != null,
                    availability = availability,
                )
            }
            Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(
                if (serverMode == ServerMode.MANUAL) {
                    "↑ ↓ choisir   •   ← → régler   •   OK modifier / tester   •   Retour fermer"
                } else {
                    "↑ ↓ choisir   •   ← → régler   •   OK modifier   •   Retour fermer"
                },
                color = VirtualDiapoColors.Cream.copy(alpha = .66f),
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = VirtualDiapoColors.Champagne.copy(alpha = .72f), fontSize = 14.sp)
}

@Composable
private fun ServerModeControl(serverMode: ServerMode, focused: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(if (focused) 3.dp else 1.dp, if (focused) VirtualDiapoColors.Amber else VirtualDiapoColors.WarmSlate, RoundedCornerShape(9.dp)),
    ) {
        listOf(ServerMode.MDNS to "Automatique (mDNS)", ServerMode.MANUAL to "Manuel").forEach { (mode, label) ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(if (serverMode == mode) Color(0xFFD5B078) else Color.Transparent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (serverMode == mode) Color(0xFF111315) else VirtualDiapoColors.Cream.copy(alpha = .72f),
                    fontSize = 16.sp,
                    fontWeight = if (serverMode == mode) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyServerAddress(value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.Black.copy(alpha = .22f), RoundedCornerShape(8.dp))
            .border(1.dp, VirtualDiapoColors.WarmSlate.copy(alpha = .55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text("Adresse détectée", color = VirtualDiapoColors.Cream.copy(alpha = .52f), fontSize = 12.sp)
        Text(value, color = VirtualDiapoColors.Cream.copy(alpha = .58f), fontSize = 17.sp)
    }
}

@Composable
private fun ManualTestButton(enabled: Boolean, focused: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else .38f }
            .background(
                if (focused) Color(0xFF15171A).copy(alpha = .92f) else Color.Black.copy(alpha = .22f),
                RoundedCornerShape(9.dp),
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) VirtualDiapoColors.Amber else VirtualDiapoColors.Champagne.copy(alpha = .55f),
                RoundedCornerShape(9.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("Enregistrer et tester", color = VirtualDiapoColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AvailabilityText(
    serverMode: ServerMode,
    discoveryStatus: DiscoveryStatus,
    hasDetectedAddress: Boolean,
    availability: ServerAvailability,
) {
    val (text, color) = when {
        availability == ServerAvailability.CHECKING -> "◌ Vérification…" to VirtualDiapoColors.Champagne
        availability == ServerAvailability.AVAILABLE -> "✓ Disponible" to Color(0xFF69C776)
        availability == ServerAvailability.UNAVAILABLE && hasDetectedAddress -> "! Détecté mais indisponible" to VirtualDiapoColors.Amber
        availability == ServerAvailability.UNAVAILABLE -> "! Serveur indisponible" to VirtualDiapoColors.Error
        serverMode == ServerMode.MANUAL -> "○ Serveur non testé" to VirtualDiapoColors.Cream.copy(alpha = .66f)
        discoveryStatus == DiscoveryStatus.SEARCHING -> "◌ Recherche automatique…" to VirtualDiapoColors.Champagne
        discoveryStatus == DiscoveryStatus.UNAVAILABLE -> "! Découverte indisponible" to VirtualDiapoColors.Error
        else -> "○ Aucun serveur détecté" to VirtualDiapoColors.Cream.copy(alpha = .66f)
    }
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ProjectionSettingRow(
    label: String,
    checked: Boolean,
    selected: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) VirtualDiapoColors.Amber else VirtualDiapoColors.WarmSlate,
                shape = RoundedCornerShape(10.dp),
            )
            .background(Color.Black.copy(alpha = .28f), RoundedCornerShape(10.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChanged,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (checked) "Activé" else "Désactivé"
            }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = VirtualDiapoColors.Cream, fontSize = 19.sp)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF080A0C),
                checkedTrackColor = Color(0xFFD5B078),
                uncheckedThumbColor = Color(0xFFF5E7CF).copy(alpha = .82f),
                uncheckedTrackColor = Color(0xFF1A1C20),
                uncheckedBorderColor = Color(0xFF2E3238),
            ),
        )
    }
}

@Composable
private fun AutoAdvanceDurationRow(
    seconds: Int,
    enabled: Boolean,
    selected: Boolean,
    onDelayChanged: (Int) -> Unit,
) {
    val normalizedSeconds = AutoAdvancePolicy.normalizeDelay(seconds)
    val canDecrease = enabled && normalizedSeconds > AutoAdvancePolicy.MIN_DELAY_SECONDS
    val canIncrease = enabled && normalizedSeconds < AutoAdvancePolicy.MAX_DELAY_SECONDS
    val accessibilityActions = buildList {
        if (canDecrease) add(
            CustomAccessibilityAction("Diminuer la durée") {
                onDelayChanged(AutoAdvancePolicy.adjustDelay(normalizedSeconds, -1))
                true
            },
        )
        if (canIncrease) add(
            CustomAccessibilityAction("Augmenter la durée") {
                onDelayChanged(AutoAdvancePolicy.adjustDelay(normalizedSeconds, 1))
                true
            },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else .4f }
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) VirtualDiapoColors.Amber else VirtualDiapoColors.WarmSlate,
                shape = RoundedCornerShape(9.dp),
            )
            .background(Color.Black.copy(alpha = .22f), RoundedCornerShape(9.dp))
            .semantics {
                contentDescription = "Durée d’affichage"
                stateDescription = if (enabled) "$normalizedSeconds secondes" else "Désactivée"
                customActions = accessibilityActions
                if (!enabled) disabled()
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Durée d’affichage", color = VirtualDiapoColors.Cream, fontSize = 17.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "−",
                color = VirtualDiapoColors.Cream.copy(
                    alpha = if (enabled && !canDecrease) .35f else 1f,
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "  $normalizedSeconds s  ",
                color = VirtualDiapoColors.Cream,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "+",
                color = VirtualDiapoColors.Cream.copy(
                    alpha = if (enabled && !canIncrease) .35f else 1f,
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
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
