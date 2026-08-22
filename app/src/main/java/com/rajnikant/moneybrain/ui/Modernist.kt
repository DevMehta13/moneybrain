package com.rajnikant.moneybrain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rajnikant.moneybrain.ui.theme.LocalModernist
import com.rajnikant.moneybrain.ui.theme.ModernistColors
import java.util.Locale

/*
 * Shared Modernist building blocks (design_handoff_moneybrain_ui/README.md §2):
 * structure comes from rules and borders, never elevation.
 */

@Composable
fun mb(): ModernistColors = LocalModernist.current

/** The 2px section rule. */
@Composable
fun SectionRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(mb().rule))
}

/** The 1px row rule. */
@Composable
fun RowRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(mb().ruleFaint))
}

/** Uppercase kicker: 11/800, wide tracking, muted unless a colour is given. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = mb().muted) {
    Text(text.uppercase(Locale.ENGLISH), modifier, color = color, style = MaterialTheme.typography.labelMedium)
}

/** Uppercase section header (13/800) with an optional trailing element on the same baseline. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(text.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.titleMedium)
        trailing?.invoke()
    }
}

/** Small red action text (EDIT PLAN, SEE ALL, UNDO…): 11/600 caps in the deep accent. */
@Composable
fun RedLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text.uppercase(Locale.ENGLISH),
        modifier.clickable(onClick = onClick),
        color = mb().accentDeep,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.06.em,
    )
}

/** Underlined attention link (12/600) in the deep accent. */
@Composable
fun AttentionLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clickable(onClick = onClick),
        color = mb().accentDeep,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
    )
}

/** Small uppercase chip: solid background, or a dashed outline when `dashed`. */
@Composable
fun MbTag(text: String, background: Color, contentColor: Color, dashed: Boolean = false) {
    val base = if (dashed) {
        val stroke = contentColor
        Modifier.drawBehind {
            drawRect(stroke, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
        }
    } else {
        Modifier.background(background)
    }
    Text(
        text.uppercase(Locale.ENGLISH),
        base.padding(horizontal = 7.dp, vertical = 2.dp),
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
    )
}

/** 6dp flat progress bar: ink fill on the track; solid red when overspent. */
@Composable
fun BucketBar(fraction: Float, over: Boolean, modifier: Modifier = Modifier) {
    val m = mb()
    Box(modifier.fillMaxWidth().height(6.dp).background(m.track)) {
        Box(
            Modifier
                .fillMaxWidth(if (over) 1f else fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(if (over) m.accent else m.ink),
        )
    }
}

/** Kicker + big number stat cell (OWED TO YOU / YOU OWE / ACTIVE TRIP). */
@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(Locale.ENGLISH), color = mb().muted, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.08.em))
        Text(value, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleLarge)
    }
}

/** Full-width red action bar (ADD TRANSACTION). */
@Composable
fun ActionBar(label: String, glyph: String, onClick: () -> Unit) {
    val m = mb()
    Row(
        Modifier.fillMaxWidth().background(m.accent).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(Locale.ENGLISH), color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(glyph, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Normal)
    }
}

/** Settings-style row: title + sub on the left, a trailing element (arrow/tag) right. */
@Composable
fun MbListRow(title: String, sub: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp))
            if (sub != null) Text(sub, color = mb().faint, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.padding(start = 10.dp))
        if (trailing != null) trailing() else Text("→", fontSize = 14.sp)
    }
}
