package com.example.data.ticket

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.model.ExtractedTicket
import com.example.engine.TicketParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import kotlin.coroutines.resume

object TicketImportManager {

    /**
     * Extracts ticket metadata from a selected PDF document.
     * Combines direct text stream scanning and on-device OCR for rendered PDF pages.
     * Guaranteed temporary file deletion in finally block.
     */
    suspend fun extractFromPdf(context: Context, pdfUri: Uri): ExtractedTicket = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val stringBuilder = StringBuilder()

        try {
            // 1. First attempt: Direct stream text extraction for digital PDFs
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.ISO_8859_1))
                val buffer = CharArray(4096)
                var charsRead: Int
                while (reader.read(buffer).also { charsRead = it } != -1) {
                    val chunk = String(buffer, 0, charsRead)
                    // Extract text strings between parentheses (PDF string literals) or BT/ET text blocks
                    val regex = Regex("\\((.*?)\\)")
                    regex.findAll(chunk).forEach { match ->
                        val text = match.groupValues[1]
                        if (text.length > 2 && text.any { it.isLetter() }) {
                            stringBuilder.append(text).append(" ")
                        }
                    }
                }
            }

            // 2. If direct stream yielded insufficient characters, render page 0 and perform local OCR
            if (stringBuilder.length < 50) {
                tempFile = File.createTempFile("ticket_pdf_temp", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val ocrText = runMlKitOcr(bitmap)
                    bitmap.recycle()
                    if (ocrText.isNotBlank()) {
                        stringBuilder.append("\n").append(ocrText)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            tempFile?.delete() // Strict privacy cleanup
        }

        TicketParser.parse(stringBuilder.toString())
    }

    /**
     * Extracts ticket metadata from an image file using on-device ML Kit OCR.
     */
    suspend fun extractFromImage(context: Context, imageUri: Uri): ExtractedTicket = withContext(Dispatchers.IO) {
        val ocrText = try {
            val image = InputImage.fromFilePath(context, imageUri)
            runMlKitOcr(image)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        TicketParser.parse(ocrText)
    }

    /**
     * Extracts ticket metadata from plain pasted or shared text.
     */
    fun extractFromText(rawText: String): ExtractedTicket {
        return TicketParser.parse(rawText)
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener {
                    cont.resume("")
                }
        } catch (e: Exception) {
            cont.resume("")
        }
    }

    private suspend fun runMlKitOcr(image: InputImage): String = suspendCancellableCoroutine { cont ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener {
                    cont.resume("")
                }
        } catch (e: Exception) {
            cont.resume("")
        }
    }
}
