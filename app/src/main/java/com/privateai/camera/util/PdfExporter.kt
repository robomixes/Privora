package com.privateai.camera.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Exports reports as PDF with table formatting.
 */
object PdfExporter {

    private const val PAGE_W = 595  // A4
    private const val PAGE_H = 842
    private const val MARGIN = 36f

    private val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; isAntiAlias = true; color = android.graphics.Color.BLACK }
    private val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true; isAntiAlias = true; color = android.graphics.Color.WHITE }
    private val cellPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = android.graphics.Color.BLACK }
    private val subtitlePaint = Paint().apply { textSize = 12f; isFakeBoldText = true; isAntiAlias = true; color = android.graphics.Color.DKGRAY }
    private val lightPaint = Paint().apply { textSize = 9f; isAntiAlias = true; color = android.graphics.Color.GRAY }
    private val headerBgPaint = Paint().apply { color = android.graphics.Color.rgb(66, 66, 66); style = Paint.Style.FILL }
    private val rowBgPaint = Paint().apply { color = android.graphics.Color.rgb(245, 245, 245); style = Paint.Style.FILL }
    private val linePaint = Paint().apply { color = android.graphics.Color.rgb(200, 200, 200); strokeWidth = 0.5f }

    /**
     * Export text report as PDF with tables.
     * Lines starting with "|" are treated as table rows.
     * Lines starting with "---" are separators.
     * First line is the title.
     */
    fun exportToPdf(text: String, outputFile: File) {
        val document = PdfDocument()
        val lines = text.split("\n")
        var pageNum = 1
        var lineIdx = 0

        while (lineIdx < lines.size) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            var y = MARGIN + 20f
            val maxY = PAGE_H - MARGIN - 20f

            while (lineIdx < lines.size && y < maxY) {
                val line = lines[lineIdx]
                when {
                    lineIdx == 0 -> {
                        canvas.drawText(line, MARGIN, y, titlePaint)
                        y += 28f
                    }
                    line.startsWith("---") -> {
                        y += 4f
                        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
                        y += 8f
                    }
                    line.contains("|") && line.trim().startsWith("|") -> {
                        // Table row
                        val cells = line.split("|").filter { it.isNotBlank() }.map { it.trim() }
                        if (cells.isNotEmpty()) {
                            val isHeader = cells.all { it == it.uppercase() || lineIdx > 0 && lines.getOrNull(lineIdx - 1)?.startsWith("---") == true }
                            drawTableRow(canvas, cells, y, isHeader = false)
                            y += 18f
                        }
                    }
                    line.startsWith("Generated:") || line.startsWith("Total:") || line.startsWith("Items:") || line.startsWith("Entries:") || line.startsWith("Habits:") || line.startsWith("Avg ") -> {
                        canvas.drawText(line, MARGIN, y, subtitlePaint)
                        y += 18f
                    }
                    else -> {
                        // Auto-detect tabular data: lines with " | " separator
                        if (line.contains(" | ")) {
                            val cells = line.split(" | ").map { it.trim() }
                            drawTableRow(canvas, cells, y, isHeader = false)
                            y += 18f
                        } else {
                            canvas.drawText(line, MARGIN, y, cellPaint)
                            y += 15f
                        }
                    }
                }
                lineIdx++
            }

            // Footer
            canvas.drawText("Page $pageNum — Privo", MARGIN, PAGE_H.toFloat() - 15f, lightPaint)
            document.finishPage(page)
            pageNum++
        }

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    /**
     * Export structured table data as PDF.
     * @param title Report title
     * @param subtitle Additional info lines (shown before table)
     * @param headers Table column headers
     * @param rows Table data rows
     * @param summary Summary lines shown after table
     */
    fun exportTableToPdf(
        title: String,
        subtitle: List<String>,
        headers: List<String>,
        rows: List<List<String>>,
        summary: List<String> = emptyList(),
        outputFile: File
    ) {
        val document = PdfDocument()
        val colWidths = calculateColumnWidths(headers, rows)
        var pageNum = 1
        var rowIdx = 0
        var headerDrawn = false

        // First page: title + subtitle + summary + table header
        while (rowIdx <= rows.size) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            var y = MARGIN + 20f
            val maxY = PAGE_H - MARGIN - 20f

            if (pageNum == 1) {
                // Title
                canvas.drawText(title, MARGIN, y, titlePaint)
                y += 28f

                // Subtitle lines
                subtitle.forEach { line ->
                    canvas.drawText(line, MARGIN, y, subtitlePaint)
                    y += 16f
                }
                y += 8f

                // Summary before table
                if (summary.isNotEmpty()) {
                    summary.forEach { line ->
                        canvas.drawText(line, MARGIN, y, subtitlePaint)
                        y += 16f
                    }
                    canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
                    y += 12f
                }
            }

            // Table header (on each page)
            drawTableRowStyled(canvas, headers, y, colWidths, isHeader = true)
            y += 20f

            // Table rows
            while (rowIdx < rows.size && y < maxY) {
                val isAlternate = rowIdx % 2 == 1
                drawTableRowStyled(canvas, rows[rowIdx], y, colWidths, isHeader = false, alternate = isAlternate)
                y += 18f
                rowIdx++
            }

            // Footer
            canvas.drawText("Page $pageNum — Privo", MARGIN, PAGE_H.toFloat() - 15f, lightPaint)
            document.finishPage(page)
            pageNum++

            if (rowIdx >= rows.size) break
        }

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    private fun drawTableRow(canvas: Canvas, cells: List<String>, y: Float, isHeader: Boolean) {
        val tableWidth = PAGE_W - MARGIN * 2
        val colWidth = tableWidth / cells.size.coerceAtLeast(1)
        val paint = if (isHeader) headerPaint else cellPaint

        cells.forEachIndexed { i, cell ->
            val x = MARGIN + i * colWidth
            canvas.drawText(cell.take(25), x + 4f, y, paint)
        }
        // Bottom line
        canvas.drawLine(MARGIN, y + 4f, PAGE_W - MARGIN, y + 4f, linePaint)
    }

    private fun drawTableRowStyled(canvas: Canvas, cells: List<String>, y: Float, colWidths: List<Float>, isHeader: Boolean, alternate: Boolean = false) {
        val rowHeight = if (isHeader) 20f else 18f

        // Background
        if (isHeader) {
            canvas.drawRect(MARGIN, y - 14f, PAGE_W - MARGIN, y + rowHeight - 14f, headerBgPaint)
        } else if (alternate) {
            canvas.drawRect(MARGIN, y - 14f, PAGE_W - MARGIN, y + rowHeight - 14f, rowBgPaint)
        }

        // Cell text
        var x = MARGIN + 4f
        val paint = if (isHeader) headerPaint else cellPaint
        cells.forEachIndexed { i, cell ->
            val maxChars = (colWidths.getOrElse(i) { 100f } / (paint.textSize * 0.55f)).toInt().coerceAtLeast(3)
            canvas.drawText(cell.take(maxChars), x, y, paint)
            x += colWidths.getOrElse(i) { 100f }
        }

        // Bottom line
        canvas.drawLine(MARGIN, y + rowHeight - 14f, PAGE_W - MARGIN, y + rowHeight - 14f, linePaint)
    }

    private fun calculateColumnWidths(headers: List<String>, rows: List<List<String>>): List<Float> {
        val tableWidth = PAGE_W - MARGIN * 2
        val cols = headers.size.coerceAtLeast(1)

        // Calculate based on content: wider columns get more space
        val maxLengths = (0 until cols).map { col ->
            val headerLen = headers.getOrElse(col) { "" }.length
            val maxDataLen = rows.maxOfOrNull { it.getOrElse(col) { "" }.length } ?: 0
            maxOf(headerLen, maxDataLen).coerceIn(3, 30)
        }
        val totalLen = maxLengths.sum().toFloat().coerceAtLeast(1f)
        return maxLengths.map { (it / totalLen) * tableWidth }
    }
}
