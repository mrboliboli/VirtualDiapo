package fr.virtualdiapo.player

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
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
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.virtualdiapo.player.model.SlideCollection
import fr.virtualdiapo.player.model.CollectionSummary
import fr.virtualdiapo.player.projection.MechanicalSoundPlayer
import fr.virtualdiapo.player.projection.ProjectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val viewModel: MainViewModel = viewModel()
                VirtualDiapoApp(viewModel)
            }
        }
    }
}

@Composable
private fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFC9A75D),
    background = Color.Black,
    surface = Color(0xFF171512),
)

@Composable
private fun VirtualDiapoApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    when (val current = state) {
        PlayerUiState.Setup -> ConnectionScreen(onConnect = viewModel::connect)
        PlayerUiState.Loading -> LoadingScreen()
        is PlayerUiState.CollectionSelection -> {
            BackHandler(onBack = viewModel::returnToSetup)
            CollectionSelectionScreen(current.collections) { id ->
                viewModel.selectCollection(current.address, id)
            }
        }
        is PlayerUiState.Failure -> ConnectionScreen(current.message, viewModel::connect)
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
private fun ConnectionScreen(error: String? = null, onConnect: (String) -> Unit) {
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
    val sound = remember { MechanicalSoundPlayer() }
    val projectionFocus = remember { FocusRequester() }
    var projection by remember(collection.id) { mutableStateOf(ProjectionState(collection.slides.size)) }
    val imageLoader = coil3.SingletonImageLoader.get(context)

    fun move(delta: Int) {
        val started = projection.beginMove(delta) ?: return
        projection = started
        sound.play()
        scope.launch {
            delay(180)
            projection = projection.reveal()
        }
    }

    PreloadAdjacentImages(collection, projection.currentIndex, imageLoader)
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
        if (!projection.black) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(collection.slides[projection.currentIndex].imageUrl)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
    LaunchedEffect(collection.id) { projectionFocus.requestFocus() }
}

@Composable
private fun PreloadAdjacentImages(collection: SlideCollection, currentIndex: Int, imageLoader: ImageLoader) {
    val context = LocalContext.current
    LaunchedEffect(collection.id, currentIndex) {
        listOf(currentIndex - 1, currentIndex, currentIndex + 1)
            .filter { it in collection.slides.indices }
            .forEach { index ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(collection.slides[index].imageUrl)
                        .memoryCacheKey(collection.slides[index].imageUrl)
                        .build(),
                )
            }
    }
}
