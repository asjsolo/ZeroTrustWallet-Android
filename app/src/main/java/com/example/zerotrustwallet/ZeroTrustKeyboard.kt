package com.example.zerotrustwallet


import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class KeyboardMode { ALPHABET, SYMBOLS }


@Composable
fun SecureKeyButton(
    keyChar: String,
    engine: KeystrokeFeatureExtractor,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .height(50.dp)
            .background(
                if (keyChar in listOf("DEL", "SPACE", "SHIFT", "?123", "ABC")) Color(0xFF1F618D) else Color(0xFF2E86C1),
                RoundedCornerShape(6.dp)
            )
            .pointerInput(keyChar) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (keyChar !in listOf("?123", "ABC", "SHIFT")) {
                        engine.recordKeyDown(keyChar)
                    }


                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        if (keyChar !in listOf("?123", "ABC", "SHIFT")) {
                            engine.recordKeyUp(keyChar)
                        }
                        onClick(keyChar)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = keyChar, color = Color.White, fontSize = if (keyChar.length > 1) 12.sp else 18.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
fun ZeroTrustKeyboard(
    engine: KeystrokeFeatureExtractor,
    onKeyPressed: (String) -> Unit
) {
    var isShifted by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.ALPHABET) }


    val alphaRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val alphaRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val alphaRow3 = listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL")
    val alphaRow4 = listOf("?123", ",", "SPACE", ".", "-")


    val symRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val symRow2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
    val symRow3 = listOf("=", "*", "\"", "'", ":", ";", "!", "?", "DEL")
    val symRow4 = listOf("ABC", ",", "SPACE", ".", "_")


    val currentRows = if (keyboardMode == KeyboardMode.ALPHABET)
        listOf(alphaRow1, alphaRow2, alphaRow3, alphaRow4)
    else
        listOf(symRow1, symRow2, symRow3, symRow4)


    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F9FF)).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        currentRows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val isAsdfRow = keyboardMode == KeyboardMode.ALPHABET && rowIndex == 1
                if (isAsdfRow) Spacer(modifier = Modifier.weight(0.5f))


                row.forEach { keyChar ->
                    val displayChar = if (keyChar.length == 1 && isShifted && keyChar[0].isLetter()) keyChar.uppercase() else keyChar
                    val weight = when (keyChar) {
                        "SPACE" -> 5f
                        "SHIFT", "DEL", "?123", "ABC" -> 1.5f
                        else -> 1f
                    }


                    SecureKeyButton(
                        keyChar = displayChar, engine = engine, modifier = Modifier.weight(weight),
                        onClick = { clickedKey ->
                            when (clickedKey) {
                                "SHIFT" -> isShifted = !isShifted
                                "?123" -> keyboardMode = KeyboardMode.SYMBOLS
                                "ABC" -> keyboardMode = KeyboardMode.ALPHABET
                                else -> {
                                    onKeyPressed(clickedKey)
                                    if (isShifted && clickedKey.length == 1) isShifted = false
                                }
                            }
                        }
                    )
                }


                if (isAsdfRow) Spacer(modifier = Modifier.weight(0.5f))
            }
        }
    }
}
