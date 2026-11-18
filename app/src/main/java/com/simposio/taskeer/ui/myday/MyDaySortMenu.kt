package com.simposio.taskeer.ui.myday

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun MyDaySortMenu(sort: MyDaySort, onSort: (MyDaySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(
            icon = Icons.Filled.SwapVert,
            contentDescription = stringResource(R.string.my_day_sort_menu),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MyDaySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    trailingIcon = { if (option == sort) DecorativeIcon(Icons.Filled.Check) },
                    onClick = {
                        expanded = false
                        onSort(option)
                    },
                )
            }
        }
    }
}

@StringRes
private fun MyDaySort.labelRes(): Int = when (this) {
    MyDaySort.DUE_DATE -> R.string.my_day_sort_due_date
    MyDaySort.PRIORITY -> R.string.my_day_sort_priority
    MyDaySort.MARKED_AT -> R.string.my_day_sort_marked_at
}

@Preview(showBackground = true)
@Composable
private fun MyDaySortMenuPreview() {
    TaskeerTheme { MyDaySortMenu(sort = MyDaySort.PRIORITY, onSort = {}) }
}
