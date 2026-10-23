package com.scanstack.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.scanstack.app.data.DocRepository
import com.scanstack.app.ui.App
import com.scanstack.app.ui.ScanStackTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {

    /** A PDF another app asked us to open. */
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        if (savedInstanceState == null) readIntent(intent)

        val repo = DocRepository(applicationContext)
        setContent {
            ScanStackTheme {
                App(
                    repo = repo,
                    activity = this,
                    incoming = incoming.value,
                    onIncomingHandled = { incoming.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { incoming.value = it }
    }
}
