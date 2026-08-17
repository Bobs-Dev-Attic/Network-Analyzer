package com.bobsdevattic.networkanalyzer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.network.WifiBand

private val ChipShape = RoundedCornerShape(6.dp)

/**
 * Tinted status pill. The glyph is not decoration: PASS-green and FAULT-red are exactly
 * the pair red-green colour blindness collapses, so every chip carries a glyph and a
 * text label and stays readable with colour disregarded.
 */
@Composable
fun StatusChip(
    label: String,
    colors: StatusColor,
    modifier: Modifier = Modifier,
    glyph: String? = null,
) {
    Box(
        modifier
            .clip(ChipShape)
            .background(colors.container)
            .border(1.dp, colors.fg.copy(alpha = 0.5f), ChipShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = if (glyph != null) "$glyph $label" else label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.fg,
        )
    }
}

/** Convenience overload: chip coloured and glyphed straight from a [Status]. */
@Composable
fun StatusChip(label: String, status: Status, modifier: Modifier = Modifier) {
    StatusChip(label = label, colors = status.colors(), modifier = modifier, glyph = status.glyph)
}

/**
 * Four-bar strength meter. Height ramp carries the reading on its own; colour is a
 * second, redundant channel.
 */
@Composable
fun SignalBars(bars: Int, modifier: Modifier = Modifier) {
    val on = statusOfSignalBars(bars).colors().fg
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(3.dp)
                    .height((4 + i * 3).dp)
                    .background(if (i <= bars) on else off, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
fun BandChip(band: WifiBand, modifier: Modifier = Modifier) {
    StatusChip(label = band.label, colors = band.colors(), modifier = modifier)
}
