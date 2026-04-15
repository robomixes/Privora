package com.privateai.camera.ui.insights

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.EXPENSE_CATEGORIES
import com.privateai.camera.security.Expense
import com.privateai.camera.security.InsightsRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CAT_COLORS = listOf(
    Color(0xFFE57373), Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784),
    Color(0xFFBA68C8), Color(0xFF4DD0E1), Color(0xFFFF8A65), Color(0xFF90A4AE)
)

@Composable
fun ExpensesTab(repo: InsightsRepository, selectedProfileId: String = SELF_PROFILE_ID) {
    val context = LocalContext.current
    var allExpenses by remember(selectedProfileId) {
        mutableStateOf(repo.listExpenses().filter { it.profileId == selectedProfileId })
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    // Month navigation
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    val monthNames = remember { listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec") }

    // Filter by selected month
    val monthStart = remember(selectedYear, selectedMonth) {
        Calendar.getInstance().apply { set(selectedYear, selectedMonth, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
    val monthEnd = remember(selectedYear, selectedMonth) {
        Calendar.getInstance().apply { set(selectedYear, selectedMonth + 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis - 1
    }
    val expenses = allExpenses.filter { it.date in monthStart..monthEnd }
    val monthTotal = expenses.sumOf { it.amount }

    val catTotals = EXPENSE_CATEGORIES.mapIndexed { i, cat ->
        Triple(cat, expenses.filter { it.category == cat }.sumOf { it.amount }.toFloat(), CAT_COLORS[i % CAT_COLORS.size])
    }.filter { it.second > 0 }

    if (showAddDialog) {
        AddExpenseDialog(onDismiss = { showAddDialog = false }, onSave = { expense ->
            // Tag with currently-selected profile so it shows up when filter applied
            repo.saveExpense(expense.copy(profileId = selectedProfileId))
            allExpenses = repo.listExpenses().filter { it.profileId == selectedProfileId }
            showAddDialog = false
        })
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Month navigator
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (selectedMonth == 0) { selectedMonth = 11; selectedYear-- } else selectedMonth--
                    }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.action_previous)) }
                    Text("${monthNames[selectedMonth]} $selectedYear", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = {
                        if (selectedMonth == 11) { selectedMonth = 0; selectedYear++ } else selectedMonth++
                    }) { Icon(Icons.Default.ChevronRight, stringResource(R.string.action_next)) }
                }
            }

            // Total + PDF export
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(stringResource(R.string.expenses_total), style = MaterialTheme.typography.labelSmall)
                            Text("${"%.2f".format(monthTotal)}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.expenses_count, expenses.size), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.PictureAsPdf, stringResource(R.string.action_export_pdf), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Pie chart
            if (catTotals.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.expenses_by_category), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            PieChart(catTotals, Modifier.fillMaxWidth().height(140.dp))
                            Spacer(Modifier.height(8.dp))
                            catTotals.forEach { (label, value, color) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(12.dp).background(color, CircleShape))
                                    Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            if (expenses.isEmpty()) {
                item { Text(stringResource(R.string.expenses_none_this_month), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
            }

            // List
            items(expenses) { expense ->
                val ci = EXPENSE_CATEGORIES.indexOf(expense.category).coerceAtLeast(0)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(CAT_COLORS[ci % CAT_COLORS.size], RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Text(expense.category.first().toString(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(expense.description.ifEmpty { expense.category }, style = MaterialTheme.typography.bodyLarge)
                            Text("${expense.category} • ${dateFormat.format(Date(expense.date))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${"%.2f".format(expense.amount)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { repo.deleteExpense(expense.id); allExpenses = repo.listExpenses().filter { it.profileId == selectedProfileId } }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, stringResource(R.string.action_add))
        }

        if (showExportDialog) {
            val (sub, hdr, rws) = remember(expenses) { repo.generateExpenseReportTable(expenses) }
            ExportDialog(
                context = context,
                fileName = "expense_report_${monthNames[selectedMonth]}_$selectedYear",
                onDismiss = { showExportDialog = false },
                title = "Expense Report — ${monthNames[selectedMonth]} $selectedYear",
                subtitle = sub,
                headers = hdr,
                rows = rws
            )
        }
    }
}

@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("Food") }
    val cal = remember { Calendar.getInstance() }
    var selYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var selDay by remember { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    val dateDisplay = "%04d-%02d-%02d".format(selYear, selMonth + 1, selDay)

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.expenses_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.label_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    androidx.compose.material3.OutlinedButton(onClick = {
                        android.app.DatePickerDialog(context, { _, y, m, d -> selYear = y; selMonth = m; selDay = d }, selYear, selMonth, selDay).show()
                    }, modifier = Modifier.weight(1f)) { Text("📅 $dateDisplay") }
                }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.label_description)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    EXPENSE_CATEGORIES.take(4).forEachIndexed { i, cat ->
                        Box(Modifier.weight(1f).background(if (selectedCat == cat) CAT_COLORS[i] else Color.Transparent, RoundedCornerShape(8.dp)).clickable { selectedCat = cat }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(cat, style = MaterialTheme.typography.labelSmall, color = if (selectedCat == cat) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    EXPENSE_CATEGORIES.drop(4).forEachIndexed { i, cat ->
                        Box(Modifier.weight(1f).background(if (selectedCat == cat) CAT_COLORS[i + 4] else Color.Transparent, RoundedCornerShape(8.dp)).clickable { selectedCat = cat }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(cat, style = MaterialTheme.typography.labelSmall, color = if (selectedCat == cat) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            amount.toDoubleOrNull()?.let { amt ->
                val entryDate = Calendar.getInstance().apply { set(selYear, selMonth, selDay, 12, 0, 0) }.timeInMillis
                onSave(Expense(amount = amt, category = selectedCat, description = description, date = entryDate))
            }
        }, enabled = amount.toDoubleOrNull() != null) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
