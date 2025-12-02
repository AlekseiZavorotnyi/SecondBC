// Файл: MainActivity.kt
package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import kotlinx.coroutines.launch

private const val TAG = "CatGallery"

@JsonClass(generateAdapter = true)
data class CatResponse(
    @Json(name = "id") val id: String,
    @Json(name = "tags") val tags: List<String> = emptyList(),
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "mimetype") val mimetype: String? = null
) {
    val isValid: Boolean
        get() = !id.isNullOrEmpty()
}


data class CatItem(
    val id: String,
    val url: String,
    val tags: List<String>,
    val width: Int,
    val height: Int
) {
    companion object {
        fun fromResponse(response: CatResponse): CatItem {
            return CatItem(
                id = response.id,
                url = "https://cataas.com/cat/${response.id}", // без .jpg
                tags = response.tags,
                width = 300 + (Math.random() * 200).toInt(),
                height = 300 + (Math.random() * 300).toInt()
            )
        }
    }
}

interface CatApiService {
    @GET("api/cats")
    suspend fun getCats(
        @Query("skip") skip: Int,
        @Query("limit") limit: Int
    ): List<CatResponse>
}

class CatsPagingSource(
    private val apiService: CatApiService
) : PagingSource<Int, CatItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CatItem> {
        return try {
            val page = params.key ?: 0
            val skip = page * params.loadSize

            val response = apiService.getCats(skip = skip, limit = params.loadSize)

            val cats = response.map { CatItem.fromResponse(it) }

            LoadResult.Page(
                data = cats,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (response.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }


    override fun getRefreshKey(state: PagingState<Int, CatItem>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}

class MainViewModel : ViewModel() {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://cataas.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(CatApiService::class.java)

    var hasData by mutableStateOf(false)
        private set

    val cats = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false
        )
    ) {
        CatsPagingSource(apiService)
    }.flow

    fun startLoading() {
        hasData = true
    }

    fun resetState() {
        hasData = false
    }
}

@Composable
fun CatCard(
    cat: CatItem,
    index: Int,
    onClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        onClick = { onClick(index) }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(cat.url)
                .crossfade(true)
                .build(),
            contentDescription = "Cat image",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cat.width.toFloat() / cat.height.toFloat())
        )

    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorRetrySection(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(message)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
fun StartScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Добро пожаловать в CatGallery!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Загрузить котов")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {

    val viewModel: MainViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cat Gallery") }
            )
        },
        floatingActionButton = {
            if (viewModel.hasData) {
                FloatingActionButton(onClick = { viewModel.resetState() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            }
        }
    ) { padding ->

        if (!viewModel.hasData) {
            StartScreen { viewModel.startLoading() }
            return@Scaffold
        }

        val cats = viewModel.cats.collectAsLazyPagingItems()

        when {
            cats.loadState.refresh is androidx.paging.LoadState.Loading -> {
                LoadingIndicator()
            }
            cats.loadState.refresh is androidx.paging.LoadState.Error -> {
                val error = cats.loadState.refresh as androidx.paging.LoadState.Error
                ErrorRetrySection(
                    message = "Ошибка загрузки: ${error.error.localizedMessage}",
                    onRetry = { cats.retry() }
                )
            }
            else -> {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(150.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    itemsIndexed(cats.itemSnapshotList.items) { index, cat ->
                        if (cat != null) {
                            CatCard(cat, index) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Cat #${it + 1}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                MainScreen()
            }
        }
    }
}