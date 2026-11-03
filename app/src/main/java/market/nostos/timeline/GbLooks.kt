// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * The original Game Boy's three screens, changed with L1 and R1 while a game plays. The first Game Boy's green
 * dot-matrix screen; the Pocket's black and white one; and the game as a Game Boy Color showed it, in the
 * colours the Color's start-up chose for that cartridge, on the Color's own screen with its pixel transparency.
 *
 * A look has to change without the core starting over, and a Game Boy Color's colours for a Game Boy game are chosen
 * by the console as it starts. So the core draws the game in code colours instead (Gambatte's own palette file,
 * [codePal]): each colour says which of the Game Boy's three palettes and four shades a pixel is. Every look's first
 * pass reads the codes back ([code]) and draws each pixel the way that look wants it: in the core's own four greys for
 * the dot-matrix screens, or in this game's Color palette, corrected the way Gambatte corrects a Color's colours.
 * RetroArch's own next and previous shader keys then step through a folder holding this game's looks, one preset each.
 *
 * The palette tables and the way the Color chose among them are SameBoy's open-source Game Boy Color boot ROM
 * (cgb_boot.asm, Copyright (c) 2015-2026 Lior Halphon, MIT license; see NOTICE.md), which follows the console's own:
 * only Nintendo's cartridges were looked up, by the sum of the title's bytes, with the title's fourth letter telling
 * apart titles that share a sum.
 */
object GbLooks {
    const val GAME_BOY = "Game Boy"
    const val POCKET = "Game Boy Pocket"
    const val COLOR = "Game Boy Color"
    val all = listOf(GAME_BOY, POCKET, COLOR)

    /** The looks' folders, inside the launcher's own shader folder ([Crt3d.DIR]): one per game, and the plain Game Boy look. */
    const val DIR = "gb"
    const val CODE = "gb-code.inc"
    const val DECODE = "gb-decode.slang"
    const val RESPONSE = "gb-response.slang"
    const val TRANSPARENCY = "pixel_transparency_gb.slang"
    const val LISTED = "palettes.txt"

    /** L1 and R1, by Android's own key numbers for them: the same on the handheld and on a pad docked to a television. */
    const val PREVIOUS = "102"
    const val NEXT = "103"

    /** A Game Boy game to give its looks: its file's stem, the file itself, and the look it was last left in. */
    class Rom(val stem: String, val file: File, val look: String)

    /** [start]: by stem, the preset each game starts in; [folder]: the plain Game Boy look, for any other game. */
    class Setup(val start: Map<String, String>, val folder: String, val looks: Map<String, List<String>>)

    /** A cartridge as the Color saw it: its palette, whether it shows colour of its own there, the name the core gives it. */
    class Cart(val palette: Int, val color: Boolean, val name: String)

    // ---- SameBoy's tables (cgb_boot.asm: TitleChecksums, PalettePerChecksum, Dups4thLetterArray) ----------------------

    private val checksums = intArrayOf(
        0x00, 0x88, 0x16, 0x36, 0xD1, 0xDB, 0xF2, 0x3C, 0x8C, 0x92, 0x3D, 0x5C, 0x58, 0xC9, 0x3E, 0x70,
        0x1D, 0x59, 0x69, 0x19, 0x35, 0xA8, 0x14, 0xAA, 0x75, 0x95, 0x99, 0x34, 0x6F, 0x15, 0xFF, 0x97,
        0x4B, 0x90, 0x17, 0x10, 0x39, 0xF7, 0xF6, 0xA2, 0x49, 0x4E, 0x43, 0x68, 0xE0, 0x8B, 0xF0, 0xCE,
        0x0C, 0x29, 0xE8, 0xB7, 0x86, 0x9A, 0x52, 0x01, 0x9D, 0x71, 0x9C, 0xBD, 0x5D, 0x6D, 0x67, 0x3F,
        0x6B, 0xB3, 0x46, 0x28, 0xA5, 0xC6, 0xD3, 0x27, 0x61, 0x18, 0x66, 0x6A, 0xBF, 0x0D, 0xF4, 0xB3,
        0x46, 0x28, 0xA5, 0xC6, 0xD3, 0x27, 0x61, 0x18, 0x66, 0x6A, 0xBF, 0x0D, 0xF4, 0xB3,
    )
    private val perChecksum = intArrayOf(
        0, 4, 5, 35, 34, 3, 31, 15, 10, 5, 19, 36, 7, 37, 30, 44, 21, 32, 31, 20,
        5, 33, 13, 14, 5, 29, 5, 18, 9, 3, 2, 26, 25, 25, 41, 42, 26, 45, 42, 45,
        36, 38, 26, 42, 30, 41, 34, 34, 5, 42, 6, 5, 33, 25, 42, 42, 40, 2, 16, 25,
        42, 42, 5, 0, 39, 36, 22, 25, 6, 32, 12, 36, 11, 39, 18, 39, 24, 31, 50, 17,
        46, 6, 27, 0, 47, 41, 41, 0, 0, 19, 34, 23, 18, 29,
    )
    /** From this entry on, a sum is shared: the title's fourth letter must also be this one. */
    private const val FIRST_SHARED = 65
    private const val FOURTH = "BEFAARBEKEK R-URAR INAILICE R"

