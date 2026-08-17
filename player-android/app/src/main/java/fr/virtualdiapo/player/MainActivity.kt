package fr.virtualdiapo.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import fr.virtualdiapo.player.model.SlideCollection
import fr.virtualdiapo.player.model.CollectionSummary
import fr.virtualdiapo.player.projection.MechanicalSoundPlayer
import fr.virtualdiapo.player.projection.ProjectionState
import fr.virtualdiapo.player.network.VirtualDiapoDiscovery
import fr.virtualdiapo.player.network.DiscoveredServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            MaterialTheme(colorScheme = darkColorScheme()) {
                val viewModel: MainViewModel = viewModel()
                val servers by discovery.servers.collectAsState()
                VirtualDiapoApp(viewModel, servers)
            }
        }
    }

    override fun onStart() { super.onStart(); discovery.start() }
    override fun onStop() { discovery.stop(); super.onStop() }
}

@Composable
private fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFC9A75D),
    background = Color.Black,
    surface = Color(0xFF171512),
)

@Composable
private fun VirtualDiapoApp(viewModel: MainViewModel, servers: List<DiscoveredServer>) {
    val state by viewModel.state.collectAsState()
    when (val current = state) {
        PlayerUiState.Setup -> ConnectionScreen(servers = servers, onConnect = viewModel::connect)
        PlayerUiState.Loading -> LoadingScreen()
        is PlayerUiState.CollectionSelection -> {
            BackHandler(onBack = viewModel::returnToSetup)
            CollectionSelectionScreen(current.collections) { id ->
                viewModel.selectCollection(current.address, id)
            }
        }
        is PlayerUiState.Failure -> ConnectionScreen(current.message, servers, viewModel::connect)
        is PlayerUiState.Ready -> {
            BackHandler(onBack = viewModel::returnToCollections)
            ProjectionScreen(current.collection)
        }
    }
}

@Composable
private fun CollectionSelectionScreen(
    collections: List<CollectionSummary>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF171512)).padding(64.dp),
    ) {
        Text("Choisir une collection", style = MaterialTheme.typography.headlineLarge, color = Color(0xFFE8DFC8))
        Spacer(Modifier.height(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(collections, key = { it.id }) { collection ->
                Button(
                    onClick = { onSelect(collection.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(collection.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            listOfNotNull(collection.year?.toString(), "${collection.slideCount} images")
                                .joinToString(" · "),
                        )
                        collection.description?.let { Text(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    error: String? = null,
    servers: List<DiscoveredServer> = emptyList(),
    onConnect: (String) -> Unit,
) {
    var address by remember { mutableStateOf("10.0.2.2:8080") }
    val buttonFocus = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF171512)).padding(72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("VIRTUALDIAPO", style = MaterialTheme.typography.displaySmall, color = Color(0xFFE8DFC8))
        Text("Adresse du Mac", modifier = Modifier.padding(top = 32.dp, bottom = 8.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(0.55f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        if (error != null) {
            Text(error, color = Color(0xFFE29A82), modifier = Modifier.padding(top = 12.dp))
        }
        Button(
            onClick = { onConnect(address) },
            modifier = Modifier.padding(top = 24.dp).focusRequester(buttonFocus),
        ) { Text("Charger le projecteur") }
        if (servers.isNotEmpty()) {
            Text("Serveurs détectés", modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
            servers.forEach { server ->
                Button(onClick = { onConnect(server.address) }, modifier = Modifier.padding(top = 6.dp)) {
                    Text("${server.name} · ${server.address}")
                }
            }
        }
    }
    LaunchedEffect(Unit) { buttonFocus.requestFocus() }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFFC9A75D))
    }
}

@Composable
private fun ProjectionScreen(collection: SlideCollection) {
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
            } catch (_: Exception) {
                if (projection == preparing) projection = preparing.cancelPreparation()
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

    PreloadAdjacentImages(
        collection,
        preloadIndex,
        imageLoader,
        projectionWidth,
        projectionHeight,
    )
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
        val visibleSlideIndex = projection.visibleSlideIndex()
        if (visibleSlideIndex != null) {
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
        listOf(currentIndex - 1, currentIndex, currentIndex + 1)
            .filter { it in collection.slides.indices }
            .forEach { index ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(collection.slides[index].imageUrl)
                        .size(width, height)
                        .precision(Precision.INEXACT)
                        .build(),
                )
            }
    }
}
