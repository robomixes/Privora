package com.privateai.camera.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// ===== Unit Data =====

private data class UnitDef(
    val name: String,
    val symbol: String,
    val toBase: (Double) -> Double,   // convert to base unit
    val fromBase: (Double) -> Double  // convert from base unit
)

private data class Category(
    val name: String,
    val emoji: String,
    val units: List<UnitDef>
)

private val CATEGORIES = listOf(
    Category("Length", "📏", listOf(
        UnitDef("Millimeter", "mm", { it / 1000 }, { it * 1000 }),
        UnitDef("Centimeter", "cm", { it / 100 }, { it * 100 }),
        UnitDef("Meter", "m", { it }, { it }),
        UnitDef("Kilometer", "km", { it * 1000 }, { it / 1000 }),
        UnitDef("Inch", "in", { it * 0.0254 }, { it / 0.0254 }),
        UnitDef("Foot", "ft", { it * 0.3048 }, { it / 0.3048 }),
        UnitDef("Yard", "yd", { it * 0.9144 }, { it / 0.9144 }),
        UnitDef("Mile", "mi", { it * 1609.344 }, { it / 1609.344 }),
    )),
    Category("Weight", "⚖️", listOf(
        UnitDef("Milligram", "mg", { it / 1_000_000 }, { it * 1_000_000 }),
        UnitDef("Gram", "g", { it / 1000 }, { it * 1000 }),
        UnitDef("Kilogram", "kg", { it }, { it }),
        UnitDef("Ounce", "oz", { it * 0.0283495 }, { it / 0.0283495 }),
        UnitDef("Pound", "lb", { it * 0.453592 }, { it / 0.453592 }),
        UnitDef("Ton", "t", { it * 1000 }, { it / 1000 }),
    )),
    Category("Temperature", "🌡️", listOf(
        UnitDef("Celsius", "°C", { it }, { it }),
        UnitDef("Fahrenheit", "°F", { (it - 32) * 5.0 / 9 }, { it * 9.0 / 5 + 32 }),
        UnitDef("Kelvin", "K", { it - 273.15 }, { it + 273.15 }),
    )),
    Category("Speed", "🏎️", listOf(
        UnitDef("m/s", "m/s", { it }, { it }),
        UnitDef("km/h", "km/h", { it / 3.6 }, { it * 3.6 }),
        UnitDef("mph", "mph", { it * 0.44704 }, { it / 0.44704 }),
        UnitDef("Knots", "kn", { it * 0.514444 }, { it / 0.514444 }),
    )),
    Category("Data", "💾", listOf(
        UnitDef("Byte", "B", { it }, { it }),
        UnitDef("Kilobyte", "KB", { it * 1024 }, { it / 1024 }),
        UnitDef("Megabyte", "MB", { it * 1_048_576 }, { it / 1_048_576 }),
        UnitDef("Gigabyte", "GB", { it * 1_073_741_824 }, { it / 1_073_741_824 }),
        UnitDef("Terabyte", "TB", { it * 1_099_511_627_776.0 }, { it / 1_099_511_627_776.0 }),
    )),
    Category("Area", "📐", listOf(
        UnitDef("Sq Meter", "m²", { it }, { it }),
        UnitDef("Sq Km", "km²", { it * 1_000_000 }, { it / 1_000_000 }),
        UnitDef("Sq Foot", "ft²", { it * 0.092903 }, { it / 0.092903 }),
        UnitDef("Acre", "ac", { it * 4046.86 }, { it / 4046.86 }),
        UnitDef("Hectare", "ha", { it * 10000 }, { it / 10000 }),
    )),
    Category("Volume", "🧪", listOf(
        UnitDef("Milliliter", "mL", { it / 1000 }, { it * 1000 }),
        UnitDef("Liter", "L", { it }, { it }),
        UnitDef("Gallon", "gal", { it * 3.78541 }, { it / 3.78541 }),
        UnitDef("Cup", "cup", { it * 0.236588 }, { it / 0.236588 }),
        UnitDef("Fl Oz", "fl oz", { it * 0.0295735 }, { it / 0.0295735 }),
    )),
    Category("Time", "⏱️", listOf(
        UnitDef("Second", "sec", { it }, { it }),
        UnitDef("Minute", "min", { it * 60 }, { it / 60 }),
        UnitDef("Hour", "hr", { it * 3600 }, { it / 3600 }),
        UnitDef("Day", "day", { it * 86400 }, { it / 86400 }),
        UnitDef("Week", "wk", { it * 604800 }, { it / 604800 }),
    )),
)

// ===== UI =====

