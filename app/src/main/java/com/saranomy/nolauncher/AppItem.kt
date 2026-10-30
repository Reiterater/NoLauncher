package com.saranomy.nolauncher

import android.content.ComponentName
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class AppItem(
    val name: String,
    val packageName: String,
    val component: ComponentName
) : Comparable<AppItem> {
    val key: String get() = component.flattenToString()

    override fun compareTo(other: AppItem): Int {
        return name.compareTo(other.name, true)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Display(
        checked: Boolean?,
        spacing: Int,
        showIcon: Boolean,
        rightAlign: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onDoubleClick: (() -> Unit)? = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick
                )
                .padding(horizontal = 16.dp, vertical = spacing.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rightAlign) {
                if (checked != null) {
                    Checkbox(checked = checked, onCheckedChange = null)
                }
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = if (showIcon) 12.dp else 0.dp)
                )
                if (showIcon) {
                    NoLauncher.icons[key]?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                if (showIcon) {
                    NoLauncher.icons[key]?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (showIcon) 12.dp else 0.dp, end = 12.dp)
                )
                if (checked != null) {
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
