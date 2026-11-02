// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

/**
 * The CRT look for the consoles that draw in 3D. The television consoles use "CRT - GDV Mini Ultra" from RetroArch's
 * shader collection; these carry it to pictures it was not written for:
 *
 *  - A 3D console drawn at four times its own detail hands over a picture of 960 lines, and GDV judges whether to
 *    draw scanlines from the size the console drew, so it drew none. Here the picture is first brought back to the
 *    console's own number of lines (each line the average of the four, or two, it was drawn as, so the clean edges
 *    stay), and GDV decides from the picture it is given ([slang], made from the collection's own file).
 *  - Nintendo 64 (and Dreamcast drawn the compatible way) run under OpenGL, where RetroArch takes only GLSL shaders,
 *    which the collection does not have: [glsl] is GDV Mini Ultra ported to GLSL, under its own license (below).
 *  - GameCube and Wii are drawn by Dolphin, which has no CRT look of its own: [dolphin] is Timeline's own shader for
 *    them, drawn at the game's own number of lines however finely Dolphin renders it. Its corner darkening follows
 *    GDV's.
 *
 * GDV Mini Ultra ("CRT - Guest - Nomask w. Curvature"): Copyright (C) 2017-2018 guest(r), with work by DariusG.
 * GNU General Public License, version 2 or (at your option) any later version.
 */
object Crt3d {
    /** Where the launcher puts its own shader files, beside the collection it unpacks. */
    const val DIR = "shaders/timeline"
    const val SLANG = "crt-gdv-3d.slang"
    const val GLSL = "crt-gdv-3d.glsl"
    const val STOCK_GLSL = "stock.glsl"
    const val DOLPHIN = "Timeline CRT.glsl"

    private const val CENTRE = "vec2 OGL2Pos = pos * SourceSize.xy;"
    private const val GLOW = "color += glow0(pC4,color);"

    /**
     * GDV Mini Ultra from the collection, with three changes. Lines are averaged instead of drawn as scanlines only when
     * the picture it is handed is at least [gdv_hires] lines tall (0: never), not when the console drew that many. Each
     * beam is centered on its line, and each colour on its pixel: the original puts them half a console pixel up and to the
     * left, which cut the top line in half and faded the last column into the border (measured on a test pattern: the
     * brightest panel rows fell between lines). And the glow's 21 texture reads are skipped when the glow is off. Used by
     * the television consoles too. Null when the file is not the one these changes were written for.
     */
    fun slang(gdv: String): String? {
        val struct = Regex("(float\\s+thres\\s*;)(\\s*\\}\\s*params\\s*;)")
        val params = "// Parameter lines go here:"
        val decision = "if (OriginalSize.y >= 400.0) {color = (color1 + color2)/2.0;}"
        if (!struct.containsMatchIn(gdv) || !gdv.contains(params) || !gdv.contains(decision)) return null
        if (!gdv.contains(CENTRE) || !gdv.contains(GLOW)) return null
        return gdv
            .replaceFirst(CENTRE, "vec2 OGL2Pos = pos * SourceSize.xy - vec2(0.5, global.OutputSize.y * SourceSize.w < 3.0 ? 0.25 : 0.5);   // Timeline: the beam centered on each line (a quarter line off at under 3 rows a line, so one row is beam and one is gap)")
            .replaceFirst(GLOW, "if (glow > 0.0) color += glow0(pC4,color);")
            .replaceFirst(struct, "$1\n    float gdv_hires;$2")
            .replaceFirst(params, "$params\n#pragma parameter gdv_hires \"Average lines from this height (0: never)\" 0.0 0.0 1200.0 1.0\n#define gdv_hires params.gdv_hires")
            .replaceFirst(decision, "if (gdv_hires > 0.0 && global.SourceSize.y >= gdv_hires) {color = (color1 + color2)/2.0;}")
            .replaceFirst("#version 450", "#version 450\n// Modified by Timeline (NOSTOS) from crt-gdv-mini-ultra.slang in RetroArch's shader collection: gdv_hires added, the beam centered on each line, the glow skipped when off.")
    }

    /** A plain pass, for bringing a picture down to its console's own lines with the sampler's averaging. */
    val stockGlsl = """
#if defined(VERTEX)
#if __VERSION__ >= 130
#define COMPAT_VARYING out
#define COMPAT_ATTRIBUTE in
#else
#define COMPAT_VARYING varying
#define COMPAT_ATTRIBUTE attribute
#endif
COMPAT_ATTRIBUTE vec4 VertexCoord;
COMPAT_ATTRIBUTE vec4 TexCoord;
COMPAT_VARYING vec4 TEX0;
uniform mat4 MVPMatrix;
void main()
{
    gl_Position = MVPMatrix * VertexCoord;
    TEX0.xy = TexCoord.xy;
}
#elif defined(FRAGMENT)
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif
#if __VERSION__ >= 130
#define COMPAT_VARYING in
#define COMPAT_TEXTURE texture
out vec4 FragColor;
#else
#define COMPAT_VARYING varying
#define FragColor gl_FragColor
#define COMPAT_TEXTURE texture2D
#endif
uniform sampler2D Texture;
COMPAT_VARYING vec4 TEX0;
void main()
{
    FragColor = vec4(COMPAT_TEXTURE(Texture, TEX0.xy).rgb, 1.0);
}
#endif
""".trimStart()

