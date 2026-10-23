package com.scanstack.app.pdf

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.scanstack.app.R

/** Fonts for typing on documents. The first three are the phone's built-in fonts. */
enum class TextFont(val label: String, val res: Int?) {
    Sans("Sans", null),
    Serif("Serif", null),
    Mono("Typewriter", null),
    Handwriting("Handwriting", R.font.caveat),
    Script("Script", R.font.dancing_script);

    fun typeface(): Typeface = when (this) {
        Sans -> Typeface.SANS_SERIF
        Serif -> Typeface.SERIF
        Mono -> Typeface.MONOSPACE
        else -> AppFonts.get(res!!)
    }
}

/** Handwriting-style fonts for typed signatures (all open-licensed, see assets/licenses). */
enum class SignatureFont(val label: String, val res: Int) {
    GreatVibes("Great Vibes", R.font.great_vibes),
    Allura("Allura", R.font.allura),
    Sacramento("Sacramento", R.font.sacramento),
    Delafield("Mrs Saint Delafield", R.font.mrs_saint_delafield),
    Dancing("Dancing Script", R.font.dancing_script);

    fun typeface(): Typeface = AppFonts.get(res)
}

object AppFonts {
    private var context: Context? = null
    private val cache = HashMap<Int, Typeface>()

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    @Synchronized
    fun get(res: Int): Typeface = cache.getOrPut(res) {
        context?.let { runCatching { ResourcesCompat.getFont(it, res) }.getOrNull() } ?: Typeface.DEFAULT
    }
}
