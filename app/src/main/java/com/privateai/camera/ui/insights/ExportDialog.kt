package com.privateai.camera.ui.insights

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultRepository
import com.privateai.camera.util.PdfExporter
import java.io.File

/**
 * Export dialog with table PDF support.
 */
@Composable
fun ExportDialog(
    context: Context,
    reportText: String = "",
    fileName: String,
    onDismiss: () -> Unit,
    // Table mode (if headers provided, uses table layout)
    title: String = "",
    subtitle: List<String> = emptyList(),
    headers: List<String> = emptyList(),
    rows: List<List<String>> = emptyList(),
    summary: List<String> = emptyList()
) {
    fun generatePdf(): File {
        val file = File(context.cacheDir, "$fileName.pdf")
        if (headers.isNotEmpty()) {
            PdfExporter.exportTableToPdf(title.ifEmpty { fileName }, subtitle, headers, rows, summary, file)
        } else {
            PdfExporter.exportToPdf(reportText, file)
        }
        return file
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Share
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val file = generatePdf()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share Report"))
                        onDismiss()
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Share", style = MaterialTheme.typography.bodyLarge)
                        Text("Send PDF via email, messaging, etc.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Save to vault
                Row(
                    Modifier.fillMaxWidth().clickable {
                        try {
                            val file = generatePdf()
                            val pdfBytes = file.readBytes()
                            val crypto = CryptoManager(context).also { it.initialize() }
                            val vault = VaultRepository(context, crypto)
                            vault.saveFile(pdfBytes, "$fileName.pdf", VaultCategory.REPORTS)
                            file.delete()
                            Toast.makeText(context, "Saved to vault (Reports)", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Save to Vault", style = MaterialTheme.typography.bodyLarge)
                        Text("Encrypt and store in vault", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Save to device
                Row(
                    Modifier.fillMaxWidth().clickable {
                        try {
                            val file = generatePdf()
                            val pdfBytes = file.readBytes()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.pdf")
                                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Privo")
                                }
                                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(pdfBytes) } }
                            }
                            file.delete()
                            Toast.makeText(context, "Saved to Downloads/Privo", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Save to Device", style = MaterialTheme.typography.bodyLarge)
                        Text("Save PDF to Downloads folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