    /**
     * GDV Mini Ultra in RetroArch's GLSL, without the curvature (set flat here). Defaults are Timeline's true-picture
     * settings, not the collection's (see [slang]): no glow, no bright boost, no extra saturation, no mask, no vignette,
     * wider beams on bright lines, and the beam centered on each line.
     */
    val glsl = """
/*
   CRT - Guest - Nomask w. Curvature
   With work by DariusG to create a cut down extra fast version
   
   Copyright (C) 2017-2018 guest(r) - guest.r@gmail.com

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
   
   Modified by NOSTOS for Timeline, October 2026: ported to GLSL for RetroArch's OpenGL driver, curvature removed,
   gdv_hires added (lines are averaged only above a chosen picture height). November 2026: the beam centered on each
   line, the glow skipped when off, and defaults set to show the picture as a television did.
*/
#pragma parameter scanline "Scanline Adjust" 10.0 1.0 15.0 1.0
#pragma parameter beam_min "Scanline Dark" 1.5 0.5 3.0 0.05
#pragma parameter beam_max "Scanline Bright" 2.5 0.5 3.0 0.05
#pragma parameter h_sharp "Horizontal Sharpness" 2.5 1.0 5.0 0.05
#pragma parameter shadowMask "CRT Mask: -1 none, 0 CGWG, 11 GDV" -1.0 -1.0 11.0 1.0
#pragma parameter thres "Mask Effect Threshold" 0.4 0.0 0.9 0.02
#pragma parameter masksize "CRT Mask Size" 1.0 1.0 2.0 1.0
#pragma parameter maskDark "Lottes maskDark" 0.0 0.0 2.0 0.1
#pragma parameter CGWG "CGWG Mask Str." 1.0 0.0 1.0 0.1
#pragma parameter vignette "Vignette On/Off" 0.0 0.0 1.0 1.0
#pragma parameter gamma_out "Gamma out" 2.3 1.0 4.0 0.1
#pragma parameter brightboost "Bright boost" 1.0 0.5 2.0 0.05
#pragma parameter sat "Saturation adjustment" 1.0 0.0 2.0 0.05
#pragma parameter glow "Glow Strength" 0.0 0.0 1.0 0.01
#pragma parameter gdv_hires "Average lines from this height (0: never)" 0.0 0.0 1200.0 1.0
#if defined(VERTEX)
#if __VERSION__ >= 130
#define COMPAT_VARYING out
#define COMPAT_ATTRIBUTE in
#else
#define COMPAT_VARYING varying
#define COMPAT_ATTRIBUTE attribute
#endif
COMPAT_ATTRIBUTE vec4 VertexCoord;
COMPAT_ATTRIBUTE vec4 TexCoord;
COMPAT_VARYING vec4 TEX0;
uniform mat4 MVPMatrix;
void main()
{
    gl_Position = MVPMatrix * VertexCoord;
    TEX0.xy = TexCoord.xy;
}
#elif defined(FRAGMENT)
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif
#if __VERSION__ >= 130
#define COMPAT_VARYING in
#define COMPAT_TEXTURE texture
out vec4 FragColor;
#else
#define COMPAT_VARYING varying
#define FragColor gl_FragColor
#define COMPAT_TEXTURE texture2D
#endif
uniform sampler2D Texture;
uniform vec2 TextureSize;
uniform vec2 InputSize;
uniform vec2 OutputSize;
COMPAT_VARYING vec4 TEX0;
#ifdef PARAMETER_UNIFORM
uniform float scanline, beam_min, beam_max, h_sharp, shadowMask, thres, masksize, maskDark, CGWG, vignette,
    gamma_out, brightboost, sat, glow, gdv_hires;
#else
#define scanline 10.0
#define beam_min 1.5
#define beam_max 2.5
#define h_sharp 2.5
#define shadowMask -1.0
#define thres 0.4
#define masksize 1.0
#define maskDark 0.0
#define CGWG 1.0
#define vignette 0.0
#define gamma_out 2.3
#define brightboost 1.0
#define sat 1.0
#define glow 0.0
#define gdv_hires 0.0
#endif

float sw(float x, float l)
{
    return exp2(-scanline * pow(x, mix(beam_min, beam_max, l)));
}

vec3 Mask(vec2 pos)
{
    pos = floor(pos / masksize);
    if (shadowMask < -0.5) return vec3(1.0);
    if (shadowMask < 0.5)
    {
        float mc = 1.0 - CGWG;
        return fract(pos.x * 0.5) < 0.5 ? vec3(1.1, mc, 1.1) : vec3(mc, 1.1, mc);
    }
    return fract(pos.x / 3.0) > 0.333 ? vec3(1.0) : vec3(maskDark);
}

vec3 saturation(vec3 c)
{
    float lum = length(c) * 0.5775;
    vec3 w = vec3(0.3, 0.6, 0.1);
    if (lum < 0.5) w = w * w + w * w;
    return mix(vec3(dot(c, w)), c, sat);
}

vec3 tex(vec2 p) { return COMPAT_TEXTURE(Texture, p).rgb; }

vec3 glow0(vec2 t, vec2 b)
{
    vec3 c11 = tex(t);
    vec3 near = tex(t - vec2(b.x, 0.0)) + tex(t + vec2(b.x, 0.0)) + tex(t - b) + tex(t + b)
        + tex(t + vec2(-b.x, b.y)) + tex(t + vec2(b.x, -b.y)) + tex(t - vec2(0.0, b.y)) + tex(t + vec2(0.0, b.y));
    vec3 far = tex(t - vec2(2.0 * b.x, 0.0)) + tex(t + vec2(2.0 * b.x, 0.0))
        + tex(t + vec2(-2.0 * b.x, -b.y)) + tex(t + vec2(-b.x, -2.0 * b.y)) + tex(t + vec2(b.x, 2.0 * b.y)) + tex(t + vec2(2.0 * b.x, b.y))
        + tex(t + vec2(-2.0 * b.x, b.y)) + tex(t + vec2(-b.x, 2.0 * b.y)) + tex(t + vec2(b.x, -2.0 * b.y)) + tex(t + vec2(2.0 * b.x, -b.y))
        + tex(t - vec2(0.0, 2.0 * b.y)) + tex(t + vec2(0.0, 2.0 * b.y));
    return (3.0 * c11 + 2.5 * near + 1.5 * far) / 45.0 * glow;
}

void main()
{
    vec2 ps = 1.0 / TextureSize;
    // the beam centered on each line, the colour on each pixel; a quarter line off at under 3 rows a line ([slang])
    vec2 OGL2Pos = TEX0.xy * TextureSize - vec2(0.5, OutputSize.y / InputSize.y < 3.0 ? 0.25 : 0.5);
    vec2 fp = fract(OGL2Pos);
    vec2 pC4 = floor(OGL2Pos) * ps + 0.5 * ps;
    vec2 dx = vec2(ps.x, 0.0);
    vec2 dy = vec2(0.0, ps.y);

    vec3 ul = tex(pC4);
    vec3 ur = tex(pC4 + dx);
    vec3 dl = tex(pC4 + dy);
    vec3 dr = tex(pC4 + ps);

    float lx = pow(fp.x, h_sharp);
    float rx = pow(1.0 - fp.x, h_sharp);
    vec3 color1 = (ur * lx + ul * rx) / (lx + rx);
    vec3 color2 = (dr * lx + dl * rx) / (lx + rx);

    float luma1 = dot(color1, vec3(0.3, 0.6, 0.1));
    float luma2 = dot(color2, vec3(0.3, 0.6, 0.1));
    color1 = 2.0 * pow(color1, vec3(2.8)) - pow(color1, vec3(3.6));
    color2 = 2.0 * pow(color2, vec3(2.8)) - pow(color2, vec3(3.6));

    vec2 outPos = TEX0.xy * TextureSize / InputSize * OutputSize;
    vec3 m = Mask(outPos);
    color1 *= mix(m, vec3(1.0), luma1 * thres);
    color2 *= mix(m, vec3(1.0), luma2 * thres);

    vec3 color = color1 * sw(fp.y, luma1) + color2 * sw(1.0 - fp.y, luma2);
    if (gdv_hires > 0.0 && InputSize.y >= gdv_hires) color = (color1 + color2) / 2.0;

    color = min(color, 1.0);
    float lum = dot(color, vec3(0.3, 0.6, 0.1));
    color = pow(color, vec3(1.0 / gamma_out));
    if (glow > 0.0) color += glow0(pC4, ps);
    color *= mix(1.0, brightboost, lum);
    color = saturation(color);

    vec2 v = TEX0.xy * TextureSize / InputSize;
    v *= 1.0 - v;
    float vig = min(pow(v.x * v.y * 45.0, 0.15), 1.0);
    if (vignette > 0.5) color *= vig;

    FragColor = vec4(color, 1.0);
}
#endif
""".trimStart()