    /** PaletteCombinations: each palette's first colour for the first sprites, the second sprites and the background. */
    private val combinations = intArrayOf(
        16, 16, 116, 72, 72, 72, 80, 80, 80, 96, 96, 96, 36, 36, 36, 0, 0, 0, 108, 108, 108, 20, 20, 20,
        48, 48, 48, 104, 104, 104, 64, 32, 32, 16, 112, 112, 16, 8, 8, 12, 16, 16, 16, 116, 116, 112, 16, 112,
        8, 68, 8, 64, 64, 32, 16, 16, 28, 16, 16, 72, 16, 16, 80, 76, 76, 36, 15, 15, 44, 68, 68, 8,
        16, 16, 8, 16, 16, 12, 112, 112, 0, 12, 12, 0, 0, 0, 4, 72, 88, 72, 80, 88, 80, 96, 88, 96,
        64, 88, 32, 68, 16, 52, 111, 0, 56, 111, 16, 60, 76, 91, 36, 64, 112, 40, 16, 92, 112, 68, 88, 8,
        16, 0, 8, 16, 112, 12, 112, 12, 0, 12, 112, 16, 84, 112, 16, 12, 112, 0, 100, 12, 112, 0, 112, 32,
        16, 12, 112, 112, 12, 24, 16, 112, 116,
    )
    /** Palettes: the Color's own colours, five bits each of red, green and blue, red lowest. */
    private val colours = intArrayOf(
        0x7FFF, 0x32BF, 0x00D0, 0x0000, 0x639F, 0x4279, 0x15B0, 0x04CB,
        0x7FFF, 0x6E31, 0x454A, 0x0000, 0x7FFF, 0x1BEF, 0x0200, 0x0000,
        0x7FFF, 0x421F, 0x1CF2, 0x0000, 0x7FFF, 0x5294, 0x294A, 0x0000,
        0x7FFF, 0x03FF, 0x012F, 0x0000, 0x7FFF, 0x03EF, 0x01D6, 0x0000,
        0x7FFF, 0x42B5, 0x3DC8, 0x0000, 0x7E74, 0x03FF, 0x0180, 0x0000,
        0x67FF, 0x77AC, 0x1A13, 0x2D6B, 0x7ED6, 0x4BFF, 0x2175, 0x0000,
        0x53FF, 0x4A5F, 0x7E52, 0x0000, 0x4FFF, 0x7ED2, 0x3A4C, 0x1CE0,
        0x03ED, 0x7FFF, 0x255F, 0x0000, 0x036A, 0x021F, 0x03FF, 0x7FFF,
        0x7FFF, 0x01DF, 0x0112, 0x0000, 0x231F, 0x035F, 0x00F2, 0x0009,
        0x7FFF, 0x03EA, 0x011F, 0x0000, 0x299F, 0x001A, 0x000C, 0x0000,
        0x7FFF, 0x027F, 0x001F, 0x0000, 0x7FFF, 0x03E0, 0x0206, 0x0120,
        0x7FFF, 0x7EEB, 0x001F, 0x7C00, 0x7FFF, 0x3FFF, 0x7E00, 0x001F,
        0x7FFF, 0x03FF, 0x001F, 0x0000, 0x03FF, 0x001F, 0x000C, 0x0000,
        0x7FFF, 0x033F, 0x0193, 0x0000, 0x0000, 0x4200, 0x037F, 0x7FFF,
        0x7FFF, 0x7E8C, 0x7C00, 0x0000, 0x7FFF, 0x1BEF, 0x6180, 0x0000,
    )

