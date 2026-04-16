package com.privateai.camera.ui.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Functional calculator that doubles as the disguise entry point.
 *
 * Typing the user's PIN followed by "=" silently launches Privora.
 * All other operations behave like a normal calculator.
 * Wrong PIN → shows "0" with no error feedback.
 */
@Composable
/**
 * @param appPin The app's unlock PIN. If null, secret unlock is disabled.
 * @param onNormalUnlock Called when digits match the normal app PIN.
 * @param isDuressPin Checker for duress PIN (hashed, so can't compare directly).
 * @param onDuressUnlock Called when digits match the duress PIN.
 */
fun CalculatorScreen(
    appPin: String?,
    onNormalUnlock: () -> Unit,
    isDuressPin: (String) -> Boolean = { false },
    onDuressUnlock: () -> Unit = {}
) {
    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var operator by remember { mutableStateOf<String?>(null) }
    var waitingForOperand2 by remember { mutableStateOf(false) }
    // Hidden buffer: accumulates digits typed. If it matches PIN when "=" is pressed → unlock.
    var digitBuffer by remember { mutableStateOf("") }

    fun appendDigit(d: String) {
        digitBuffer += d
        if (waitingForOperand2 || display == "0" || display == "Error") {
            display = d
            waitingForOperand2 = false
        } else {
            if (display.length < 12) display += d
        }
    }

    fun appendDot() {
        if ("." !in display) display += "."
    }

    fun setOperator(op: String) {
        operand1 = display.toDoubleOrNull()
        operator = op
        waitingForOperand2 = true
    }

    fun calculate(): Double? {
        val a = operand1 ?: return display.toDoubleOrNull()
        val b = display.toDoubleOrNull() ?: return null
        return when (operator) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else null
            "%" -> a * b / 100.0
            else -> b
        }
    }

    fun pressEquals() {
        // Secret unlock: check digit buffer against normal PIN and duress PIN
        if (digitBuffer.length >= 4) {
            if (appPin != null && digitBuffer == appPin) {
                onNormalUnlock()
                return
            }
            if (isDuressPin(digitBuffer)) {
                onDuressUnlock()
                return
            }
        }
        // Normal calculator equals
        val result = calculate()
        display = if (result == null) {
            "Error"
        } else {
            formatResult(result)
        }
        operand1 = result
        operator = null
        waitingForOperand2 = false
        digitBuffer = ""
    }

    fun clear() {
        display = "0"
        operand1 = null
        operator = null
        waitingForOperand2 = false
        digitBuffer = ""
    }

    fun toggleSign() {
        val v = display.toDoubleOrNull() ?: return
        display = formatResult(-v)
    }

    fun percent() {
        val v = display.toDoubleOrNull() ?: return
        display = formatResult(v / 100.0)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1C1C1E) // Dark calculator background
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Display
            Text(
                text = display,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                textAlign = TextAlign.End,
                fontSize = if (display.length > 8) 42.sp else 56.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Button grid
            val btnRows = listOf(
                listOf("C" to Color(0xFFA5A5A5), "±" to Color(0xFFA5A5A5), "%" to Color(0xFFA5A5A5), "÷" to Color(0xFFFF9500)),
                listOf("7" to Color(0xFF333333), "8" to Color(0xFF333333), "9" to Color(0xFF333333), "×" to Color(0xFFFF9500)),
                listOf("4" to Color(0xFF333333), "5" to Color(0xFF333333), "6" to Color(0xFF333333), "−" to Color(0xFFFF9500)),
                listOf("1" to Color(0xFF333333), "2" to Color(0xFF333333), "3" to Color(0xFF333333), "+" to Color(0xFFFF9500)),
                listOf("0" to Color(0xFF333333), "." to Color(0xFF333333), "=" to Color(0xFFFF9500))
            )

            btnRows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, bg) ->
                        val weight = if (label == "0") 2.1f else 1f
                        CalcButton(
                            label = label,
                            bg = bg,
                            textColor = if (bg == Color(0xFFA5A5A5)) Color.Black else Color.White,
                            modifier = Modifier.weight(weight).height(72.dp),
                            onClick = {
                                when (label) {
                                    "C" -> clear()
                                    "±" -> toggleSign()
                                    "%" -> percent()
                                    "." -> appendDot()
                                    "=" -> pressEquals()
                                    "+", "−", "×", "÷" -> setOperator(label)
                                    else -> appendDigit(label)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    bg: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

private fun formatResult(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        "%.8g".format(value)
    }
}
