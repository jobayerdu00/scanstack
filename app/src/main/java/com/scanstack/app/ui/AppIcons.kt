package com.scanstack.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * ScanStack's own outline icon set: 24 x 24 grid, 1.8 strokes, rounded caps and joins.
 * Icons are single-color; the color comes from the `tint` where they're used.
 */
object AppIcons {

    // ---------------------------------------------------------------- building blocks

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        horizontalLineTo(x + w - r)
        arcTo(r, r, 0f, false, true, x + w, y + r)
        verticalLineTo(y + h - r)
        arcTo(r, r, 0f, false, true, x + w - r, y + h)
        horizontalLineTo(x + r)
        arcTo(r, r, 0f, false, true, x, y + h - r)
        verticalLineTo(y + r)
        arcTo(r, r, 0f, false, true, x + r, y)
        close()
    }

    /** A page with a folded top-right corner (x 5..19, y 3..21). */
    private fun PathBuilder.page() {
        moveTo(14f, 3f)
        horizontalLineTo(7f)
        arcTo(2f, 2f, 0f, false, false, 5f, 5f)
        verticalLineTo(19f)
        arcTo(2f, 2f, 0f, false, false, 7f, 21f)
        horizontalLineTo(17f)
        arcTo(2f, 2f, 0f, false, false, 19f, 19f)
        verticalLineTo(8f)
        close()
        moveTo(14f, 3f)
        verticalLineTo(8f)
        horizontalLineTo(19f)
    }

    /** Four scanning corners around the edge of the icon. */
    private fun PathBuilder.scanCorners(inset: Float = 3.5f, len: Float = 4f) {
        val a = inset
        val b = 24f - inset
        moveTo(a, a + len); verticalLineTo(a + 1.5f); arcTo(1.5f, 1.5f, 0f, false, true, a + 1.5f, a); horizontalLineTo(a + len)
        moveTo(b - len, a); horizontalLineTo(b - 1.5f); arcTo(1.5f, 1.5f, 0f, false, true, b, a + 1.5f); verticalLineTo(a + len)
        moveTo(b, b - len); verticalLineTo(b - 1.5f); arcTo(1.5f, 1.5f, 0f, false, true, b - 1.5f, b); horizontalLineTo(b - len)
        moveTo(a + len, b); horizontalLineTo(a + 1.5f); arcTo(1.5f, 1.5f, 0f, false, true, a, b - 1.5f); verticalLineTo(b - len)
    }

    // ---------------------------------------------------------------- navigation

    val Home: ImageVector by lazy {
        icon("Home") {
            moveTo(3.5f, 10.5f); lineTo(12f, 3.8f); lineTo(20.5f, 10.5f)
            moveTo(5.8f, 8.8f); verticalLineTo(18.5f)
            arcTo(1.5f, 1.5f, 0f, false, false, 7.3f, 20f)
            horizontalLineTo(16.7f)
            arcTo(1.5f, 1.5f, 0f, false, false, 18.2f, 18.5f)
            verticalLineTo(8.8f)
            moveTo(10f, 20f); verticalLineTo(14.5f); horizontalLineTo(14f); verticalLineTo(20f)
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            moveTo(3.5f, 7f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5f, 5.5f)
            horizontalLineTo(9.6f); lineTo(11.6f, 7.8f); horizontalLineTo(19f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20.5f, 9.3f)
            verticalLineTo(17.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 19f, 19f)
            horizontalLineTo(5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 3.5f, 17.5f)
            close()
        }
    }

    val Grid: ImageVector by lazy {
        icon("Grid") {
            roundRect(4f, 4f, 6.5f, 6.5f, 1.6f)
            roundRect(13.5f, 4f, 6.5f, 6.5f, 1.6f)
            roundRect(4f, 13.5f, 6.5f, 6.5f, 1.6f)
            roundRect(13.5f, 13.5f, 6.5f, 6.5f, 1.6f)
        }
    }

    val Back: ImageVector by lazy {
        icon("Back") { moveTo(19f, 12f); horizontalLineTo(5f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
    }

    val Close: ImageVector by lazy {
        icon("Close") { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
    }

    val ChevronLeft: ImageVector by lazy { icon("ChevronLeft") { moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f) } }
    val ChevronRight: ImageVector by lazy { icon("ChevronRight") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) } }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown") { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) } }

    val More: ImageVector by lazy {
        ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black)) {
                circle(12f, 5.5f, 1.7f)
                circle(12f, 12f, 1.7f)
                circle(12f, 18.5f, 1.7f)
            }
            .build()
    }

    val Search: ImageVector by lazy { icon("Search") { circle(10.8f, 10.8f, 6.3f); moveTo(15.6f, 15.6f); lineTo(20f, 20f) } }

    val Check: ImageVector by lazy { icon("Check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }

    val Plus: ImageVector by lazy { icon("Plus") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } }

    // ---------------------------------------------------------------- capture & create

    /** Document inside scanning corners: the main "scan" action and the app's symbol. */
    val Scan: ImageVector by lazy {
        icon("Scan") {
            scanCorners()
            moveTo(7.5f, 12f); horizontalLineTo(16.5f)
        }
    }

    val Camera: ImageVector by lazy {
        icon("Camera") {
            moveTo(3.5f, 9f)
            arcTo(2f, 2f, 0f, false, true, 5.5f, 7f)
            horizontalLineTo(7.6f); lineTo(9.2f, 4.8f); horizontalLineTo(14.8f); lineTo(16.4f, 7f); horizontalLineTo(18.5f)
            arcTo(2f, 2f, 0f, false, true, 20.5f, 9f)
            verticalLineTo(17.5f)
            arcTo(2f, 2f, 0f, false, true, 18.5f, 19.5f)
            horizontalLineTo(5.5f)
            arcTo(2f, 2f, 0f, false, true, 3.5f, 17.5f)
            close()
            circle(12f, 13f, 3.4f)
        }
    }

    val Import: ImageVector by lazy {
        icon("Import") {
            page()
            moveTo(12f, 10.5f); verticalLineTo(17f)
            moveTo(9.3f, 14.4f); lineTo(12f, 17.1f); lineTo(14.7f, 14.4f)
        }
    }

    val Image: ImageVector by lazy {
        icon("Image") {
            roundRect(3.5f, 4.5f, 17f, 15f, 2.5f)
            circle(9f, 9.6f, 1.6f)
            moveTo(4f, 17f); lineTo(9.2f, 12.3f); lineTo(13f, 15.6f); lineTo(15.6f, 13.4f); lineTo(20f, 17.2f)
        }
    }

    /** Document with an arrow: converting a file to PDF. */
    val Convert: ImageVector by lazy {
        icon("Convert") {
            page()
            moveTo(8.5f, 14.5f); horizontalLineTo(15.3f)
            moveTo(12.8f, 12f); lineTo(15.3f, 14.5f); lineTo(12.8f, 17f)
        }
    }

    val Document: ImageVector by lazy {
        icon("Document") {
            page()
            moveTo(8.5f, 12.5f); horizontalLineTo(15.5f)
            moveTo(8.5f, 16f); horizontalLineTo(13.5f)
        }
    }

    val AddPage: ImageVector by lazy {
        icon("AddPage") {
            page()
            moveTo(12f, 11f); verticalLineTo(17.4f)
            moveTo(8.8f, 14.2f); horizontalLineTo(15.2f)
        }
    }

    // ---------------------------------------------------------------- edit & sign

    val Signature: ImageVector by lazy {
        icon("Signature") {
            moveTo(3f, 17.2f)
            curveTo(4.6f, 13.8f, 6.2f, 13.6f, 6.9f, 15.6f)
            curveTo(7.6f, 17.6f, 9.3f, 18f, 10.6f, 15.6f)
            moveTo(10.3f, 15.7f); lineTo(10.8f, 12.3f); lineTo(17.2f, 5.9f)
            arcTo(1.6f, 1.6f, 0f, false, true, 19.5f, 8.2f)
            lineTo(13.1f, 14.6f); close()
            moveTo(3f, 20.5f); horizontalLineTo(21f)
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
            moveTo(5f, 19f); lineTo(5.6f, 15.2f); lineTo(15.4f, 5.4f)
            arcTo(1.8f, 1.8f, 0f, false, true, 18f, 5.4f)
            lineTo(18.6f, 6f)
            arcTo(1.8f, 1.8f, 0f, false, true, 18.6f, 8.6f)
            lineTo(8.8f, 18.4f); close()
            moveTo(13.6f, 7.2f); lineTo(16.8f, 10.4f)
        }
    }

    val Form: ImageVector by lazy {
        icon("Form") {
            page()
            roundRect(8f, 10.6f, 3f, 3f, 0.7f)
            moveTo(13f, 12.1f); horizontalLineTo(16f)
            roundRect(8f, 15.4f, 3f, 3f, 0.7f)
            moveTo(13f, 16.9f); horizontalLineTo(16f)
        }
    }

    val Watermark: ImageVector by lazy {
        icon("Watermark") {
            page()
            moveTo(12f, 10.6f)
            curveTo(10.8f, 12.1f, 9.6f, 13.6f, 9.6f, 15.1f)
            curveTo(9.6f, 16.5f, 10.7f, 17.6f, 12f, 17.6f)
            curveTo(13.3f, 17.6f, 14.4f, 16.5f, 14.4f, 15.1f)
            curveTo(14.4f, 13.6f, 13.2f, 12.1f, 12f, 10.6f)
            close()
        }
    }

    val Merge: ImageVector by lazy {
        icon("Merge") {
            moveTo(12f, 4f); lineTo(20f, 8.2f); lineTo(12f, 12.4f); lineTo(4f, 8.2f); close()
            moveTo(4f, 12.2f); lineTo(12f, 16.4f); lineTo(20f, 12.2f)
            moveTo(4f, 16.2f); lineTo(12f, 20.4f); lineTo(20f, 16.2f)
        }
    }

    val Scissors: ImageVector by lazy {
        icon("Scissors") {
            circle(6.5f, 7f, 2.5f)
            circle(6.5f, 17f, 2.5f)
            moveTo(8.7f, 8.3f); lineTo(20f, 17.4f)
            moveTo(8.7f, 15.7f); lineTo(20f, 6.6f)
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            roundRect(4f, 5.5f, 16f, 14.5f, 2.5f)
            moveTo(4f, 10f); horizontalLineTo(20f)
            moveTo(8.5f, 3.5f); verticalLineTo(7f)
            moveTo(15.5f, 3.5f); verticalLineTo(7f)
        }
    }

    val TextTool: ImageVector by lazy {
        icon("TextTool") {
            moveTo(5.5f, 7.5f); verticalLineTo(5.5f); horizontalLineTo(18.5f); verticalLineTo(7.5f)
            moveTo(12f, 5.5f); verticalLineTo(18.5f)
            moveTo(9.5f, 18.5f); horizontalLineTo(14.5f)
        }
    }

    /** "T" inside scanning corners: read text from a page. */
    val Ocr: ImageVector by lazy {
        icon("Ocr") {
            scanCorners(3.5f, 3.6f)
            moveTo(8.5f, 8.5f); horizontalLineTo(15.5f)
            moveTo(12f, 8.5f); verticalLineTo(16f)
        }
    }

    val Resize: ImageVector by lazy {
        icon("Resize") {
            moveTo(14f, 4.5f); horizontalLineTo(19.5f); verticalLineTo(10f)
            moveTo(19.5f, 4.5f); lineTo(13.5f, 10.5f)
            moveTo(10f, 19.5f); horizontalLineTo(4.5f); verticalLineTo(14f)
            moveTo(4.5f, 19.5f); lineTo(10.5f, 13.5f)
        }
    }

    // ---------------------------------------------------------------- optimise, protect, share

    val Compress: ImageVector by lazy {
        icon("Compress") {
            moveTo(12f, 3.5f); verticalLineTo(9.5f)
            moveTo(9f, 6.5f); lineTo(12f, 9.5f); lineTo(15f, 6.5f)
            moveTo(12f, 20.5f); verticalLineTo(14.5f)
            moveTo(9f, 17.5f); lineTo(12f, 14.5f); lineTo(15f, 17.5f)
            moveTo(4.5f, 12f); horizontalLineTo(19.5f)
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            roundRect(5f, 10.5f, 14f, 10f, 2f)
            moveTo(8f, 10.5f); verticalLineTo(8f)
            arcTo(4f, 4f, 0f, false, true, 16f, 8f)
            verticalLineTo(10.5f)
            moveTo(12f, 14.6f); verticalLineTo(16.4f)
        }
    }

    val Unlock: ImageVector by lazy {
        icon("Unlock") {
            roundRect(5f, 10.5f, 14f, 10f, 2f)
            moveTo(8f, 10.5f); verticalLineTo(8f)
            arcTo(4f, 4f, 0f, false, true, 15.7f, 6.5f)
            moveTo(12f, 14.6f); verticalLineTo(16.4f)
        }
    }

    val Share: ImageVector by lazy {
        icon("Share") {
            circle(17.5f, 5.8f, 2.4f)
            circle(6.5f, 12f, 2.4f)
            circle(17.5f, 18.2f, 2.4f)
            moveTo(8.6f, 10.8f); lineTo(15.4f, 7f)
            moveTo(8.6f, 13.2f); lineTo(15.4f, 17f)
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            moveTo(12f, 4f); verticalLineTo(14.5f)
            moveTo(7.5f, 10f); lineTo(12f, 14.5f); lineTo(16.5f, 10f)
            moveTo(4.5f, 16.5f); verticalLineTo(18.5f)
            arcTo(1.5f, 1.5f, 0f, false, false, 6f, 20f)
            horizontalLineTo(18f)
            arcTo(1.5f, 1.5f, 0f, false, false, 19.5f, 18.5f)
            verticalLineTo(16.5f)
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            moveTo(4.5f, 6.5f); horizontalLineTo(19.5f)
            moveTo(9.5f, 6.5f); verticalLineTo(5f)
            arcTo(1f, 1f, 0f, false, true, 10.5f, 4f)
            horizontalLineTo(13.5f)
            arcTo(1f, 1f, 0f, false, true, 14.5f, 5f)
            verticalLineTo(6.5f)
            moveTo(6.5f, 6.5f); lineTo(7.3f, 18.6f)
            arcTo(1.5f, 1.5f, 0f, false, false, 8.8f, 20f)
            horizontalLineTo(15.2f)
            arcTo(1.5f, 1.5f, 0f, false, false, 16.7f, 18.6f)
            lineTo(17.5f, 6.5f)
            moveTo(10.2f, 10.5f); verticalLineTo(16f)
            moveTo(13.8f, 10.5f); verticalLineTo(16f)
        }
    }

    val Pages: ImageVector by lazy {
        icon("Pages") {
            roundRect(8f, 3.5f, 12f, 14f, 2f)
            moveTo(16f, 20.5f); horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 4f, 18.5f)
            verticalLineTo(7.5f)
        }
    }

    val Storage: ImageVector by lazy {
        icon("Storage") {
            roundRect(4f, 4.5f, 16f, 6f, 2f)
            roundRect(4f, 13.5f, 16f, 6f, 2f)
            moveTo(7.5f, 7.5f); horizontalLineTo(8f)
            moveTo(7.5f, 16.5f); horizontalLineTo(8f)
        }
    }
}