    /**
     * The CRT for GameCube and Wii, in Dolphin's shader dialect: the picture as a television of their day showed it
     * ([Tv480.body], shared with PlayStation 2). GDV's mask and full-strength lines read as a fine black mesh at 480
     * lines, and an earlier 480-line television with an aperture grille worked on gamma-encoded values, which made its
     * gaps two to three times deeper than a set's and clipped white. The true picture (2026-11-02): both consoles sent 480 lines
     * interlaced through a composite cable, whose fields fill each other's gaps on a set, leaving a faint line structure
     * deepest in dark parts; the tube's own 2.4 gamma; the set's colour and white. [lines] is the line structure's depth
     * and [mask] the aperture grille's strength, both from the cable ([RetroPack.tvLook]); [signal] and [white] as
     * Settings, Picture.
     */
    fun dolphin(lines: Double, signal: Int, white: String, mask: Double): String = """
// Timeline TV for Dolphin: GameCube and Wii as a television showed them.
// Written for Timeline by NOSTOS. Timeline is under the GNU General Public License, version 3 or later.

/*
[configuration]

[OptionRangeFloat]
GUIName = Line structure
OptionName = lines
MinValue = 0.0
MaxValue = 0.9
StepAmount = 0.05
DefaultValue = $lines

[OptionRangeFloat]
GUIName = Signal: 0 component, 1 S-video, 2 composite
OptionName = signal
MinValue = 0.0
MaxValue = 2.0
StepAmount = 1.0
DefaultValue = $signal.0

[OptionRangeFloat]
GUIName = White: 0 6500K, 1 7500K, 2 8000K, 3 9300K
OptionName = white
MinValue = 0.0
MaxValue = 3.0
StepAmount = 1.0
DefaultValue = $white

[OptionRangeFloat]
GUIName = Aperture grille
OptionName = mask
MinValue = 0.0
MaxValue = 0.6
StepAmount = 0.02
DefaultValue = $mask

[OptionRangeFloat]
GUIName = Saturation
OptionName = colour
MinValue = 0.0
MaxValue = 2.0
StepAmount = 0.05
DefaultValue = 1.0

[/configuration]
*/

float3 TEX(float2 uv)
{
    return SampleLocation(src_rect.xy + src_rect.zw * clamp(uv, float2(0.0, 0.0), float2(1.0, 1.0))).rgb;
}
#define PICSIZE (src_rect.zw * GetResolution())
#define OPT_LINES GetOption(lines)
#define OPT_SIGNAL GetOption(signal)
#define OPT_WHITE GetOption(white)
#define OPT_MASK GetOption(mask)
#define OPT_SAT GetOption(colour)
${Tv480.body}
void main()
{
    float2 uv = (GetCoordinates() - src_rect.xy) / src_rect.zw;
    // How many screen rows one of the 480 lines covers, from how far the picture moves per row.
    float rows = abs(src_rect.w / max(abs(dFdy(GetCoordinates().y)), 0.000001)) / 480.0;
    SetOutput(float4(tv(uv, gl_FragCoord.xy, rows), 1.0));
}
""".trimStart()
}

