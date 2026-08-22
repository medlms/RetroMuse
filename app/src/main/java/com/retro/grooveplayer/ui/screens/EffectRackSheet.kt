package com.retro.grooveplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.dsp.Modulation
import com.retro.grooveplayer.dsp.RackSettings
import com.retro.grooveplayer.dsp.Saturator
import com.retro.grooveplayer.ui.theme.*

/**
 * The live editing rack. Every control takes effect on the playing audio immediately,
 * and the same [RackSettings] drive the offline render when the user saves.
 */
@Composable
fun EffectRackContent(accentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Text(
            text = "Everything here applies to the audio as it plays. Use Save & Share to render it to a file.",
            color = TextSecondaryColor,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(2.dp))

        RackSection("Frequency", accentColor)

        EffectUnit(
            title = "Parametric EQ",
            subtitle = "5 bands, with a dynamic mode",
            enabled = RackSettings.eqEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.eqEnabled = it; RackSettings.touch() }
        ) {
            val labels = listOf("80 Hz", "250 Hz", "1 kHz", "4 kHz", "10 kHz")
            labels.forEachIndexed { index, label ->
                val gains = RackSettings.eqBandGains
                RackSlider(
                    label = label,
                    value = gains.getOrElse(index) { 0f },
                    range = -12f..12f,
                    display = { "${it.toInt()} dB" },
                    accentColor = accentColor
                ) { newValue ->
                    RackSettings.eqBandGains = gains.toMutableList().also { it[index] = newValue }
                    RackSettings.touch()
                }
            }
            RackToggleRow(
                label = "Dynamic mode",
                hint = "Only acts when the band gets loud",
                checked = RackSettings.eqDynamic,
                accentColor = accentColor
            ) { RackSettings.eqDynamic = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Resonance Suppressor",
            subtitle = "Tames harsh ringing frequencies",
            enabled = RackSettings.resonanceEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.resonanceEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Amount",
                value = RackSettings.resonanceAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.resonanceAmount = it; RackSettings.touch() }
        }

        RackSection("Dynamics", accentColor)

        EffectUnit(
            title = "Multiband Compressor",
            subtitle = "Evens out low, mid and high separately",
            enabled = RackSettings.multibandEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.multibandEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Strength",
                value = RackSettings.multibandAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.multibandAmount = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Transient Shaper",
            subtitle = "Sharpen or soften attacks and tails",
            enabled = RackSettings.transientEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.transientEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Attack",
                value = RackSettings.transientAttack,
                range = -1f..1f,
                display = { if (it > 0) "+${(it * 100).toInt()}" else "${(it * 100).toInt()}" },
                accentColor = accentColor
            ) { RackSettings.transientAttack = it; RackSettings.touch() }
            RackSlider(
                label = "Sustain",
                value = RackSettings.transientSustain,
                range = -1f..1f,
                display = { if (it > 0) "+${(it * 100).toInt()}" else "${(it * 100).toInt()}" },
                accentColor = accentColor
            ) { RackSettings.transientSustain = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Sidechain Pump",
            subtitle = "Rhythmic ducking, the EDM breathing effect",
            enabled = RackSettings.pumpEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.pumpEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Tempo",
                value = RackSettings.pumpBpm,
                range = 60f..180f,
                display = { "${it.toInt()} BPM" },
                accentColor = accentColor
            ) { RackSettings.pumpBpm = it; RackSettings.touch() }
            RackSlider(
                label = "Depth",
                value = RackSettings.pumpDepth,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.pumpDepth = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Gate & Stutter",
            subtitle = "Silence quiet parts, or chop rhythmic gaps",
            enabled = RackSettings.gateEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.gateEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Threshold",
                value = RackSettings.gateThresholdDb,
                range = -70f..-10f,
                display = { "${it.toInt()} dB" },
                accentColor = accentColor
            ) { RackSettings.gateThresholdDb = it; RackSettings.touch() }
            RackSlider(
                label = "Stutter",
                value = RackSettings.gateStutterHz,
                range = 0f..16f,
                display = { if (it < 0.5f) "Off" else "${it.toInt()} Hz" },
                accentColor = accentColor
            ) { RackSettings.gateStutterHz = it; RackSettings.touch() }
        }

        RackSection("Harmonics & Texture", accentColor)

        EffectUnit(
            title = "Saturation",
            subtitle = "Analog warmth and presence",
            enabled = RackSettings.saturationEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.saturationEnabled = it; RackSettings.touch() }
        ) {
            RackChoice(
                options = listOf(
                    Saturator.Character.TAPE to "Tape",
                    Saturator.Character.TUBE to "Tube",
                    Saturator.Character.TRANSISTOR to "Transistor"
                ),
                selected = RackSettings.saturationCharacter,
                accentColor = accentColor
            ) { RackSettings.saturationCharacter = it; RackSettings.touch() }
            RackSlider(
                label = "Drive",
                value = RackSettings.saturationDrive,
                range = 1f..10f,
                display = { String.format("%.1fx", it) },
                accentColor = accentColor
            ) { RackSettings.saturationDrive = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Bitcrusher",
            subtitle = "Lo-fi bit depth and sample rate reduction",
            enabled = RackSettings.bitcrushEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.bitcrushEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Bit depth",
                value = RackSettings.bitcrushBits,
                range = 3f..16f,
                display = { "${it.toInt()} bit" },
                accentColor = accentColor
            ) { RackSettings.bitcrushBits = it; RackSettings.touch() }
            RackSlider(
                label = "Downsample",
                value = RackSettings.bitcrushDownsample,
                range = 1f..16f,
                display = { "1/${it.toInt()}" },
                accentColor = accentColor
            ) { RackSettings.bitcrushDownsample = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Vinyl Dust",
            subtitle = "Crackle, hiss and turntable rumble",
            enabled = RackSettings.dustEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.dustEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Amount",
                value = RackSettings.dustAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.dustAmount = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Wow & Flutter",
            subtitle = "Worn tape pitch drift",
            enabled = RackSettings.wowFlutterEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.wowFlutterEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Amount",
                value = RackSettings.wowFlutterAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.wowFlutterAmount = it; RackSettings.touch() }
        }

        RackSection("Modulation & Space", accentColor)

        EffectUnit(
            title = "Modulation",
            subtitle = "Chorus, flanger, phaser or tremolo",
            enabled = RackSettings.modulationEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.modulationEnabled = it; RackSettings.touch() }
        ) {
            RackChoice(
                options = listOf(
                    Modulation.Type.CHORUS to "Chorus",
                    Modulation.Type.FLANGER to "Flanger",
                    Modulation.Type.PHASER to "Phaser",
                    Modulation.Type.TREMOLO to "Tremolo"
                ),
                selected = RackSettings.modulationType,
                accentColor = accentColor
            ) { RackSettings.modulationType = it; RackSettings.touch() }
            RackSlider(
                label = "Rate",
                value = RackSettings.modulationRate,
                range = 0.05f..8f,
                display = { String.format("%.2f Hz", it) },
                accentColor = accentColor
            ) { RackSettings.modulationRate = it; RackSettings.touch() }
            RackSlider(
                label = "Depth",
                value = RackSettings.modulationDepth,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.modulationDepth = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Delay",
            subtitle = "Stereo and ping-pong echoes",
            enabled = RackSettings.delayEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.delayEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Time",
                value = RackSettings.delayTimeMs,
                range = 40f..1200f,
                display = { "${it.toInt()} ms" },
                accentColor = accentColor
            ) { RackSettings.delayTimeMs = it; RackSettings.touch() }
            RackSlider(
                label = "Feedback",
                value = RackSettings.delayFeedback,
                range = 0f..0.85f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.delayFeedback = it; RackSettings.touch() }
            RackSlider(
                label = "Mix",
                value = RackSettings.delayMix,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.delayMix = it; RackSettings.touch() }
            RackToggleRow(
                label = "Ping-pong",
                hint = "Echoes alternate between ears",
                checked = RackSettings.delayPingPong,
                accentColor = accentColor
            ) { RackSettings.delayPingPong = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Reverb",
            subtitle = "Room, hall and plate spaces",
            enabled = RackSettings.reverbEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.reverbEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Size",
                value = RackSettings.reverbSize,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.reverbSize = it; RackSettings.touch() }
            RackSlider(
                label = "Damping",
                value = RackSettings.reverbDamping,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.reverbDamping = it; RackSettings.touch() }
            RackSlider(
                label = "Mix",
                value = RackSettings.reverbMix,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.reverbMix = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Stereo Imaging",
            subtitle = "Width, mono bass and Haas widening",
            enabled = RackSettings.imagerEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.imagerEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Width",
                value = RackSettings.stereoWidth,
                range = 0f..2f,
                display = { if (it < 0.05f) "Mono" else "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.stereoWidth = it; RackSettings.touch() }
            RackSlider(
                label = "Bass to mono",
                value = RackSettings.bassMonoHz,
                range = 20f..300f,
                display = { if (it <= 21f) "Off" else "below ${it.toInt()} Hz" },
                accentColor = accentColor
            ) { RackSettings.bassMonoHz = it; RackSettings.touch() }
            RackSlider(
                label = "Haas",
                value = RackSettings.haasMs,
                range = 0f..30f,
                display = { if (it < 0.5f) "Off" else String.format("%.1f ms", it) },
                accentColor = accentColor
            ) { RackSettings.haasMs = it; RackSettings.touch() }
        }

        RackSection("Voice", accentColor)

        EffectUnit(
            title = "Formant Shift",
            subtitle = "Change vocal character without changing pitch",
            enabled = RackSettings.formantEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.formantEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Shift",
                value = RackSettings.formantSemitones,
                range = -12f..12f,
                display = { "${it.toInt()} st" },
                accentColor = accentColor
            ) { RackSettings.formantSemitones = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Doubler",
            subtitle = "Makes one take sound like two",
            enabled = RackSettings.doublerEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.doublerEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Amount",
                value = RackSettings.doublerAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.doublerAmount = it; RackSettings.touch() }
        }

        RackSection("Master", accentColor)

        EffectUnit(
            title = "Brickwall Limiter",
            subtitle = "Stops peaks clipping. Leave this on.",
            enabled = RackSettings.limiterEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.limiterEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Ceiling",
                value = RackSettings.limiterCeilingDb,
                range = -6f..0f,
                display = { String.format("%.1f dB", it) },
                accentColor = accentColor
            ) { RackSettings.limiterCeilingDb = it; RackSettings.touch() }
        }

        RackSlider(
            label = "Output gain",
            value = RackSettings.outputGainDb,
            range = -12f..12f,
            display = { "${it.toInt()} dB" },
            accentColor = accentColor
        ) { RackSettings.outputGainDb = it; RackSettings.touch() }

        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(99.dp))
                .background(BgSunkenColor)
                .clickable { RackSettings.resetAll() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Reset all effects",
                color = DangerColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RackSection(title: String, accentColor: Color) {
    Text(
        text = title.uppercase(),
        color = accentColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

/** One rack unit: header with a switch, and controls revealed only when it is on. */
@Composable
private fun EffectUnit(
    title: String,
    subtitle: String,
    enabled: Boolean,
    accentColor: Color,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) accentColor.copy(alpha = 0.07f) else BgSunkenColor)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimaryColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = subtitle, color = TextMutedColor, fontSize = 12.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor
                )
            )
        }
        AnimatedVisibility(visible = enabled) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    }
}

@Composable
private fun RackSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    accentColor: Color,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextSecondaryColor, fontSize = 12.sp)
            Text(display(value), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = BorderColor
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
private fun RackToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    accentColor: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextSecondaryColor, fontSize = 12.5.sp)
            Text(hint, color = TextMutedColor, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun <T> RackChoice(
    options: List<Pair<T, String>>,
    selected: T,
    accentColor: Color,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isSelected) accentColor else BgCardColor)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else TextSecondaryColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