    /** The palette a Game Boy Color's start-up chose for this cartridge, from its header (the file's first 0x150 bytes). */
    fun paletteId(h: ByteArray): Int {
        if (h.size < 0x150) return 0
        fun at(i: Int) = h[i].toInt() and 0xFF
        val nintendo = at(0x14B) == 0x01 || (at(0x14B) == 0x33 && at(0x144) == '0'.code && at(0x145) == '1'.code)
        if (!nintendo) return 0
        var sum = 0
        for (i in 0x134..0x143) sum += at(i)
        sum = sum and 0xFF
        for (i in checksums.indices) {
            if (checksums[i] != sum) continue
            if (i >= FIRST_SHARED && FOURTH[i - FIRST_SHARED].code != at(0x137)) continue
            return perChecksum[i]
        }
        return 0
    }

    /** That palette's twelve colours: the background's four shades, lightest first, then the first sprites', then the second's. */
    fun colors(id: Int): IntArray {
        val at = (if (id in 0 until combinations.size / 3) id else 0) * 3
        val out = IntArray(12)
        for (k in 0..3) {
            out[k] = colours[combinations[at + 2] + k]
            out[4 + k] = colours[combinations[at] + k]
            out[8 + k] = colours[combinations[at + 1] + k]
        }
        return out
    }

    /**
     * A Color's colour as Gambatte shows it on the Color's screen (its "accurate" correction, the front light central,
     * as for the Color's own games): Pokefan531's model of that screen, worked the way the core works it.
     */
    fun correct(c: Int): Int {
        val gamma = 2.2f - 0.5f
        val r = ((c and 31) / 31f).pow(gamma)
        val g = (((c shr 5) and 31) / 31f).pow(gamma)
        val b = (((c shr 10) and 31) / 31f).pow(gamma)
        fun level(x: Float) = (x.coerceAtLeast(0f).pow(1f / 2.2f).coerceAtMost(1f) * 31f + 0.5f).toInt()
        val rc = level(0.94f * (0.82f * r + 0.24f * g - 0.06f * b))
        val gc = level(0.94f * (0.125f * r + 0.665f * g + 0.21f * b))
        val bc = level(0.94f * (0.195f * r + 0.075f * g + 0.73f * b))
        return rc or (gc shl 5) or (bc shl 10)
    }

    /** The looks a cartridge has: one that shows colour of its own on a Color (Pokémon Yellow) runs as a Game Boy here. */
    fun looks(cart: Cart): List<String> = if (cart.color) listOf(GAME_BOY, POCKET) else all

    /**
     * A game's folder of looks, in RetroArch's order (it sorts the names, and leading spaces come first): the next look
     * first, so R1 goes on to it; the one the game starts in last. RetroArch's first L1 after a start goes to the last
     * name, which is the look already showing.
     */
    fun cycle(looks: List<String>, current: String): List<Pair<String, String>> {
        val at = looks.indexOf(current).coerceAtLeast(0)
        return looks.indices.map { j -> looks[(at + 1 + j) % looks.size].let { " ".repeat(looks.size - j) + it + ".slangp" to it } }
    }

    /** The name of the file a game starts in, for its look. */
    fun startName(look: String): String = " $look.slangp"

    /** A cartridge's header: as much of its first 0x150 bytes as there are. */
    private fun head(s: java.io.InputStream): ByteArray {
        val b = ByteArray(0x150)
        var n = 0
        while (n < b.size) { val r = s.read(b, n, b.size - n); if (r < 0) break; n += r }
        return b.copyOf(n)
    }

    private val carts = ConcurrentHashMap<String, Pair<Long, Cart>>()

