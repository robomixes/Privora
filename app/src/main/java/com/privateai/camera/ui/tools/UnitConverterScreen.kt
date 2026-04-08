package com.privateai.camera.ui.tools

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.privateai.camera.R

// ===== Unit Data =====

private data class UnitDef(
    val name: String,
    val symbol: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double
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
    Category("Temp", "🌡️", listOf(
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

// Brand colors
private val AccentTeal = Color(0xFF00897B)
private val AccentTealLight = Color(0xFFE0F2F1)
private val SurfaceLight = Color(0xFFF5F5F5)

// ===== UI =====

private const val CONVERTER_PREFS = "unit_converter"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences(CONVERTER_PREFS, android.content.Context.MODE_PRIVATE) }

    val savedCatIdx = remember { prefs.getInt("last_cat", 0).coerceIn(0, CATEGORIES.size - 1) }
    var selectedCatIdx by remember { mutableIntStateOf(savedCatIdx) }

    fun savedFrom(catIdx: Int) = prefs.getInt("cat_${catIdx}_from", 0).coerceIn(0, CATEGORIES[catIdx].units.size - 1)
    fun savedTo(catIdx: Int) = prefs.getInt("cat_${catIdx}_to", 1.coerceAtMost(CATEGORIES[catIdx].units.size - 1)).coerceIn(0, CATEGORIES[catIdx].units.size - 1)

    var fromUnitIdx by remember { mutableIntStateOf(savedFrom(savedCatIdx)) }
    var toUnitIdx by remember { mutableIntStateOf(savedTo(savedCatIdx)) }
    var inputValue by remember { mutableStateOf(prefs.getString("last_input", "1") ?: "1") }
    var showFromDropdown by remember { mutableStateOf(false) }
    var showToDropdown by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }

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

    val inputNum = inputValue.toDoubleOrNull()
    val result = if (inputNum != null) {
        val baseValue = fromUnit.toBase(inputNum)
        val converted = toUnit.fromBase(baseValue)
        formatNumber(converted)
    } else ""

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.unit_converter_title)) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Category tabs — scrollable pill-style
            ScrollableTabRow(
                selectedTabIndex = selectedCatIdx,
                edgePadding = 12.dp,
                divider = {},
                indicator = {},
                containerColor = Color.Transparent
            ) {
                CATEGORIES.forEachIndexed { i, cat ->
                    val selected = selectedCatIdx == i
                    val bgColor by animateColorAsState(if (selected) AccentTeal else SurfaceLight, label = "tab")
                    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    Tab(
                        selected = selected,
                        onClick = {
                            saveState()
                            selectedCatIdx = i
                            fromUnitIdx = savedFrom(i)
                            toUnitIdx = savedTo(i)
                            saveState()
                        },
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor, RoundedCornerShape(20.dp))
                    ) {
                        Text(
                            "${cat.emoji} ${cat.name}",
                            color = textColor,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Scrollable content
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Unified conversion card
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        // FROM section
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("FROM", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                // Input field with inline unit
                                val inputBorderColor by animateColorAsState(
                                    if (isInputFocused) AccentTeal else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), label = "border"
                                )
                                Box(
                                    Modifier.weight(1f)
                                        .border(1.5.dp, inputBorderColor, RoundedCornerShape(12.dp))
                                        .background(if (isInputFocused) AccentTealLight.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        BasicTextField(
                                            value = inputValue,
                                            onValueChange = { inputValue = it.filter { c -> c.isDigit() || c == '.' || c == '-' }; saveState() },
                                            modifier = Modifier.weight(1f).onFocusChanged { isInputFocused = it.isFocused },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                                            cursorBrush = SolidColor(AccentTeal)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        // Unit selector chip
                                        Box {
                                            Text(
                                                fromUnit.symbol,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SurfaceLight, RoundedCornerShape(8.dp))
                                                    .clickable { showFromDropdown = true }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = AccentTeal,
                                                fontWeight = FontWeight.Bold
                                            )
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
                        }

                        // Swap button on divider
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            IconButton(
                                onClick = { val tmp = fromUnitIdx; fromUnitIdx = toUnitIdx; toUnitIdx = tmp; saveState() },
                                modifier = Modifier.size(44.dp).background(AccentTeal, CircleShape)
                            ) {
                                Icon(Icons.Default.SwapVert, stringResource(R.string.unit_converter_swap), Modifier.size(24.dp), tint = Color.White)
                            }
                        }

                        // TO section (result)
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("TO", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.weight(1f)
                                        .background(AccentTealLight, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = result.ifEmpty { "—" },
                                            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                                            color = AccentTeal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box {
                                            Text(
                                                toUnit.symbol,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White, RoundedCornerShape(8.dp))
                                                    .clickable { showToDropdown = true }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = AccentTeal,
                                                fontWeight = FontWeight.Bold
                                            )
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
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // All conversions — card-based results
                if (inputNum != null) {
                    Text(
                        stringResource(R.string.unit_converter_all_conversions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val baseValue = fromUnit.toBase(inputNum)
                    category.units.forEach { unit ->
                        val conv = unit.fromBase(baseValue)
                        val display = formatNumber(conv)
                        val isSelected = unit.symbol == toUnit.symbol

                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clickable { toUnitIdx = category.units.indexOf(unit); saveState() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AccentTealLight else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        unit.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            display,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) AccentTeal else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            unit.symbol,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // Copy button
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString("$display ${unit.symbol}"))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy, "Copy",
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1_000_000_000)
        value.toLong().toString()
    else
        "%.6f".format(value).trimEnd('0').trimEnd('.')
}
