package fr.virtualdiapo.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import fr.virtualdiapo.player.projection.ProjectionBeamGeometry
import fr.virtualdiapo.player.projection.ProjectionDust
import fr.virtualdiapo.player.projection.ProjectionPreferences
import fr.virtualdiapo.player.projection.ProjectionState
import fr.virtualdiapo.player.projection.ProjectionTransition
import fr.virtualdiapo.player.projection.SlidePreloader
import fr.virtualdiapo.player.projection.SlideTransform
import fr.virtualdiapo.player.ui.CarouselHomeScreen
import fr.virtualdiapo.player.ui.CarouselLoadingScreen
import fr.virtualdiapo.player.ui.DiscoveryFailureScreen
import fr.virtualdiapo.player.ui.DiscoveryScreen
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
    val options = remember(collection.id) { preferences.load() }
    var initialPreparation by remember(collection.id) { mutableStateOf(projection is ProjectionState.LoadingFirstSlide) }
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

    val animateBeamDust = when (val state = projection) {
        is ProjectionState.LoadingFirstSlide,
        is ProjectionState.Transition,
        is ProjectionState.EndOfCarousel,
        -> true
        is ProjectionState.Preparing ->
            state.origin == null || state.origin == ProjectionState.Destination.End
        is ProjectionState.Slide -> false
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
                when (event.key) {
                    Key.DirectionRight, Key.Enter, Key.NumPadEnter, Key.MediaNext -> move(1)
                    Key.DirectionLeft, Key.MediaPrevious -> move(-1)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        ProjectionSurface(animateDust = animateBeamDust)
        ProjectionContent(
            projection = projection,
            preloader = preloader,
            imageLoader = imageLoader,
            fadeEnabled = options.fadeEnabled,
        )
        if (initialPreparation) {
            CarouselLoadingScreen(collection.title)
        }
    }
    LaunchedEffect(collection.id) {
        projectionFocus.requestFocus()
        prepareAndRun(projection.beginInitialLoad() ?: return@LaunchedEffect)
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
        if (origin != null) {
            ProjectionSlide(
                index = origin.index,
                transform = ProjectionTransition.outgoing(elapsedMs, projection.direction, fadeEnabled),
                preloader = preloader,
                imageLoader = imageLoader,
            )
        }
        val destination = projection.destination as? ProjectionState.Destination.Slide
        if (destination != null) {
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
            transform = SlideTransform(translationXFactor = 0f, photoAlpha = 1f),
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = transform.translationXFactor * size.width
            }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = preloader.request(index),
            contentDescription = null,
            imageLoader = imageLoader,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = transform.photoAlpha },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ProjectionSurface(animateDust: Boolean) {
    val dustPhase = remember { Animatable(0f) }
    LaunchedEffect(animateDust) {
        if (!animateDust) {
            dustPhase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            dustPhase.snapTo(0f)
            dustPhase.animateTo(1f, tween(durationMillis = 8_000, easing = LinearEasing))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val gradientCenter = Offset(size.width / 2f, size.height / 2f)
                val gradientRadius = ProjectionBeamGeometry.radius(size.width, size.height)
                val haloRadius = ProjectionBeamGeometry.haloRadius(size.width, size.height)
                val dust = ProjectionDust.generate(size.width, size.height)
                val beam = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFF1F1ED),
                        .32f to Color(0xFFF2F2EE),
                        .62f to Color(0xFFECECE8),
                        .82f to Color(0xFFDFDFDB),
                        1f to Color(0xFFC9C9C5),
                    ),
                    center = gradientCenter,
                    radius = gradientRadius,
                )
                val halo = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = .58f),
                        .12f to Color.White.copy(alpha = .42f),
                        .32f to Color(0xFFFFFEFC).copy(alpha = .20f),
                        .55f to Color(0xFFFFFEFC).copy(alpha = .08f),
                        .78f to Color(0xFFFFFEFC).copy(alpha = .02f),
                        1f to Color.Transparent,
                    ),
                    center = gradientCenter,
                    radius = haloRadius,
                )
                val vignette = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .72f to Color.Transparent,
                        .90f to Color.Black.copy(alpha = .07f),
                        1f to Color.Black.copy(alpha = .18f),
                    ),
                    center = gradientCenter,
                    radius = gradientRadius,
                )
                onDrawBehind {
                    drawRect(Color(0xFFDEDEDA))
                    drawRect(beam)
                    drawRect(halo)
                    val phase = dustPhase.value
                    dust.forEach { particle ->
                        val center = Offset(
                            x = particle.x + ProjectionDust.offsetX(particle, phase) * density,
                            y = particle.y + ProjectionDust.offsetY(particle, phase) * density,
                        )
                        val radius = particle.radiusDp * density
                        drawCircle(
                            color = Color(0xFFFFFDF7).copy(alpha = particle.haloAlpha),
                            radius = radius * 2.2f,
                            center = center,
                        )
                        drawCircle(
                            color = Color(0xFFFFFDF7).copy(alpha = particle.coreAlpha),
                            radius = radius,
                            center = center,
                        )
                    }
                    drawRect(vignette)
                }
            },
    )
}
