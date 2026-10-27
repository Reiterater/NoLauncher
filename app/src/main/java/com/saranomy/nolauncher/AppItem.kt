package com.saranomy.nolauncher

import android.content.ComponentName
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    fun Display(checked: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                        NoLauncher.icons[key]?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            if (checked != null) {
                Checkbox(checked = checked, onCheckedChange = null)
            }
        }
    }
}
