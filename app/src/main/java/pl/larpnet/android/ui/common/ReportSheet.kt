package pl.larpnet.android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.larpnet.android.R

private val REPORT_CATEGORIES = listOf("spam", "violation", "other")

@Composable
private fun reportCategoryLabel(category: String): String = when (category) {
    "spam" -> stringResource(R.string.report_category_spam)
    "violation" -> stringResource(R.string.report_category_violation)
    else -> stringResource(R.string.report_category_other)
}

/** Reusable for both post-report and account-report -- the caller decides which by whether the
 * [ReportTarget][pl.larpnet.android.ui.moderation.ReportTarget] it built carries any status ids. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    accountAcct: String,
    onDismiss: () -> Unit,
    onSubmit: (category: String, comment: String) -> Unit,
) {
    var category by remember { mutableStateOf(REPORT_CATEGORIES.first()) }
    var comment by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.report_title, accountAcct), style = MaterialTheme.typography.titleMedium)

            Column(modifier = Modifier.selectableGroup().padding(top = 12.dp)) {
                REPORT_CATEGORIES.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = category == option, onClick = { category = option }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = category == option, onClick = { category = option })
                        Text(reportCategoryLabel(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.report_comment_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                maxLines = 4,
            )

            Button(
                onClick = { onSubmit(category, comment) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.report_submit))
            }
        }
    }
}
