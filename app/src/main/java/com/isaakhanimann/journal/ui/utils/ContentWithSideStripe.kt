// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Measure wrapping content normally, then match the decorative stripe to its height. */
@Composable
fun ContentWithSideStripe(
    stripeWidth: Dp,
    modifier: Modifier = Modifier,
    stripe: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier) {
        Column(Modifier.padding(start = stripeWidth + 10.dp), content = content)
        Box(Modifier.matchParentSize()) { stripe() }
    }
}
