package fr.virtualdiapo.player.projection

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.size.Precision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class SlidePreloader(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val imageUrls: List<String>,
    private val width: Int,
    private val height: Int,
) {
    private val cacheKeys = mutableMapOf<Int, MemoryCache.Key>()

    fun request(index: Int): ImageRequest = ImageRequest.Builder(context)
        .data(imageUrls[index])
        .size(width, height)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()

    suspend fun prepareInitial(indices: List<Int>): InitialPreloadResult {
        val successfulIndices = indices
            .zip(load(indices))
            .filter { (_, success) -> success }
            .map { (index, _) -> index }
            .toSet()
        val result = InitialPreloadPolicy.evaluate(
            requestedIndices = indices,
            successfulIndices = successfulIndices,
        )
        check(result.firstSlideReady) { "Impossible de préparer la première diapositive" }
        return result
    }

    suspend fun updateWindow(currentIndex: Int) {
        val retainedIndices = PreloadWindow.indices(currentIndex, imageUrls.size).toSet()
        evictOutside(retainedIndices)
        load(retainedIndices.toList())
        evictOutside(retainedIndices)
    }

    suspend fun ensure(index: Int) {
        if (isReady(index)) return
        val result = imageLoader.execute(request(index))
        check(result is SuccessResult) { "Impossible de préparer la diapositive" }
        result.memoryCacheKey?.let { cacheKeys[index] = it }
    }

    fun isReady(index: Int): Boolean {
        val cacheKey = cacheKeys[index] ?: return false
        return imageLoader.memoryCache?.get(cacheKey) != null
    }

    private suspend fun load(indices: List<Int>): List<Boolean> = coroutineScope {
        indices.map { index ->
            async {
                try {
                    val result = imageLoader.execute(request(index))
                    if (result is SuccessResult) {
                        result.memoryCacheKey?.let { cacheKeys[index] = it }
                        true
                    } else {
                        false
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    false
                }
            }
        }.awaitAll()
    }

    private fun evictOutside(retainedIndices: Set<Int>) {
        val obsolete = cacheKeys.keys.filterNot(retainedIndices::contains)
        obsolete.forEach { index ->
            cacheKeys.remove(index)?.let { imageLoader.memoryCache?.remove(it) }
        }
    }
}

data class InitialPreloadResult(
    val firstSlideReady: Boolean,
    val followingSlidesReady: Int,
)

object InitialPreloadPolicy {
    fun evaluate(
        requestedIndices: List<Int>,
        successfulIndices: Set<Int>,
    ): InitialPreloadResult {
        val firstIndex = requestedIndices.firstOrNull()
        return InitialPreloadResult(
            firstSlideReady = firstIndex != null && firstIndex in successfulIndices,
            followingSlidesReady = requestedIndices
                .drop(1)
                .count(successfulIndices::contains),
        )
    }
}
