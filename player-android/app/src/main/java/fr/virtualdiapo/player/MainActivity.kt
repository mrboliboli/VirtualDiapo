package fr.virtualdiapo.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import fr.virtualdiapo.player.model.SlideCollection
import fr.virtualdiapo.player.network.DiscoveredServer
import fr.virtualdiapo.player.network.DiscoveryStatus
import fr.virtualdiapo.player.network.VirtualDiapoDiscovery
import fr.virtualdiapo.player.projection.MechanicalSoundPlayer
import fr.virtualdiapo.player.projection.PreloadWindow
import fr.virtualdiapo.player.projection.ProjectionOptions
import fr.virtualdiapo.player.projection.ProjectionPreferences
import fr.virtualdiapo.player.projection.ProjectionState
import fr.virtualdiapo.player.projection.ProjectionTransition
import fr.virtualdiapo.player.projection.SlidePreloader
import fr.virtualdiapo.player.projection.SlideTransform
import fr.virtualdiapo.player.ui.CarouselHomeScreen
import fr.virtualdiapo.player.ui.CarouselLoadingScreen
import fr.virtualdiapo.player.ui.DiscoveryFailureScreen
import fr.virtualdiapo.player.ui.DiscoveryScreen
import fr.virtualdiapo.player.ui.ProjectionSettingsScreen
import fr.virtualdiapo.player.ui.SplashScreen
import fr.virtualdiapo.player.ui.theme.VirtualDiapoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SPLASH_DURATION_MS = 1_800L

class MainActivity : ComponentActivity() {
    private lateinit var discovery: VirtualDiapoDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        discovery = VirtualDiapoDiscovery(applicationContext)
        setContent {
            VirtualDiapoTheme {
                val viewModel: MainViewModel = viewModel()
                val servers by discovery.servers.collectAsState()
                val discoveryStatus by discovery.status.collectAsState()
                VirtualDiapoApp(
                    viewModel = viewModel,
                    servers = servers,
                    discoveryStatus = discoveryStatus,
                    retryDiscovery = discovery::start,
                )
            }
        }
    }

    override fun onStart() { super.onStart(); discovery.start() }
    override fun onStop() { discovery.stop(); super.onStop() }
}

@Composable
private fun VirtualDiapoApp(
    viewModel: MainViewModel,
    servers: List<DiscoveredServer>,
    discoveryStatus: DiscoveryStatus,
    retryDiscovery: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var splashFinished by rememberSaveable { mutableStateOf(false) }
    var discoveryTimedOut by rememberSaveable { mutableStateOf(false) }
    var retryKey by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        splashFinished = true
    }
    if (!splashFinished) {
        SplashScreen()
        return
    }

    when (val current = state) {
        PlayerUiState.Setup -> {
            val server = servers.firstOrNull()
            LaunchedEffect(server?.address, retryKey) {
                if (server != null) viewModel.connect(server.address)
            }
            if (discoveryTimedOut || discoveryStatus == DiscoveryStatus.UNAVAILABLE) {
                DiscoveryFailureScreen(
                    message = null,
                    initialAddress = "10.0.2.2:8080",
                    onRetry = {
                        discoveryTimedOut = false
                        retryKey++
                        retryDiscovery()
                    },
                    onConnect = viewModel::connect,
                )
            } else {
                DiscoveryScreen(
                    connecting = server != null,
                    unavailable = discoveryStatus == DiscoveryStatus.UNAVAILABLE,
                    retryKey = retryKey,
                    onTimeout = { discoveryTimedOut = true },
                )
            }
        }
        PlayerUiState.Connecting -> DiscoveryScreen(
            connecting = true,
            unavailable = false,
            retryKey = retryKey,
            onTimeout = {},
        )
        is PlayerUiState.LoadingCarousel -> CarouselLoadingScreen(current.title)
        is PlayerUiState.CollectionSelection -> {
            BackHandler(onBack = viewModel::returnToSetup)
            CarouselHomeScreen(current.collections) { collection ->
                viewModel.selectCollection(current.address, collection)
            }
        }
        is PlayerUiState.Failure -> DiscoveryFailureScreen(
            message = current.message,
            initialAddress = current.address,
            onRetry = {
                discoveryTimedOut = false
                retryKey++
                viewModel.returnToSetup()
                retryDiscovery()
            },
            onConnect = viewModel::connect,
        )
        is PlayerUiState.Ready -> {
            BackHandler(onBack = viewModel::returnToCollections)
            ProjectionScreen(
                collection = current.collection,
                onConnectionLost = { viewModel.reportProjectionFailure(current.address, it) },
            )
        }
    }
}