    /**
     * What the Color would make of a game file: a plain dump, or the first Game Boy file in a zip (which RetroArch
     * unpacks before the core sees it, under its own name). A file that cannot be read is given the start-up's own
     * palette for an unknown cartridge, and its own name.
     */
    fun cart(rom: File): Cart {
        val seen = rom.length() * 31 + rom.lastModified()
        carts[rom.path]?.let { (at, c) -> if (at == seen) return c }
        val read = runCatching {
            if (rom.extension.equals("zip", ignoreCase = true)) java.util.zip.ZipFile(rom).use { z ->
                val e = z.entries().asSequence().firstOrNull { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("gb", "gbc", "dmg") }
                e?.let { z.getInputStream(it).use(::head) to it.name.substringAfterLast('/').substringBeforeLast('.') }
            } else rom.inputStream().use(::head) to rom.name.substringBeforeLast('.')
        }.getOrNull()
        val h = read?.first ?: ByteArray(0)
        val c = Cart(paletteId(h), h.size > 0x143 && (h[0x143].toInt() and 0x80) != 0, read?.second ?: rom.name.substringBeforeLast('.'))
        carts[rom.path] = seen to c
        return c
    }

    // ---- what the core is told, and the passes that read it back --------------------------------------------------

    /** Red: the shade (lightest first); green: the palette (background, first sprites, second sprites); blue: nothing. */
    private val shadeRed = intArrayOf(248, 168, 80, 0)
    private val layerGreen = intArrayOf(0, 128, 252)

    /** Gambatte's palette file for a game, in its own form ("Sprite%2010" is its key for the first sprites' shade 0). */
    val codePal: String = buildString {
        append("[General]\n")
        for ((layer, key) in listOf("Background", "Sprite%201", "Sprite%202").withIndex())
            for (shade in 0..3) append("$key$shade=${(shadeRed[shade] shl 16) or (layerGreen[layer] shl 8)}\n")
    }

    val code: String = """
// Made by Timeline: the original Game Boy's picture, read back from the colours the core is told to draw it in.
// Gambatte draws each of the Game Boy's twelve palette entries in a colour of its own (Timeline's .pal file): red holds
// the shade (31, 21, 10 or 0 of 31, lightest first), green the palette (0 the background, 32 the first sprites', 63 the
// second's), blue nothing. From that the screen can be any of the Game Boy's looks without the core starting over.

layout(std140, set = 0, binding = 0) uniform UBO
{
    mat4 MVP;
    float gb_color;
    float gb_response;
    float gb_c0, gb_c1, gb_c2, gb_c3, gb_c4, gb_c5, gb_c6, gb_c7, gb_c8, gb_c9, gb_c10, gb_c11;
} global;

#pragma parameter gb_color "Game Boy: shades (0) or a Game Boy Color's palette (1)" 0.0 0.0 1.0 1.0
#pragma parameter gb_response "Game Boy: screen response" 0.0 0.0 0.777 0.111
#pragma parameter gb_c0 "Color palette, background 0" 32767.0 0.0 32767.0 1.0
#pragma parameter gb_c1 "Color palette, background 1" 21140.0 0.0 32767.0 1.0
#pragma parameter gb_c2 "Color palette, background 2" 10570.0 0.0 32767.0 1.0
#pragma parameter gb_c3 "Color palette, background 3" 0.0 0.0 32767.0 1.0
#pragma parameter gb_c4 "Color palette, sprites A 0" 32767.0 0.0 32767.0 1.0
#pragma parameter gb_c5 "Color palette, sprites A 1" 21140.0 0.0 32767.0 1.0
#pragma parameter gb_c6 "Color palette, sprites A 2" 10570.0 0.0 32767.0 1.0
#pragma parameter gb_c7 "Color palette, sprites A 3" 0.0 0.0 32767.0 1.0
#pragma parameter gb_c8 "Color palette, sprites B 0" 32767.0 0.0 32767.0 1.0
#pragma parameter gb_c9 "Color palette, sprites B 1" 21140.0 0.0 32767.0 1.0
#pragma parameter gb_c10 "Color palette, sprites B 2" 10570.0 0.0 32767.0 1.0
#pragma parameter gb_c11 "Color palette, sprites B 3" 0.0 0.0 32767.0 1.0

// A Game Boy Color colour (five bits each of red, green and blue, red lowest) as the core puts it on screen: 16 bits a
// pixel, green given six bits with the lowest left at 0.
vec3 gbColor(float v)
{
    float r = mod(v, 32.0);
    float g = mod(floor(v / 32.0), 32.0);
    float b = floor(v / 1024.0);
    return vec3(r / 31.0, g * 2.0 / 63.0, b / 31.0);
}

vec3 gbDecode(vec3 c)
{
    float shade = clamp(floor((1.0 - c.r) * 3.0 + 0.5), 0.0, 3.0);
    // A colour with blue in it is not one of the codes (the core's own grey, if the palette file were missing): it is
    // shown as the grey it is, on the background's palette.
    float layer = c.b > 0.5 / 31.0 ? 0.0 : clamp(floor(c.g * 2.0 + 0.5), 0.0, 2.0);
    if (global.gb_color < 0.5)
    {
        // The core's own four greys, exactly as it draws them when it is left to its plain Game Boy palette.
        if (shade < 0.5) return vec3(1.0);
        if (shade < 1.5) return vec3(21.0 / 31.0, 42.0 / 63.0, 21.0 / 31.0);
        if (shade < 2.5) return vec3(10.0 / 31.0, 21.0 / 63.0, 10.0 / 31.0);
        return vec3(0.0);
    }
    int i = int(layer * 4.0 + shade);
    float v = i == 0 ? global.gb_c0 : i == 1 ? global.gb_c1 : i == 2 ? global.gb_c2 : i == 3 ? global.gb_c3
            : i == 4 ? global.gb_c4 : i == 5 ? global.gb_c5 : i == 6 ? global.gb_c6 : i == 7 ? global.gb_c7
            : i == 8 ? global.gb_c8 : i == 9 ? global.gb_c9 : i == 10 ? global.gb_c10 : global.gb_c11;
    return gbColor(v);
}
""".trimStart()

