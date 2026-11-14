package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desktop layout primitives (UI-UX pass, 2026-11-13).
 *
 * The ported Android screens are single-column `fillMaxWidth` forms: on a
 * 1280dp-wide window they stretch edge-to-edge like a zoomed phone app.
 * These wrappers give every workspace the same desktop reading geometry
 * WITHOUT touching the screens' internals:
 *
 *  - [DesktopContentPane] — the standard workspace frame: a centered column
 *    capped at readable width (two margins of empty space on wide windows
 *    instead of stretched widgets). Scroll behavior stays inside the screen.
 *  - [DesktopTwoPane]    — master/detail layout for the screens that have a
 *    natural list+detail split: fixed-width control pane on the left,
 *    flexible work surface on the right.
 *
 * Both degrade gracefully below the compact breakpoint (the window minimum
 * is 1024dp wide, so the panes simply share the space more tightly).
 */

/** Max readable column width; beyond this content centers with margins. */
private val ContentMaxWidth = 980.dp

/** Default master pane width for two-pane workspaces. */
private val DefaultMasterWidth = 400.dp

/**
 * The standard desktop workspace frame: centers a readable column. Wrap the
 * screen's existing root column/box — no internal changes needed.
 */
@Composable
fun DesktopContentPane(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = ContentMaxWidth)
                .padding(horizontal = horizontalPadding, vertical = 14.dp)
        ) {
            content()
        }
    }
}

/**
 * Master/detail two-pane workspace. [master] gets a bounded rail (lists,
 * pickers, settings); [detail] fills the rest. On narrow windows the master
 * pane shrinks first (it is the one that already scrolls internally).
 */
@Composable
fun DesktopTwoPane(
    modifier: Modifier = Modifier,
    masterWidth: Dp = DefaultMasterWidth,
    master: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 320.dp, max = masterWidth)
        ) {
            master()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 12.dp)
        ) {
            detail()
        }
    }
}

/**
 * Section header used inside [DesktopContentPane]: desktop apps separate
 * groups with quiet typographic headers rather than mobile-style filled
 * chips. Place between cards/controls.
 */
@Composable
fun DesktopSectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .fillMaxWidth(0.08f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