/**
 * PlayStation 2 in ARMSX2: the same 480-line television as [Crt3d.dolphin], as a RetroArch slang shader, which
 * ARMSX2's "RetroArch Shaders" setting takes. ARMSX2 keeps its preset at [PRESET] in its own folder, which the
 * launcher reaches through the maker's root service ([Ps2Look] in Device.kt). Before this, the PS2 used the collection's
 * GDV, which averages lines away on any picture of 400 lines or more: PS2 games, at 448 or 480, showed no lines.
 */
object Tv480 {
    const val SLANG = "timeline-tv.slang"
    const val PRESET = "timeline-crt.slangp"

    /** ARMSX2's preset: [lines], [signal], [white] and [mask] as for [Crt3d.dolphin]. */
    fun preset(lines: Double, signal: Int, white: String, mask: Double) = """
shaders = "1"
shader0 = "$SLANG"
filter_linear0 = "true"
wrap_mode0 = "clamp_to_edge"
mipmap_input0 = "false"
scale_type0 = "viewport"
scale0 = "1.000000"
tv_lines = "$lines"
tv_signal = "$signal.0"
tv_white = "$white"
tv_mask = "$mask"
""".trimStart()

    /**
     * The 480-line television, shared by PlayStation 2 (as a slang shader) and GameCube and Wii (Dolphin's dialect), in
     * one pass, since neither emulator is known to take more. In order, as in the set: the signal ([Signal]'s softening,
     * measured in a 320-pixel line so every console meets the same set), the set's phosphors and white ([Colour]), the
     * tube's 2.4 gamma, then a faint line structure in light, deepest in dark parts, as a set's interlaced fields left
     * it. Nothing is lifted, so nothing clips. Needs TEX(uv), PICSIZE (the picture's size in its own pixels) and the OPT_
     * names defined first.
     */
    val body = """
const mat3 toYIQ = mat3(0.299, 0.596, 0.211, 0.587, -0.274, -0.523, 0.114, -0.322, 0.312);
const mat3 toRGB = mat3(1.0, 1.0, 1.0, 0.956, -0.272, -1.106, 0.621, -0.647, 1.703);
const mat3 W6500 = mat3(0.93954, 0.01777, -0.00162,   0.05018, 0.96579, -0.00437,   0.01028, 0.01643, 1.00599);
const mat3 W7500 = mat3(0.79920, 0.01712,  0.00122,   0.01097, 0.85092,  0.00565,   0.00212, 0.01223, 0.99313);
const mat3 W8000 = mat3(0.74958, 0.01679,  0.00224,  -0.00131, 0.80881,  0.00928,  -0.00070, 0.01069, 0.98849);
const mat3 W9300 = mat3(0.65840, 0.01604,  0.00411,  -0.02173, 0.72941,  0.01607,  -0.00579, 0.00776, 0.97982);
mat3 whiteOf(float w) { return w < 0.5 ? W6500 : (w < 1.5 ? W7500 : (w < 2.5 ? W8000 : W9300)); }

// The picture sampled pixel by pixel, blended only in the third of a pixel at each edge: at a screen's exact two
// rows and columns a pixel, each line and column is drawn whole, as a progressive picture's were; the sampler's own
// interpolation would soften every one.
float3 SRC(float2 uv)
{
    float2 p = uv * PICSIZE - 0.5;
    float2 i = floor(p);
    float2 f = clamp((p - i - 0.5) * 3.0 + 0.5, 0.0, 1.0);
    return TEX((i + 0.5 + f) / PICSIZE);
}

float3 tv(float2 uv, float2 frag, float rows)
{
    float3 c = SRC(uv);
    if (OPT_SIGNAL > 0.5) {
        float one = 1.0 / 320.0;
        bool composite = OPT_SIGNAL > 1.5;
        float sigma = 1.3 * one * (composite ? 1.25 : 1.0);
        float2 iq = float2(0.0, 0.0);
        float sum = 0.0;
        for (int k = -6; k <= 6; k++) {
            float w = exp(-0.125 * float(k * k));
            iq += w * (toYIQ * SRC(uv + float2(float(k) * 0.5 * sigma, 0.0))).yz;
            sum += w;
        }
        iq /= sum;
        float side = composite ? 0.25 : 0.1;
        float y = (toYIQ * c).x * (1.0 - 2.0 * side) + ((toYIQ * SRC(uv - float2(one, 0.0))).x + (toYIQ * SRC(uv + float2(one, 0.0))).x) * side;
        c = clamp(toRGB * float3(y, iq), 0.0, 1.0);
    }
    c = clamp(whiteOf(OPT_WHITE) * pow(c, float3(2.2, 2.2, 2.2)), 0.0, 1.0);
    float3 light = pow(c, float3(2.4 / 2.2, 2.4 / 2.2, 2.4 / 2.2));
    float lum = dot(light, float3(0.2126, 0.7152, 0.0722));
    float f = fract(frag.y / max(rows, 1.0));
    float depth = OPT_LINES * (1.0 - 0.5 * lum);
    light *= 1.0 - depth * (0.5 - 0.5 * cos(6.2831853 * (f - 0.25)));
    if (OPT_MASK > 0.0) {
        float m = OPT_MASK;
        float col = mod(floor(frag.x), 3.0);
        light *= col < 0.5 ? float3(1.0, 1.0 - m, 1.0 - m) : (col < 1.5 ? float3(1.0 - m, 1.0, 1.0 - m) : float3(1.0 - m, 1.0 - m, 1.0));
    }
    light = mix(float3(lum, lum, lum), light, OPT_SAT);
    return pow(clamp(light, 0.0, 1.0), float3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));
}
"""

