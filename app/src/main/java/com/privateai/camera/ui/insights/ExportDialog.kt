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
import androidx.compose.material.icons.filled.GridOn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.privateai.camera.R
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
    fun generateCsv(): String {
        return if (headers.isNotEmpty()) {
            val escapedHeaders = headers.joinToString(",") { v -> if (v.contains(",") || v.contains("\"")) "\"${v.replace("\"", "\"\"")}\"" else v }
            val escapedRows = rows.joinToString("\n") { row ->
                row.joinToString(",") { v -> if (v.contains(",") || v.contains("\"")) "\"${v.replace("\"", "\"\"")}\"" else v }
            }
            "$escapedHeaders\n$escapedRows"
        } else {
            reportText
        }
    }

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
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Share
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val file = generatePdf()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, context.getString(R.string.export_share_report)))
                        onDismiss()
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.action_share), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.export_share_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Toast.makeText(context, context.getString(R.string.export_saved_to_vault), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.export_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.export_save_to_vault), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.export_save_to_vault_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Toast.makeText(context, context.getString(R.string.export_saved_to_downloads), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.export_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.export_save_to_device), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.export_save_to_device_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Save as CSV
                Row(
                    Modifier.fillMaxWidth().clickable {
                        try {
                            val csvString = generateCsv()
                            val csvBytes = csvString.toByteArray(Charsets.UTF_8)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.csv")
                                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Privo")
                                }
                                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(csvBytes) } }
                            }
                            Toast.makeText(context, context.getString(R.string.export_saved_csv_to_downloads), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.export_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.GridOn, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.export_csv), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.export_csv_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
