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
 * Navigation is a small back stack of route strings, saved so it survives the app being
 * paused while the camera scanner is open. Routes:
 *  "home", "merge", "view:<path>", "view:<path>|<action>", "sign:<path>", "form:<path>"
 */
@Composable
fun App(
    repo: DocRepository,
    activity: ComponentActivity,
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
) {
    var stackText by rememberSaveable { mutableStateOf("home") }
    val stack = stackText.split('\n')
    val route = stack.last()
    var version by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    val refresh: () -> Unit = { version++ }
    fun push(r: String) {
        version++
        stackText = (stack + r).joinToString("\n")
    }
    fun replaceTop(r: String) {
        version++
        stackText = (stack.dropLast(1) + r).joinToString("\n")
    }
    fun pop() {
        version++
        stackText = if (stack.size > 1) stack.dropLast(1).joinToString("\n") else "home"
    }
    val open: (File) -> Unit = { push("view:" + it.absolutePath) }
    fun openTool(tool: Tool, file: File) {
        val path = file.absolutePath
        when (tool) {
            Tool.Sign -> push("sign:$path")
            Tool.Form -> push("form:$path")
            Tool.Watermark -> push("watermark:$path")
            Tool.Enhance -> push("enhance:$path")
            Tool.Compress -> push("view:$path|compress")
            Tool.Text -> push("view:$path|text")
            Tool.Protect -> push("view:$path|password")
            Tool.Extract -> push("view:$path|extract")
            else -> push("view:$path")
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // A PDF opened from another app (Files, Gmail, WhatsApp...)
    LaunchedEffect(incoming) {
        val uri = incoming ?: return@LaunchedEffect
        val file = withContext(Dispatchers.IO) {
            runCatching { repo.importUri(uri, repo.displayName(uri) ?: "Opened ${timestamp()}") }.getOrNull()
        }
        onIncomingHandled()
        if (file != null) open(file) else message("Couldn't open that file")
    }

    when {
        route == "merge" -> MergeScreen(
            repo = repo,
            snackbar = snackbar,
            message = message,
            onBack = { pop() },
            onDone = { replaceTop("view:" + it.absolutePath) },
        )

        route.startsWith("sign:") -> {
            val file = File(route.removePrefix("sign:"))
            SignScreen(
                repo = repo,
                file = file,
                snackbar = snackbar,
                message = message,
                onBack = { pop() },
                onDone = { replaceTop("view:" + it.absolutePath) },
            )
        }

        route == "settings" -> SettingsScreen(
            repo = repo,
            snackbar = snackbar,
            message = message,
            onBack = { pop() },
        )

        route.startsWith("enhance:") -> EnhanceScreen(
            repo = repo,
            file = File(route.removePrefix("enhance:")),
            snackbar = snackbar,
            message = message,
            onBack = { pop() },
            onDone = { replaceTop("view:" + it.absolutePath) },
        )

        route.startsWith("watermark:") -> WatermarkScreen(
            repo = repo,
            file = File(route.removePrefix("watermark:")),
            snackbar = snackbar,
            message = message,
            onBack = { pop() },
            onDone = { replaceTop("view:" + it.absolutePath) },
        )

        route.startsWith("form:") -> {
            val file = File(route.removePrefix("form:"))
            FormScreen(
                repo = repo,
                file = file,
                snackbar = snackbar,
                message = message,
                onBack = { pop() },
                onDone = { replaceTop("view:" + it.absolutePath) },
                onUseFillAndSign = { replaceTop("sign:" + file.absolutePath) },
            )
        }

        route.startsWith("view:") -> {
            val parts = route.removePrefix("view:").split('|')
            val file = File(parts[0])
            ViewerScreen(
                repo = repo,
                file = file,
                initialAction = parts.getOrNull(1),
                onActionConsumed = { replaceTop("view:" + file.absolutePath) },
                snackbar = snackbar,
                message = message,
                onBack = { pop() },
                onOpen = { push("view:" + it.absolutePath) },
                onReplace = { replaceTop("view:" + it.absolutePath) },
                onTool = { tool -> openTool(tool, file) },
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
            onMerge = { push("merge") },
            onTool = { tool, file -> openTool(tool, file) },
            onSettings = { push("settings") },
        )
    }
}