private const val CONVERTER_PREFS = "unit_converter"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(onBack: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(CONVERTER_PREFS, android.content.Context.MODE_PRIVATE) }

    // Load last used category + units from prefs
    val savedCatIdx = remember { prefs.getInt("last_cat", 0).coerceIn(0, CATEGORIES.size - 1) }
    var selectedCatIdx by remember { mutableIntStateOf(savedCatIdx) }

    // Per-category saved from/to unit indices
    fun savedFrom(catIdx: Int) = prefs.getInt("cat_${catIdx}_from", 0).coerceIn(0, CATEGORIES[catIdx].units.size - 1)
    fun savedTo(catIdx: Int) = prefs.getInt("cat_${catIdx}_to", 1.coerceAtMost(CATEGORIES[catIdx].units.size - 1)).coerceIn(0, CATEGORIES[catIdx].units.size - 1)

    var fromUnitIdx by remember { mutableIntStateOf(savedFrom(savedCatIdx)) }
    var toUnitIdx by remember { mutableIntStateOf(savedTo(savedCatIdx)) }
    var inputValue by remember { mutableStateOf(prefs.getString("last_input", "1") ?: "1") }
    var showFromDropdown by remember { mutableStateOf(false) }
    var showToDropdown by remember { mutableStateOf(false) }

    // Sort categories: last used first
    val categoryOrder = remember(selectedCatIdx) {
        val order = (0 until CATEGORIES.size).toMutableList()
        order.remove(savedCatIdx)
        order.add(0, savedCatIdx)
        order
    }

    // Save state on changes
    fun saveState() {
        prefs.edit()
            .putInt("last_cat", selectedCatIdx)
            .putInt("cat_${selectedCatIdx}_from", fromUnitIdx)
            .putInt("cat_${selectedCatIdx}_to", toUnitIdx)
            .putString("last_input", inputValue)
            .apply()
    }

    val category = CATEGORIES[selectedCatIdx]
    val fromUnit = category.units[fromUnitIdx.coerceIn(0, category.units.size - 1)]
    val toUnit = category.units[toUnitIdx.coerceIn(0, category.units.size - 1)]

    // Instant conversion
    val inputNum = inputValue.toDoubleOrNull()
    val result = if (inputNum != null) {
        val baseValue = fromUnit.toBase(inputNum)
        val converted = toUnit.fromBase(baseValue)
        if (converted == converted.toLong().toDouble() && kotlin.math.abs(converted) < 1_000_000_000)
            converted.toLong().toString()
        else
            "%.6f".format(converted).trimEnd('0').trimEnd('.')
    } else ""

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Unit Converter") },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Category chips — fixed at top, not scrollable with content
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryOrder.forEach { i ->
                    val cat = CATEGORIES[i]
                    FilterChip(
                        selected = selectedCatIdx == i,
                        onClick = {
                            saveState()
                            selectedCatIdx = i
                            fromUnitIdx = savedFrom(i)
                            toUnitIdx = savedTo(i)
                            saveState()
                        },
                        label = { Text("${cat.emoji} ${cat.name}") }
                    )
                }
            }

            // Scrollable content
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            // From
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.headlineSmall
                        )
                        // Unit selector
                        OutlinedButton(onClick = { showFromDropdown = true }) {
                            Text(fromUnit.symbol)
                            DropdownMenu(expanded = showFromDropdown, onDismissRequest = { showFromDropdown = false }) {
                                category.units.forEachIndexed { i, unit ->
                                    DropdownMenuItem(
                                        text = { Text("${unit.name} (${unit.symbol})") },
                                        onClick = { fromUnitIdx = i; showFromDropdown = false; saveState() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Swap button
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = {
                    val tmp = fromUnitIdx; fromUnitIdx = toUnitIdx; toUnitIdx = tmp; saveState()
                }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SwapVert, "Swap", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            // To (result)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("To", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = result.ifEmpty { "—" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { showToDropdown = true }) {
                            Text(toUnit.symbol)
                            DropdownMenu(expanded = showToDropdown, onDismissRequest = { showToDropdown = false }) {
                                category.units.forEachIndexed { i, unit ->
                                    DropdownMenuItem(
                                        text = { Text("${unit.name} (${unit.symbol})") },
                                        onClick = { toUnitIdx = i; showToDropdown = false; saveState() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick conversions — show all units for current input
            if (inputNum != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("All conversions", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        val baseValue = fromUnit.toBase(inputNum)
                        category.units.forEach { unit ->
                            val conv = unit.fromBase(baseValue)
                            val display = if (conv == conv.toLong().toDouble() && kotlin.math.abs(conv) < 1_000_000_000)
                                conv.toLong().toString()
                            else "%.6f".format(conv).trimEnd('0').trimEnd('.')
                            Row(Modifier.fillMaxWidth().clickable {
                                toUnitIdx = category.units.indexOf(unit)
                            }.padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(unit.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$display ${unit.symbol}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            } // end scrollable content
        } // end outer column
    }
}