    val slang = """
#version 450
// Timeline TV: PlayStation 2 as a television showed it. Written for Timeline by NOSTOS.
// Timeline is under the GNU General Public License, version 3 or later.

layout(push_constant) uniform Push
{
    vec4 SourceSize;
    vec4 OriginalSize;
    vec4 OutputSize;
    uint FrameCount;
    float tv_lines;
    float tv_signal;
    float tv_white;
    float tv_mask;
    float tv_sat;
} params;

#pragma parameter tv_lines "Line structure" 0.25 0.0 0.9 0.05
#pragma parameter tv_signal "Signal: 0 component, 1 S-video, 2 composite" 2.0 0.0 2.0 1.0
#pragma parameter tv_white "White: 0 6500K, 1 7500K, 2 8000K, 3 9300K" 2.0 0.0 3.0 1.0
#pragma parameter tv_mask "Aperture grille" 0.0 0.0 0.6 0.02
#pragma parameter tv_sat "Saturation" 1.0 0.0 2.0 0.05

layout(std140, set = 0, binding = 0) uniform UBO
{
    mat4 MVP;
} global;

#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main()
{
    gl_Position = global.MVP * Position;
    vTexCoord = TexCoord;
}

#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Source;

#define float2 vec2
#define float3 vec3
#define float4 vec4
vec3 TEX(vec2 uv) { return texture(Source, clamp(uv, vec2(0.0), vec2(1.0))).rgb; }
#define PICSIZE params.SourceSize.xy
#define OPT_LINES params.tv_lines
#define OPT_SIGNAL params.tv_signal
#define OPT_WHITE params.tv_white
#define OPT_MASK params.tv_mask
#define OPT_SAT params.tv_sat
$body
void main()
{
    FragColor = vec4(tv(vTexCoord, gl_FragCoord.xy, params.OutputSize.y / 480.0), 1.0);
}
""".trimStart()
}

/**
 * The signal a 3D console reached the television by, for pictures handed over wider than the console drew them
 * (PlayStation and Nintendo 64 at four times their detail are 1280 columns across). The collection's NTSC filter
 * measures everything in the core's own pixels, so it would treat those as 1280 real pixels; this one works in
 * fractions of the line, as a television's bandwidth does. In YIQ: color softened over about 1.3 of the console's
 * pixels (a home set decoded color at about 0.6 MHz), brightness nearly untouched for S-video, and for composite
 * averaged with its neighbors, which is what blended a console's dithered stripes into flat color. No dot crawl.
 * [slang] for Vulkan, [glsl] for RetroArch's OpenGL driver.
 */
/**
 * Dreamcast's beam, which keeps still pictures steady. While a
 * Dreamcast game loads, Flycast hands RetroArch a picture only every 100 ms (its threaded renderer waits five times
 * 20 ms for one that never comes), and each is drawn as the beam's pair: dark for 8 ms, lit for the rest, a blink ten
 * times a second. Measured on a Retroid Pocket Nova: gaps of exactly 100 ms at every load, none in play. This is Blur Busters' beam
 * reduced to the way Timeline runs it, drawing a moving picture exactly as the original does, with pixels unchanged for
 * three pictures drawn steady at the brightness they average while flashing. Pictures repeated by a game running at
 * 30 or 20 a second are not still: those keep flashing like any other, so no slower flicker is made.
 */
object SteadyBeam {
    const val SLANG = "timeline-beam.slang"

