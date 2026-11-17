package io.github.neoruben.mercadonastats.parser

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

/**
 * Extrae el texto seleccionable de un PDF en Android usando pdfbox-android (sin OCR ni servicios externos).
 */
object PdfTextExtractor {

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialized = true
                }
            }
        }
    }

    fun extractText(context: Context, pdfBytes: ByteArray): String {
        init(context)
        PDDocument.load(ByteArrayInputStream(pdfBytes)).use { document ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            return stripper.getText(document)
        }
    }
}