@Composable
private fun ProjectionScreen(
    collection: SlideCollection,
    onConnectionLost: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sound = remember(collection.id) { MechanicalSoundPlayer(context) }
    val preferences = remember { ProjectionPreferences(context) }
    val projectionFocus = remember { FocusRequester() }
    val slideCount = collection.slides.size
    val projectionSaver = remember(slideCount) {
        Saver<ProjectionState, Int>(
            save = { state -> state.settledPosition() },
            restore = { position -> ProjectionState.restore(slideCount, position) },
        )
    }
    var projection by rememberSaveable(collection.id, stateSaver = projectionSaver) {
        mutableStateOf(ProjectionState.initial(slideCount))
    }
    var options by remember { mutableStateOf(preferences.load()) }
    var initialPreparation by remember(collection.id) { mutableStateOf(projection is ProjectionState.LoadingFirstSlide) }
    var navigationHintVisible by rememberSaveable(collection.id) { mutableStateOf(true) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var selectedSetting by rememberSaveable { mutableIntStateOf(0) }
    var transitionJob by remember(collection.id) { mutableStateOf<Job?>(null) }
    val imageLoader = coil3.SingletonImageLoader.get(context)
    val displayMetrics = context.resources.displayMetrics
    val projectionWidth = displayMetrics.widthPixels
    val projectionHeight = displayMetrics.heightPixels
    val preloader = remember(collection.id, projectionWidth, projectionHeight) {
        SlidePreloader(
            context = context.applicationContext,
            imageLoader = imageLoader,
            imageUrls = collection.slides.map { it.imageUrl },
            width = projectionWidth,
            height = projectionHeight,
        )
    }

    fun prepareAndRun(preparingState: ProjectionState) {
        val preparing = preparingState as? ProjectionState.Preparing ?: return
        transitionJob?.cancel()
        projection = preparing
        transitionJob = scope.launch {
            try {
                if (preparing.origin == null) {
                    preloader.prepareInitial(PreloadWindow.initialIndices(slideCount))
                    initialPreparation = false
                } else {
                    preparing.targetSlideIndex()?.let { preloader.ensure(it) }
                }
                if (options.soundEnabled) sound.awaitReady()
                val transition = preparing.beginMechanicalTransition() ?: return@launch
                if (projection != preparing) return@launch
                projection = transition
                if (options.soundEnabled) sound.play()
                delay(ProjectionTransition.TOTAL_DURATION_MS)
                if (projection == transition) projection = transition.reveal()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (projection == preparing) projection = preparing.cancelPreparation()
                onConnectionLost(exception)
            }
        }
    }

    fun move(delta: Int) {
        prepareAndRun(projection.beginMove(delta) ?: return)
    }

    fun updateOptions(updated: ProjectionOptions) {
        options = updated
        preferences.save(updated)
    }

    DisposableEffect(collection.id) {
        onDispose {
            transitionJob?.cancel()
            sound.release()
        }
    }
    LaunchedEffect(projection.settledPosition()) {
        val currentIndex = (projection as? ProjectionState.Slide)?.index ?: return@LaunchedEffect
        preloader.updateWindow(currentIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(projectionFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                navigationHintVisible = false
                if (settingsVisible) {
                    when (event.key) {
                        Key.DirectionUp -> selectedSetting = (selectedSetting - 1).coerceAtLeast(0)
                        Key.DirectionDown -> selectedSetting = (selectedSetting + 1).coerceAtMost(1)
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (selectedSetting == 0) {
                                updateOptions(options.copy(soundEnabled = !options.soundEnabled))
                            } else {
                                updateOptions(options.copy(fadeEnabled = !options.fadeEnabled))
                            }
                        }
                        Key.Back, Key.Menu -> settingsVisible = false
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.DirectionRight, Key.Enter, Key.NumPadEnter, Key.MediaNext -> move(1)
                    Key.DirectionLeft, Key.MediaPrevious -> move(-1)
                    Key.Menu, Key.DirectionDown -> settingsVisible = true
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        ProjectionSurface()
        ProjectionContent(
            projection = projection,
            preloader = preloader,
            imageLoader = imageLoader,
            fadeEnabled = options.fadeEnabled,
        )
        if (initialPreparation) {
            CarouselLoadingScreen(collection.title)
        }
        if (!initialPreparation && navigationHintVisible && !settingsVisible) {
            Text(
                text = "↓  Réglages de projection",
                color = Color(0xFFF5E7CF).copy(alpha = .66f),
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
            )
        }
        if (settingsVisible) {
            ProjectionSettingsScreen(
                soundEnabled = options.soundEnabled,
                fadeEnabled = options.fadeEnabled,
                selectedIndex = selectedSetting,
                onSoundChanged = { updateOptions(options.copy(soundEnabled = it)) },
                onFadeChanged = { updateOptions(options.copy(fadeEnabled = it)) },
            )
        }
    }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }
    LaunchedEffect(collection.id) {
        projectionFocus.requestFocus()
        prepareAndRun(projection.beginInitialLoad() ?: return@LaunchedEffect)
    }
    LaunchedEffect(initialPreparation) {
        if (!initialPreparation) {
            delay(4_500L)
            navigationHintVisible = false
        }
    }
}

@Composable
private fun ProjectionContent(
    projection: ProjectionState,
    preloader: SlidePreloader,
    imageLoader: coil3.ImageLoader,
    fadeEnabled: Boolean,
) {
    if (projection is ProjectionState.Transition) {
        var elapsedMs by remember(projection) { mutableLongStateOf(0L) }
        LaunchedEffect(projection) {
            var startTime: Long? = null
            while (elapsedMs < ProjectionTransition.TOTAL_DURATION_MS) {
                withFrameMillis { frameTime ->
                    val start = startTime ?: frameTime.also { startTime = it }
                    elapsedMs = (frameTime - start).coerceAtMost(ProjectionTransition.TOTAL_DURATION_MS)
                }
            }
        }
        val origin = projection.origin as? ProjectionState.Destination.Slide
        if (origin != null && elapsedMs <= 140L) {
            ProjectionSlide(
                index = origin.index,
                transform = ProjectionTransition.outgoing(elapsedMs, projection.direction, fadeEnabled),
                preloader = preloader,
                imageLoader = imageLoader,
            )
        }
        val destination = projection.destination as? ProjectionState.Destination.Slide
        if (destination != null && elapsedMs >= ProjectionTransition.ENTRY_START_MS) {
            ProjectionSlide(
                index = destination.index,
                transform = ProjectionTransition.incoming(elapsedMs, projection.direction, fadeEnabled),
                preloader = preloader,
                imageLoader = imageLoader,
            )
        }
        return
    }
    projection.visibleSlideIndex()?.let { index ->
        ProjectionSlide(
            index = index,
            transform = SlideTransform(0f, 0f, 0f, 1f, 1f),
            preloader = preloader,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun ProjectionSlide(
    index: Int,
    transform: SlideTransform,
    preloader: SlidePreloader,
    imageLoader: coil3.ImageLoader,
) {
    AsyncImage(
        model = preloader.request(index),
        contentDescription = null,
        imageLoader = imageLoader,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = transform.translationXFactor * density
                translationY = transform.translationYFactor * density
                rotationZ = transform.rotationZ
                scaleX = transform.scale
                scaleY = transform.scale
                alpha = transform.alpha
            },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ProjectionSurface() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFD8C5A3))
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFF5E8CF),
                        .55f to Color(0xFFE7D6B8),
                        1f to Color(0xFFB8A17D),
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .22f),
                    ),
                ),
            )
            .background(Color(0xFFE8A84C).copy(alpha = .035f)),
    )
}
