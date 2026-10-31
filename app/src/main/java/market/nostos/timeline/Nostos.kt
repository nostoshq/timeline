// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** The addresses Timeline points people to. One place, so a move is one change. */
object Links {
    const val STORE = "https://nostos.market"
    const val STORE_SHORT = "nostos.market"
    const val SOURCE = "https://github.com/nostoshq/timeline"
    const val SOURCE_SHORT = "github.com/nostoshq/timeline"
    const val CONSOLE_FILES = "$SOURCE/blob/main/docs/console-files.md"

    /** Opens an address in the device's browser. False when there is none to open it. */
    fun open(ctx: Context, url: String): Boolean = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/**
 * The QR code for the store's site, made once with macOS's own generator (CoreImage, error correction Q) and checked
 * by reading it back. Drawn module by module, so it stays sharp at any size and needs no library.
 */
object StoreQr {
    val rows = listOf(
        "11111110111000111000101111111",
        "10000010100010101001101000001",
        "10111010101000110000101011101",
        "10111010110101101011101011101",
        "10111010110010111101001011101",
        "10000010011001010011101000001",
        "11111110101010101010101111111",
        "00000000101001011000100000000",
        "01101011001111100001101011111",
        "01100101100001000101110011001",
        "00000011000000110001100000011",
        "11000000001100110100111010010",
        "10010011010011101101101100000",
        "01001101010010000100111100001",
        "00001110001011011010101100111",
        "00010001010100110110100100000",
        "00001011010000111011001011001",
        "00101100001010111011000101111",
        "10111111000000011100010011011",
        "01110101000011001111001000000",
        "10000010111111011100111110011",
        "00000000110111101011100010011",
        "11111110100110001101101010111",
        "10000010011100101000100010000",
        "10111010111101110000111111010",
        "10111010001010110100110010000",
        "10111010100101100111000111101",
        "10000010101100110111001100010",
        "11111110011010101111000011011"   )
}

/** A QR code from rows of "1" (dark) and "0", on white with the four-module margin readers expect. */
@Composable
fun QrCode(rows: List<String>, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White)) {
        // Every module the same whole number of pixels, centered, so no row or column comes out wider than the rest.
        val cell = kotlin.math.floor(size.minDimension / (rows.size + 8)).coerceAtLeast(1f)
        val start = kotlin.math.floor((size.minDimension - cell * rows.size) / 2)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == '1') drawRect(Color.Black, Offset(start + x * cell, start + y * cell), Size(cell, cell))
            }
        }
    }
}

/** Settings, About NOSTOS: who made this, where they are, and a way to find them. */
@Composable
fun NostosScreen() {
    val app = LocalApp.current
    OnKey { k ->
        when (k) {
            Key.A -> { if (!Links.open(app.ctx, Links.STORE)) app.say("No browser to open ${Links.STORE_SHORT} in."); true }
            else -> false
        }
    }
    Frame(header = { Crumbs("Settings", "About NOSTOS") }, hints = listOf("A" to "Open the site", "B" to "Back")) {
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 24.u()), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 64.u())) {
                // The full lockup, as the store sets it: the wordmark in white with its scanlines, the Korean and
                // Japanese readings above and below.
                T("노스토스", 20, color = Ink.Cream, weight = FontWeight.Medium, tracking = 6)
                Spacer(Modifier.height(6.u()))
                Box(Modifier.scanlines(true)) { T("NOSTOS", 104, color = Color.White, weight = FontWeight.Bold, tracking = 10) }
                Spacer(Modifier.height(4.u()))
                T("ノストス", 20, color = Ink.Cream, weight = FontWeight.Medium, tracking = 6)
                Spacer(Modifier.height(40.u()))
                T("Retro games. Vintage finds. Duluth, GA.", 22, color = Ink.Gold, weight = FontWeight.Medium, tracking = 1)
                Spacer(Modifier.height(36.u()))
                // Held to the width it has on a 4:3 screen, so the lines stay an easy length on a wide one.
                Column(Modifier.widthIn(max = 780.u()), verticalArrangement = Arrangement.spacedBy(20.u())) {
                    T("Thank you for taking the time to install Timeline.", 25, lineHeight = 1.35f)
                    T("We hope it brings your favorite games back the way you remember them.", 25, lineHeight = 1.35f)
                    T("NOSTOS is a retro video game and vintage store in Duluth, Georgia. Next time you're near Atlanta, " +
                        "stop by the store and say hello.", 25, lineHeight = 1.35f)
                }
                Spacer(Modifier.height(40.u()))
                T("Come Home.", 30, color = Ink.Gold, weight = FontWeight.Bold)
            }
            Column(Modifier.width(300.u()), horizontalAlignment = Alignment.CenterHorizontally) {
                QrCode(StoreQr.rows, Modifier.size(300.u()))
                Spacer(Modifier.height(20.u()))
                T(Links.STORE_SHORT, 22, color = Ink.Cream, weight = FontWeight.Bold, tracking = 1)
                Spacer(Modifier.height(6.u()))
                Label("Scan with your phone", size = 12)
            }
        }
    }
}
