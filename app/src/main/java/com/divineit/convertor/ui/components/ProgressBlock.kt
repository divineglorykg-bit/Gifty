package com.divineit.convertor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divineit.convertor.domain.progress.WorkProgress

/**
 * Shows live work progress. When the work is countable (PDF pages),
 * a determinate bar and a real percentage are shown. When it is a
 * single un-splittable step, an indeterminate bar with the label is
 * shown instead — never an invented percentage.
 */
@Composable
fun ProgressBlock(progress: WorkProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        val percent = progress.percent
        if (percent != null) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = progress.displayText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
