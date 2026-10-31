package de.autoknigge.enneagramm

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Markierte Stelle auf einer Seite (Bruchteile der Seitengröße). */
class Box(val l: Float, val t: Float, val r: Float, val b: Float)

/** Zeichnet Seiten einer PDF als Bild (Android-eigener PDF-Renderer). Nicht threadsicher. */
class PageRenderer(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(pfd)
    } catch (e: Exception) {
        pfd.close()
        throw IOException("Die PDF lässt sich nicht darstellen.")
    }

    val pageCount: Int get() = renderer.pageCount

    /** Zeichnet die Seite auf weißem Grund, die Breite hat [widthPx] Pixel. */
    fun render(index: Int, widthPx: Int): Bitmap = renderer.openPage(index).use { page ->
        val w = widthPx.coerceIn(64, 4000)
        val h = max(1, (w * (if (page.width > 0) page.height.toFloat() / page.width else 1.4142f)).roundToInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bmp
    }

    override fun close() {
        try {
            renderer.close()
        } finally {
            pfd.close()
        }
    }
}
