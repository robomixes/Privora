package com.privateai.camera.ui.qrscanner

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrGenerator {

    /**
     * Generate a QR code bitmap from content string.
     * Returns null on failure.
     */
    fun generate(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /** WiFi QR format: WIFI:T:WPA;S:MyNetwork;P:MyPassword;; */
    fun formatWifi(ssid: String, password: String, security: String = "WPA"): String {
        val escapedSsid = escapeSpecial(ssid)
        val escapedPass = escapeSpecial(password)
        return "WIFI:T:$security;S:$escapedSsid;P:$escapedPass;;"
    }

    /** Phone QR format: tel:+1234567890 */
    fun formatPhone(number: String): String = "tel:$number"

    /** Email QR format: MATMSG:TO:addr;SUB:subject;BODY:body;; */
    fun formatEmail(address: String, subject: String = "", body: String = ""): String {
        return buildString {
            append("MATMSG:TO:$address;")
            if (subject.isNotEmpty()) append("SUB:$subject;")
            if (body.isNotEmpty()) append("BODY:$body;")
            append(";")
        }
    }

    /** SMS QR format: smsto:number:message */
    fun formatSms(number: String, message: String = ""): String {
        return if (message.isEmpty()) "smsto:$number" else "smsto:$number:$message"
    }

    /** vCard 3.0 format */
    fun formatVCard(name: String, phone: String = "", email: String = "", org: String = ""): String {
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$name")
            if (phone.isNotEmpty()) appendLine("TEL:$phone")
            if (email.isNotEmpty()) appendLine("EMAIL:$email")
            if (org.isNotEmpty()) appendLine("ORG:$org")
            append("END:VCARD")
        }
    }

    /** Escape special characters for WiFi QR strings */
    private fun escapeSpecial(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
    }
}
