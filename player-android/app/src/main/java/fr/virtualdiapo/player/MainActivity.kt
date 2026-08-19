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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import fr.virtualdiapo.player.model.SlideCollection
import fr.virtualdiapo.player.network.DiscoveredServer
import fr.virtualdiapo.player.network.DiscoveryStatus
import fr.virtualdiapo.player.network.VirtualDiapoDiscovery
import fr.virtualdiapo.player.projection.MechanicalSoundPlayer
import fr.virtualdiapo.player.projection.ProjectionState
import fr.virtualdiapo.player.ui.CarouselHomeScreen
import fr.virtualdiapo.player.ui.CarouselLoadingScreen
import fr.virtualdiapo.player.ui.DiscoveryFailureScreen
import fr.virtualdiapo.player.ui.DiscoveryScreen
import fr.virtualdiapo.player.ui.SplashScreen
import fr.virtualdiapo.player.ui.theme.VirtualDiapoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val sound = remember { MechanicalSoundPlayer(context) }
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
    val imageLoader = coil3.SingletonImageLoader.get(context)
    val displayMetrics = context.resources.displayMetrics
    val projectionWidth = displayMetrics.widthPixels
    val projectionHeight = displayMetrics.heightPixels

    fun imageRequest(index: Int) = ImageRequest.Builder(context)
        .data(collection.slides[index].imageUrl)
        .size(projectionWidth, projectionHeight)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()

    fun prepareAndRun(preparing: ProjectionState) {
        projection = preparing
        scope.launch {
            try {
                preparing.targetSlideIndex()?.let { targetIndex ->
                    check(imageLoader.execute(imageRequest(targetIndex)) is coil3.request.SuccessResult) {
                        "Impossible de préparer la diapositive"
                    }
                }
                sound.awaitReady()
                val transition = preparing.beginMechanicalTransition() ?: return@launch
                if (projection != preparing) return@launch
                projection = transition
                sound.play()
                delay(MechanicalSoundPlayer.SLIDE_APPEAR_TIME_MS)
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

    val preloadIndex = when (val state = projection) {
        is ProjectionState.LoadingFirstSlide -> 0
        is ProjectionState.Slide -> state.index
        is ProjectionState.Preparing -> state.targetSlideIndex() ?: slideCount - 1
        is ProjectionState.Transition -> when (val destination = state.destination) {
            is ProjectionState.Destination.Slide -> destination.index
            ProjectionState.Destination.End -> slideCount - 1
        }
        is ProjectionState.EndOfCarousel -> slideCount - 1
    }

    PreloadAdjacentImages(collection, preloadIndex, imageLoader, projectionWidth, projectionHeight)
    DisposableEffect(Unit) { onDispose(sound::release) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
        projection.visibleSlideIndex()?.let { visibleSlideIndex ->
            AsyncImage(
                model = imageRequest(visibleSlideIndex),
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
    LaunchedEffect(collection.id) {
        projectionFocus.requestFocus()
        prepareAndRun(projection.beginInitialLoad() ?: return@LaunchedEffect)
    }
}

@Composable
private fun PreloadAdjacentImages(
    collection: SlideCollection,
    currentIndex: Int,
    imageLoader: ImageLoader,
    width: Int,
    height: Int,
) {
    val context = LocalContext.current
    LaunchedEffect(collection.id, currentIndex) {
        val indices = listOf(currentIndex - 1, currentIndex, currentIndex + 1)
            .filter { it in collection.slides.indices }
        coroutineScope {
            indices.map { index ->
                async {
                    try {
                        imageLoader.execute(
                            ImageRequest.Builder(context)
                                .data(collection.slides[index].imageUrl)
                                .size(width, height)
                                .precision(Precision.INEXACT)
                                .build(),
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        // Preloading is best effort. The foreground request reports actionable failures.
                    }
                }
            }.awaitAll()
        }
    }
}