    private const val VERTEX = """
#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main()
{
    gl_Position = global.MVP * Position;
    vTexCoord = TexCoord;
}
"""

    val decode: String = """
#version 450
// Made by Timeline: the Game Boy's picture of this moment, from the core's code colours (gb-code.inc). Used where a
// later pass reads the core's own frame (Pixel Transparency), which here is this pass's output instead.

#include "gb-code.inc"
$VERTEX
#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Original;

void main()
{
    FragColor = vec4(gbDecode(texture(Original, vTexCoord).rgb), 1.0);
}
""".trimStart()

    val response: String = """
#version 450
// Made by Timeline: the Game Boy's picture from the core's code colours (gb-code.inc), with the screen's slow response:
// each of the last seven frames, read back the same way, still showing a little (the collection's response-time pass,
// Harlequin's, done here because it reads the core's frames, which are codes, not colours).

#include "gb-code.inc"
$VERTEX
#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Original;
layout(set = 0, binding = 3) uniform sampler2D OriginalHistory1;
layout(set = 0, binding = 4) uniform sampler2D OriginalHistory2;
layout(set = 0, binding = 5) uniform sampler2D OriginalHistory3;
layout(set = 0, binding = 6) uniform sampler2D OriginalHistory4;
layout(set = 0, binding = 7) uniform sampler2D OriginalHistory5;
layout(set = 0, binding = 8) uniform sampler2D OriginalHistory6;
layout(set = 0, binding = 9) uniform sampler2D OriginalHistory7;

void main()
{
    vec3 rgb = gbDecode(texture(Original, vTexCoord).rgb);
    float t = global.gb_response;
    if (t > 0.0)
    {
        rgb += (gbDecode(texture(OriginalHistory1, vTexCoord).rgb) - rgb) * t;
        rgb += (gbDecode(texture(OriginalHistory2, vTexCoord).rgb) - rgb) * pow(t, 2.0);
        rgb += (gbDecode(texture(OriginalHistory3, vTexCoord).rgb) - rgb) * pow(t, 3.0);
        rgb += (gbDecode(texture(OriginalHistory4, vTexCoord).rgb) - rgb) * pow(t, 4.0);
        rgb += (gbDecode(texture(OriginalHistory5, vTexCoord).rgb) - rgb) * pow(t, 5.0);
        rgb += (gbDecode(texture(OriginalHistory6, vTexCoord).rgb) - rgb) * pow(t, 6.0);
        rgb += (gbDecode(texture(OriginalHistory7, vTexCoord).rgb) - rgb) * pow(t, 7.0);
    }
    FragColor = vec4(rgb, 1.0);
}
""".trimStart()