    val slang = """
#version 450

/*
 * Timeline's beam: the CRT beam simulator by Mark Rejhon and Timothy Lottes (Blur Busters, crt-beam-simulator.slang in
 * RetroArch's shader collection, MIT licence), reduced to the way Timeline runs it (no rolling scan, every slice drawn
 * from the current picture), with one change: a pixel that has not changed for three pictures is drawn steady, at the
 * brightness it averages while flashing. A moving picture is drawn exactly as the original draws it. A still one (a
 * loading screen, a pause, a menu) no longer blinks when the emulator hands over pictures unevenly, as Flycast does
 * while a Dreamcast game loads: one picture every 100 ms, each shown dark for 8 ms and lit for the rest.
 *
 * RetroArch copies the picture into its history once per slice, so with two slices a picture is two steps back.
 */

// The original's license, which covers this adaptation:
//
// MIT License
// 
// Copyright 2024 Mark Rejhon (@BlurBusters) & Timothy Lottes (@NOTimothyLottes)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the “Software”), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

layout(push_constant) uniform Push
{
	vec4 SourceSize;
	vec4 OriginalSize;
	vec4 OutputSize;
	uint FrameCount;
	uint TotalSubFrames;
	uint CurrentSubFrame;
	float GAIN_VS_BLUR, GAMMA, STEADY;
} params;

#pragma parameter GAIN_VS_BLUR "Brightness vs Clarity" 0.7 0.01 1.0 0.025
#pragma parameter GAMMA "Gamma" 2.4 1.0 5.0 0.01
#pragma parameter STEADY "Still pictures drawn steady" 1.0 0.0 1.0 1.0

layout(std140, set = 0, binding = 0) uniform UBO
{
	mat4 MVP;
} global;

#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main()
{
   gl_Position = global.MVP * Position;
   vTexCoord = TexCoord;
}

#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Source;
layout(set = 0, binding = 3) uniform sampler2D OriginalHistory2;
layout(set = 0, binding = 4) uniform sampler2D OriginalHistory4;
layout(set = 0, binding = 5) uniform sampler2D OriginalHistory6;

// The original's own conversions, kept as they are so a moving picture comes out identical.
float linear2srgb(float c)
{
    vec3 j = vec3(0.0031308 * 12.92, 12.92, 1.0 / params.GAMMA);
    vec2 k = vec2(1.055, -0.055);
    return clamp(j.x, c * j.y, pow(c, j.z) * k.x + k.y);
}
vec3 linear2srgb(vec3 c) { return vec3(linear2srgb(c.r), linear2srgb(c.g), linear2srgb(c.b)); }

float srgb2linear(float c)
{
    vec3 j = vec3(0.04045, 1.0 / 12.92, params.GAMMA);
    vec2 k = vec2(1.0 / 1.055, 0.055 / 1.055);
    return c > j.x ? pow(c * k.x + k.y, j.z) : c * j.y;
}
vec3 srgb2linear(vec3 c) { return vec3(srgb2linear(c.r), srgb2linear(c.g), srgb2linear(c.b)); }

void main()
{
   vec4 src = texture(Source, vTexCoord);
   FragColor = src;
   uint n = params.TotalSubFrames;
   if (n < 2u) return;

   // This refresh's light for each channel, measured in slices: emitted as early as it can be, a slice at most at a time.
   vec3 light = srgb2linear(src.rgb) * (float(n) * params.GAIN_VS_BLUR);

   if (params.STEADY > 0.5 && n == 2u)
   {
      vec3 a = texture(OriginalHistory2, vTexCoord).rgb;
      vec3 b = texture(OriginalHistory4, vTexCoord).rgb;
      vec3 c = texture(OriginalHistory6, vTexCoord).rgb;
      vec3 d = max(max(abs(src.rgb - a), abs(a - b)), abs(b - c));
      if (max(d.r, max(d.g, d.b)) < 1.0 / 256.0)
      {
         FragColor = vec4(linear2srgb(light / float(n)), 1.0);
         return;
      }
   }

   // Which slice of the refresh this is: RetroArch counts slices from 1, so the last one drawn is slice 0, the lit one.
   // (The original takes it from the frame count too, which comes to the same and loses precision after many hours.)
   float slot = float(params.CurrentSubFrame % n);
   FragColor = vec4(linear2srgb(clamp(light - slot, 0.0, 1.0)), 1.0);
}
""".trimStart()
}

object Signal {
    const val SLANG = "timeline-signal.slang"
    const val GLSL = "timeline-signal.glsl"

    /** The value of tl_signal for a choice: 1 S-video, 2 composite. */
    fun value(choice: String): String = if (choice == "Composite") "2.0" else "1.0"

    val slang = """
#version 450
// Timeline signal: S-video or composite softness for a 3D console's picture. Written for Timeline by NOSTOS.

layout(push_constant) uniform Push
{
    vec4 SourceSize;
    vec4 OriginalSize;
    vec4 OutputSize;
    uint FrameCount;
    float tl_signal;
    float tl_width;
} params;

#pragma parameter tl_signal "Signal: 1 S-video, 2 composite" 1.0 1.0 2.0 1.0
#pragma parameter tl_width "The console's own width, in its pixels" 320.0 160.0 720.0 8.0

layout(std140, set = 0, binding = 0) uniform UBO
{
    mat4 MVP;
} global;

#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main()
{
    gl_Position = global.MVP * Position;
    vTexCoord = TexCoord;
}

#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Source;

const mat3 toYIQ = mat3(0.299, 0.596, 0.211, 0.587, -0.274, -0.523, 0.114, -0.322, 0.312);
const mat3 toRGB = mat3(1.0, 1.0, 1.0, 0.956, -0.272, -1.106, 0.621, -0.647, 1.703);

vec3 yiq(float dx) { return toYIQ * texture(Source, vTexCoord + vec2(dx, 0.0)).rgb; }

void main()
{
    float one = 1.0 / params.tl_width;                 // one of the console's pixels, across the line
    bool composite = params.tl_signal > 1.5;
    float sigma = 1.3 * one * (composite ? 1.25 : 1.0);
    vec2 iq = vec2(0.0);
    float sum = 0.0;
    for (int k = -6; k <= 6; k++) {
        float w = exp(-0.125 * float(k * k));          // a Gaussian sampled at half-sigma steps, out to three sigma
        iq += w * yiq(float(k) * 0.5 * sigma).yz;
        sum += w;
    }
    iq /= sum;
    float side = composite ? 0.25 : 0.1;
    float y = yiq(0.0).x * (1.0 - 2.0 * side) + (yiq(-one).x + yiq(one).x) * side;
    FragColor = vec4(clamp(toRGB * vec3(y, iq), 0.0, 1.0), 1.0);
}
""".trimStart()

