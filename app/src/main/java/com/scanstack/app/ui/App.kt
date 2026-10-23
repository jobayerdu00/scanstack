package com.scanstack.app.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scanstack.app.data.DocRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Top-level navigation. The route is a simple string so it survives the app being
 * paused while the camera scanner is open: "home", "merge" or "view:<file path>".
 */
@Composable
fun App(
    repo: DocRepository,
    activity: ComponentActivity,
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf("home") }
    var version by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    val refresh: () -> Unit = { version++ }
    val open: (File) -> Unit = { route = "view:" + it.absolutePath }
    val goHome: () -> Unit = {
        refresh()
        route = "home"
    }

    // A PDF opened from another app (Files, Gmail, WhatsApp...)
    LaunchedEffect(incoming) {
        val uri = incoming ?: return@LaunchedEffect
        val file = withContext(Dispatchers.IO) {
            runCatching { repo.importUri(uri, repo.displayName(uri) ?: "Opened ${timestamp()}") }.getOrNull()
        }
        onIncomingHandled()
        if (file != null) {
            refresh()
            open(file)
        } else {
            message("Couldn't open that file")
        }
    }

    when {
        route == "merge" -> {
            BackHandler(onBack = goHome)
            MergeScreen(
                repo = repo,
                snackbar = snackbar,
                message = message,
                onBack = goHome,
                onDone = {
                    refresh()
                    open(it)
                },
            )
        }
        route.startsWith("view:") -> {
            val file = File(route.removePrefix("view:"))
            BackHandler(onBack = goHome)
            ViewerScreen(
                repo = repo,
                file = file,
                snackbar = snackbar,
                message = message,
                onBack = goHome,
                onOpen = {
                    refresh()
                    open(it)
                },
            )
        }
        else -> HomeScreen(
            repo = repo,
            activity = activity,
            version = version,
            snackbar = snackbar,
            message = message,
            refresh = refresh,
            onOpen = open,
            onMerge = { route = "merge" },
        )
    }
}