    private val ptDeclared = Regex("""uniform\s+sampler2D\s+Original\s*;""")
    private val ptRead = Regex("""\btexture\(\s*Original\s*,""")
    private val ptSize = Regex("""\btextureSize\(\s*Original\s*,""")

    /**
     * The collection's Pixel Transparency, reading the Game Boy's picture from the pass that decodes it (GbFrame)
     * instead of the core's frame, which holds codes. Null for a version that reads the core's frame in some other way.
     */
    fun transparency(pt: String): String? {
        if (!pt.startsWith("#version") || "#include" in pt) return null
        if (ptDeclared.findAll(pt).count() != 1 || ptRead.findAll(pt).count() != 2 || ptSize.findAll(pt).count() != 2) return null
        val made = pt.replace(ptDeclared, "uniform sampler2D GbFrame;").replace(ptRead, "texture(GbFrame,").replace(ptSize, "textureSize(GbFrame,")
        val firstLine = made.substringBefore('\n')
        return firstLine + "\n// Made by Timeline from pixel_transparency.slang in RetroArch's shader collection: the Game Boy's own frame read from\n" +
            "// the pass that decodes it (GbFrame) instead of the core's, which holds code colours.\n" + made.substringAfter('\n')
    }

    private fun num(x: Double) = String.format(Locale.ROOT, "%.6f", x)
    private fun preset(keys: Map<String, String>) = keys.entries.joinToString("") { (k, v) -> "$k = \"$v\"\n" }

    private val passKeys = setOf("shader", "filter_linear", "scale_type", "scale_type_x", "scale_type_y", "scale", "scale_x", "scale_y",
        "alias", "float_framebuffer", "srgb_framebuffer", "wrap_mode", "mipmap_input", "frame_count_mod")
    private val indexed = Regex("^([a-z_]+?)(\\d+)$")