    val glsl = """
// Timeline signal: S-video or composite softness for a 3D console's picture, in RetroArch's GLSL. Written for Timeline by NOSTOS.
#pragma parameter tl_signal "Signal: 1 S-video, 2 composite" 1.0 1.0 2.0 1.0
#pragma parameter tl_width "The console's own width, in its pixels" 320.0 160.0 720.0 8.0
#if defined(VERTEX)
#if __VERSION__ >= 130
#define COMPAT_VARYING out
#define COMPAT_ATTRIBUTE in
#else
#define COMPAT_VARYING varying
#define COMPAT_ATTRIBUTE attribute
#endif
COMPAT_ATTRIBUTE vec4 VertexCoord;
COMPAT_ATTRIBUTE vec4 TexCoord;
COMPAT_VARYING vec4 TEX0;
uniform mat4 MVPMatrix;
void main()
{
    gl_Position = MVPMatrix * VertexCoord;
    TEX0.xy = TexCoord.xy;
}
#elif defined(FRAGMENT)
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif
#if __VERSION__ >= 130
#define COMPAT_VARYING in
#define COMPAT_TEXTURE texture
out vec4 FragColor;
#else
#define COMPAT_VARYING varying
#define FragColor gl_FragColor
#define COMPAT_TEXTURE texture2D
#endif
uniform sampler2D Texture;
uniform vec2 TextureSize;
uniform vec2 InputSize;
COMPAT_VARYING vec4 TEX0;
#ifdef PARAMETER_UNIFORM
uniform float tl_signal, tl_width;
#else
#define tl_signal 1.0
#define tl_width 320.0
#endif

vec3 yiq(float dx)
{
    vec3 c = COMPAT_TEXTURE(Texture, TEX0.xy + vec2(dx, 0.0)).rgb;
    return vec3(dot(c, vec3(0.299, 0.587, 0.114)), dot(c, vec3(0.596, -0.274, -0.322)), dot(c, vec3(0.211, -0.523, 0.312)));
}

void main()
{
    float one = (InputSize.x / TextureSize.x) / tl_width;   // one of the console's pixels, in texture coordinates
    bool composite = tl_signal > 1.5;
    float sigma = 1.3 * one * (composite ? 1.25 : 1.0);
    vec2 iq = vec2(0.0);
    float sum = 0.0;
    for (int k = -6; k <= 6; k++) {
        float w = exp(-0.125 * float(k * k));
        iq += w * yiq(float(k) * 0.5 * sigma).yz;
        sum += w;
    }
    iq /= sum;
    float side = composite ? 0.25 : 0.1;
    float y = yiq(0.0).x * (1.0 - 2.0 * side) + (yiq(-one).x + yiq(one).x) * side;
    vec3 rgb = vec3(y + 0.956 * iq.x + 0.621 * iq.y, y - 0.272 * iq.x - 0.647 * iq.y, y - 1.106 * iq.x + 1.703 * iq.y);
    FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
#endif
""".trimStart()
}

/**
 * The television's own colour, between the signal and the tube: a picture's RGB read as an American set's phosphors
 * (SMPTE-C, the P22 set consumer tubes used from the 1970s on) and shown at that set's white, on this sRGB panel.
 * Sets were sold far bluer than the broadcast standard's 6500 K: a 1993 survey of American receivers found 5,700 K to
 * 13,700 K, averaging 8,000 K (Donofrio, Hess and Sember, "The White Color of Television Receivers"), and Japanese
 * sets and monitors ran at 9,300 K. Each matrix maps linear SMPTE-C to linear sRGB, Bradford-adapted from 6500 K to
 * that white, and is scaled so white's largest channel is exactly 1, so nothing clips. Worked in 2.2-power light,
 * which for matrices this close to diagonal differs from the tube's own curve by well under one just-noticeable step.
 * Written for Timeline by NOSTOS (2026-11-02).
 */
object Colour {
    const val SLANG = "timeline-colour.slang"
    const val GLSL = "timeline-colour.glsl"

