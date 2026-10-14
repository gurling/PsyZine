// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class PsyZineNavigationItem(val name: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PsyZineNavigationScaffold(
    layoutType: NavigationSuiteType,
    items: List<PsyZineNavigationItem>,
    content: @Composable () -> Unit
) {
    BoxWithConstraints {
        val density = LocalDensity.current
        val style = MaterialTheme.typography.labelMedium
        val measurer = rememberTextMeasurer()
        val labelWidth = items.maxOfOrNull { measurer.measure(it.name, style = style, softWrap = false).size.width } ?: 0
        val itemWidth = with(density) { labelWidth.toDp() } + 16.dp
        if (layoutType == NavigationSuiteType.NavigationBar && itemWidth * items.size > maxWidth) {
            // Keep the chosen font size and full labels. Every tab remains reachable by
            // scrolling, keyboard focus and accessibility focus on its navigation item.
            Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                val height = maxOf(80.dp, with(density) { style.lineHeight.toDp() } + 52.dp)
                NavigationBar(Modifier.horizontalScroll(rememberScrollState())
                    .width(itemWidth * items.size + 72.dp).heightIn(min = height)) {
                    items.forEach { item ->
                        val bring = remember { BringIntoViewRequester() }
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(item.selected) {
                            if (item.selected) { withFrameNanos { }; bring.bringIntoView() }
                        }
                        NavigationBarItem(
                            modifier = Modifier.width(itemWidth).height(height).bringIntoViewRequester(bring).onFocusChanged {
                                if (it.isFocused) scope.launch { bring.bringIntoView() }
                            },
                            selected = item.selected,
                            onClick = item.onClick,
                            icon = { Icon(item.icon, contentDescription = item.name) },
                            label = { Text(item.name, style = style, maxLines = 1) }
                        )
                    }
                }
            }) { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() } }
        } else {
            NavigationSuiteScaffold(layoutType = layoutType, navigationSuiteItems = {
                items.forEach { item ->
                    item(selected = item.selected, onClick = item.onClick,
                        icon = { Icon(item.icon, contentDescription = item.name) }, label = { Text(item.name) })
                }
            }, content = content)
        }
    }
}