    /**
     * One of the collection's dot-matrix presets (gameboy.slangp, gameboy-pocket.slangp) with the code-reading pass in
     * front of its own: its passes moved one on, its files given in full, and the screen's response done in that first
     * pass ([response], of a frame kept into the next). Null for a preset this does not follow (one that refers to another).
     */
    fun dotMatrix(text: String, dir: String, own: String, response: Double): String? {
        val keys = LinkedHashMap<String, String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line.startsWith("#")) return null
            if ('=' !in line) continue
            keys[line.substringBefore('=').trim()] = line.substringAfter('=').trim().trim('"')
        }
        val n = keys["shaders"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val textures = keys["textures"]?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().toSet()
        fun full(v: String) = if (v.startsWith("/")) v else File(dir, v).normalize().path
        val out = linkedMapOf("shaders" to (n + 1).toString(),
            "shader0" to "$own/$RESPONSE", "filter_linear0" to "false", "scale_type0" to "source", "scale0" to "1.0", "float_framebuffer0" to "true")
        for ((k, v) in keys) {
            if (k == "shaders") continue
            val m = indexed.matchEntire(k)
            when {
                m != null && m.groupValues[1] in passKeys -> {
                    val i = m.groupValues[2].toInt()
                    if (i < n) out[m.groupValues[1] + (i + 1)] = if (m.groupValues[1] == "shader") full(v) else v
                }
                k in textures -> out[k] = full(v)
                else -> out[k] = v
            }
        }
        if ((1..n).any { "shader$it" !in out }) return null
        // The collection's own response pass reads the core's frames, which are codes: done in the first pass instead.
        out += mapOf("integer_mode" to num(1.0), "response_time" to num(0.0), "palette" to num(0.0), "gb_color" to num(0.0), "gb_response" to num(response))
        return preset(out)
    }

    /**
     * The game on a Game Boy Color: its palette in the Color's colours, then the Color's screen as the Color games have
     * it (RetroPack's gbcScreen): a third of each frame kept into the next, its pixels' measured shape, the unlit
     * screen showing through pale pixels.
     */
    fun colorPreset(cart: Cart, own: String, slang: String): String {
        val c = colors(cart.palette).map { correct(it) }
        val keys = linkedMapOf("shaders" to "5",
            "shader0" to "$own/$DECODE", "filter_linear0" to "false", "scale_type0" to "source", "scale0" to "1.0", "float_framebuffer0" to "true", "alias0" to "GbFrame",
            "shader1" to "$own/$RESPONSE", "filter_linear1" to "false", "scale_type1" to "source", "scale1" to "1.0",
            "shader2" to "$slang/handheld/shaders/authentic_gbc/to_lin_fast.slang", "filter_linear2" to "false", "scale_type2" to "source", "scale2" to "1.0",
            "srgb_framebuffer2" to "true",
            "shader3" to "$slang/handheld/shaders/authentic_gbc/authentic_gbc_fast.slang", "filter_linear3" to "false", "scale_type3" to "viewport",
            "shader4" to "$own/$TRANSPARENCY", "filter_linear4" to "false", "scale_type4" to "viewport",
            "PT_PALETTE" to num(4.0), "PT_POLARIZER" to num(0.0), "PT_HIGHLIGHTS" to num(0.0), "gb_color" to num(1.0), "gb_response" to num(0.333))
        c.forEachIndexed { i, v -> keys["gb_c$i"] = num(v.toDouble()) }
        return preset(keys)
    }

    /** The collection's files the Color look is drawn with, besides the dot-matrix presets and Pixel Transparency. */
    val colorFiles = listOf("handheld/shaders/authentic_gbc/to_lin_fast.slang", "handheld/shaders/authentic_gbc/authentic_gbc_fast.slang")

    private val started = Regex("""\[Shaders] Specific shader preset found at ".*/Gambatte/([^"/]+)\.slangp"""")
    private val applied = Regex("""\[Shaders] Applying shader: ".*/""" + Regex.escape("${Crt3d.DIR}/$DIR") + """/([^"/]+)/ +([^"/]+)\.slangp"""")

    /**
     * The look each Game Boy game was left in, by its stem, from RetroArch's log of a sitting: the last look L1 or R1
     * went to since the game started. A game that started and was not changed is not in it.
     */
    fun leftIn(log: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in log.lineSequence()) {
            if ("[Shaders]" !in line) continue
            started.find(line)?.let { out.remove(it.groupValues[1]) }
            applied.find(line)?.let { m -> if (m.groupValues[2] in all) out[m.groupValues[1]] = m.groupValues[2] }
        }
        return out
    }

    /**
     * What [write] did: the looks to use ([setup], null when there are none), and the files of the launcher's own that
     * are no longer wanted. Those are taken away only by [finish], once everything that pointed at them has been
     * written over: a game is never left pointing at a preset that is not there, even if the writing stops half-way.
     */
    class Written(val setup: Setup?, val stale: List<File>) {
        /** Files first, then their folders, which go only when empty (a preset of the player's own keeps its folder). */
        fun finish() { for (f in stale) runCatching { f.delete() } }
    }

    /**
     * The looks a game will have, worked out as [write] does (none for a game with a palette file of the player's own),
     * for its page before a setup has been written. Reads the cartridge's header: not on the main thread.
     */
    fun expected(rom: File, palettes: File, home: String, slang: String): List<String>? = runCatching {
        if (parts(home, slang) == null) return@runCatching null
        val cart = cart(rom)
        val pal = File(palettes, "${cart.name}.pal")
        if (pal.isFile && pal.readText() != codePal) null else looks(cart)
    }.getOrNull()

    /** The launcher's own preset names in a game's folder, and nothing else: a preset the player saved there is theirs. */
    private val ownName = Regex("^ +(${all.joinToString("|") { Regex.escape(it) }})\\.slangp$")
    private fun ours(f: File) = f.isFile && ownName.matches(f.name)

    /**
     * Puts the looks in place for [games], through [put] (which writes a file only where it differs). No setup, with
     * nothing of the looks left behind, when the shader collection does not have what they are made from or no
     * game takes them: the games then have the original screen alone, as before. [palettes]: Gambatte's palette folder.
     */
    /** What the looks are made from, out of the shader collection: null when it does not have all of it. */
    private class Parts(val gameBoy: String, val pocket: String, val pt: String)
    private fun parts(home: String, slang: String): Parts? {
        val own = "$home/${Crt3d.DIR}"
        fun read(path: String) = File(slang, path).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
        val gameBoy = read("handheld/gameboy.slangp")?.let { dotMatrix(it, "$slang/handheld", own, 0.555) } ?: return null
        val pocket = read("handheld/gameboy-pocket.slangp")?.let { dotMatrix(it, "$slang/handheld", own, 0.333) } ?: return null
        val pt = read("handheld/shaders/pixel_transparency/pixel_transparency.slang")?.let(::transparency) ?: return null
        return if (colorFiles.all { File(slang, it).isFile }) Parts(gameBoy, pocket, pt) else null
    }

    fun write(home: String, slang: String, palettes: File, games: List<Rom>, put: (File, String) -> Unit): Written {
        val own = "$home/${Crt3d.DIR}"
        val root = File(own, DIR)
        val parts = parts(home, slang)
        if (parts == null || games.isEmpty()) return Written(null, stale(root, palettes, emptySet(), emptySet(), emptyList()))
        val gameBoy = parts.gameBoy
        val pocket = parts.pocket
        // A palette file of the player's own for a game is left alone, and that game keeps the original screen.
        val taking = games.mapNotNull { g ->
            val cart = cart(g.file)
            val pal = File(palettes, "${cart.name}.pal")
            if (pal.isFile && runCatching { pal.readText() }.getOrNull() != codePal) null else Triple(g, cart, pal)
        }
        if (taking.isEmpty()) return Written(null, stale(root, palettes, emptySet(), emptySet(), emptyList()))
        put(File(own, CODE), code)
        put(File(own, DECODE), decode)
        put(File(own, RESPONSE), response)
        put(File(own, TRANSPARENCY), parts.pt)
        val folder = File(root, "$GAME_BOY.slangp")
        put(folder, gameBoy)
        val start = LinkedHashMap<String, String>()
        val looks = LinkedHashMap<String, List<String>>()
        val pals = HashSet<String>()
        val turned = ArrayList<File>()
        for ((g, cart, pal) in taking) {
            put(pal, codePal)
            pals += pal.name
            val mine = looks(cart)
            val dir = File(root, g.stem)
            val now = cycle(mine, g.look.takeIf { it in mine } ?: GAME_BOY)
            for ((name, look) in now) put(File(dir, name), when (look) { GAME_BOY -> gameBoy; POCKET -> pocket; else -> colorPreset(cart, own, slang) })
            val names = now.map { it.first }.toSet()
            // The names the folder had before this turn: taken away once the game points at its new start.
            dir.listFiles()?.filter { ours(it) && it.name !in names }?.let(turned::addAll)
            start[g.stem] = File(dir, now.last().first).path
            looks[g.stem] = mine
        }
        val gone = stale(root, palettes, start.keys, pals, turned)
        // Listed until they are gone, so a palette still waiting to be taken away is still known as the launcher's.
        put(File(root, LISTED), (pals + gone.filter { it.parentFile == palettes }.map { it.name }).sorted().joinToString("\n"))
        return Written(Setup(start, folder.path, looks), gone)
    }

    /**
     * The launcher's own files that are no longer wanted, in the order to take them away: the old names in a kept
     * folder ([turned]), the looks of games no longer kept and then their folders, code palettes no longer kept (only
     * while they still hold the code palette, so a player's file put in their place is never touched), and with no game
     * kept at all, the list and the shared preset too.
     */
    private fun stale(root: File, palettes: File, keep: Set<String>, keepPals: Set<String>, turned: List<File>): List<File> {
        val out = ArrayList<File>(turned)
        val dirs = root.listFiles()?.filter { it.isDirectory && it.name !in keep }.orEmpty()
        for (d in dirs) d.listFiles()?.filter(::ours)?.let(out::addAll)
        val listed = File(root, LISTED)
        val before = if (listed.isFile) runCatching { listed.readLines() }.getOrDefault(emptyList()) else emptyList()
        for (name in before) if (name.endsWith(".pal") && '/' !in name && name !in keepPals)
            File(palettes, name).takeIf { it.isFile && runCatching { it.readText() }.getOrNull() == codePal }?.let(out::add)
        out += dirs
        if (keep.isEmpty()) { out += listed; out += File(root, "$GAME_BOY.slangp"); out += root }
        return out
    }
}