    private const val MATRICES = """
const mat3 W6500 = mat3(0.93954, 0.01777, -0.00162,   0.05018, 0.96579, -0.00437,   0.01028, 0.01643, 1.00599);
const mat3 W7500 = mat3(0.79920, 0.01712,  0.00122,   0.01097, 0.85092,  0.00565,   0.00212, 0.01223, 0.99313);
const mat3 W8000 = mat3(0.74958, 0.01679,  0.00224,  -0.00131, 0.80881,  0.00928,  -0.00070, 0.01069, 0.98849);
const mat3 W9300 = mat3(0.65840, 0.01604,  0.00411,  -0.02173, 0.72941,  0.01607,  -0.00579, 0.00776, 0.97982);
mat3 white(float w) { return w < 0.5 ? W6500 : (w < 1.5 ? W7500 : (w < 2.5 ? W8000 : W9300)); }
"""

    val slang = """
#version 450
// Timeline colour: an American set's phosphors (SMPTE-C) and white point, shown on an sRGB panel. Written for Timeline by NOSTOS.

layout(push_constant) uniform Push
{
    vec4 SourceSize;
    vec4 OriginalSize;
    vec4 OutputSize;
    uint FrameCount;
    float tl_white;
} params;

#pragma parameter tl_white "White: 0 6500K, 1 7500K, 2 8000K, 3 9300K" 2.0 0.0 3.0 1.0

layout(std140, set = 0, binding = 0) uniform UBO
{
    mat4 MVP;
} global;

#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main()
{
    gl_Position = global.MVP * Position;
    vTexCoord = TexCoord;
}

#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Source;
$MATRICES
void main()
{
    vec3 c = pow(texture(Source, vTexCoord).rgb, vec3(2.2));
    FragColor = vec4(pow(clamp(white(params.tl_white) * c, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
}
""".trimStart()

    val glsl = """
// Timeline colour: an American set's phosphors (SMPTE-C) and white point, shown on an sRGB panel, in RetroArch's GLSL. Written for Timeline by NOSTOS.
#pragma parameter tl_white "White: 0 6500K, 1 7500K, 2 8000K, 3 9300K" 2.0 0.0 3.0 1.0
#if defined(VERTEX)
#if __VERSION__ >= 130
#define COMPAT_VARYING out
#define COMPAT_ATTRIBUTE in
#else
#define COMPAT_VARYING varying
#define COMPAT_ATTRIBUTE attribute
#endif
COMPAT_ATTRIBUTE vec4 VertexCoord;
COMPAT_ATTRIBUTE vec4 TexCoord;
COMPAT_VARYING vec4 TEX0;
uniform mat4 MVPMatrix;
void main()
{
    gl_Position = MVPMatrix * VertexCoord;
    TEX0.xy = TexCoord.xy;
}
#elif defined(FRAGMENT)
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif
#if __VERSION__ >= 130
#define COMPAT_VARYING in
#define COMPAT_TEXTURE texture
out vec4 FragColor;
#else
#define COMPAT_VARYING varying
#define FragColor gl_FragColor
#define COMPAT_TEXTURE texture2D
#endif
uniform sampler2D Texture;
COMPAT_VARYING vec4 TEX0;
#ifdef PARAMETER_UNIFORM
uniform float tl_white;
#else
#define tl_white 2.0
#endif
$MATRICES
void main()
{
    vec3 c = pow(COMPAT_TEXTURE(Texture, TEX0.xy).rgb, vec3(2.2));
    FragColor = vec4(pow(clamp(white(tl_white) * c, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
}
#endif
""".trimStart()
}

/**
 * The Genesis's colour levels. Its video chip turns each 3-bit level (15 with shadow and highlight) into a voltage
 * through a converter that is not linear: the dark levels come out markedly brighter than an even ramp. Genesis Plus GX
 * hands over the even ramp (0, 33, 66, 99, 140, 173, 206, 239). This pass reads each level back exactly and puts the
 * measured one in its place: BlastEm's table from voltage measurements on the console (Genesis Plus GX issue 345:
 * 0, 27, 49, 71, 87, 103, 119, 130, 146, 157, 174, 190, 206, 228, 255), scaled so the top stays at the core's 239,
 * since only the shape was measured. Normal levels are entries 0, 2 ... 14, shadow 0 to 7, highlight 7 to 14. For the
 * Genesis's own mode only: the Master System and Game Gear use different levels and do not get it.
 * Written for Timeline by NOSTOS (2026-11-02).
 */
object GenesisDac {
    const val SLANG = "timeline-genesis-dac.slang"

    val slang = """
#version 450
// Timeline: the Genesis's measured colour levels (BlastEm's DAC table) for Genesis Plus GX. Written for Timeline by NOSTOS.

layout(push_constant) uniform Push
{
    vec4 SourceSize;
    vec4 OriginalSize;
    vec4 OutputSize;
    uint FrameCount;
} params;

layout(std140, set = 0, binding = 0) uniform UBO
{
    mat4 MVP;
} global;

#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main()
{
    gl_Position = global.MVP * Position;
    vTexCoord = TexCoord;
}

#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Source;

const float L[15] = float[15](0.0, 27.0, 49.0, 71.0, 87.0, 103.0, 119.0, 130.0, 146.0, 157.0, 174.0, 190.0, 206.0, 228.0, 255.0);

void main()
{
    vec3 c = texture(Source, vTexCoord).rgb;
    // Genesis Plus GX's 15 levels, from either of its pixel formats, read back exactly as 0 to 14.
    ivec3 k = clamp(ivec3(floor(c * 15.0 + 0.5)), ivec3(0), ivec3(14));
    FragColor = vec4(vec3(L[k.r], L[k.g], L[k.b]) * (239.0 / 255.0) / 255.0, 1.0);
}
""".trimStart()
}
