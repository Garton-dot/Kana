package com.garton.kana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garton.kana.data.Mnemonics

/**
 * One line per kanji in [text]: its keyword and story, the learner's own story when they wrote one.
 * With [editable], each line gets a button to write or change that story.
 */
@Composable
fun MnemonicPanel(vm: KanaViewModel, text: String, editable: Boolean, compact: Boolean = false, maxKanji: Int = 4) {
    val kanji = Mnemonics.kanjiIn(text).take(maxKanji)
    if (kanji.isEmpty()) return
    var editing by remember { mutableStateOf<String?>(null) }
    val own = vm.progress.mnemonics

    Column(
        Modifier
            .fillMaxWidth()
            .background(KanaColors.Indigo.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        for (k in kanji) {
            val note = Mnemonics.of(k)
            val mine = own[k]
            Row(verticalAlignment = Alignment.Top) {
                Text(k, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Bold, color = KanaColors.Indigo)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    note?.let { Text(it.keyword, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp) }
                    val story = mine ?: note?.story
                    if (story != null) {
                        Text(
                            (if (mine != null) "✏️ " else "") + story,
                            fontSize = if (compact) 13.sp else 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else if (editable) {
                        Text("No story yet. Write your own!", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (editable) {
                    TextButton(onClick = { editing = k }) { Text(if (mine == null) "Write own" else "Edit", fontSize = 13.sp) }
                }
            }
        }
    }

    editing?.let { k ->
        var draft by remember(k) { mutableStateOf(own[k] ?: "") }
        val note = Mnemonics.of(k)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Your story for $k" + (note?.let { " (${it.keyword})" } ?: "")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    note?.let { Text("Built-in: ${it.story}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("A picture that makes $k stick for you") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (own[k] != null) {
                        Text("Leave it empty to go back to the built-in story.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.setMnemonic(k, draft); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
