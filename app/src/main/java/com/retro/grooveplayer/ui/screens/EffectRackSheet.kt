package com.retro.grooveplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.dsp.ConvolutionReverb
import com.retro.grooveplayer.dsp.Modulation
import com.retro.grooveplayer.dsp.RackSettings
import com.retro.grooveplayer.dsp.Saturator
import com.retro.grooveplayer.playback.PlaybackManager
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

        MasterStrip(accentColor)
        PresetBar(accentColor)

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

        EffectUnit(
            title = "De-esser",
            subtitle = "Ducks harsh S sounds only",
            enabled = RackSettings.deEsserEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.deEsserEnabled = it; RackSettings.touch() }
        ) {
            MeterBar("Reduction", RackSettings.meterDeEsser, DangerColor)
            RackSlider(
                label = "Frequency",
                value = RackSettings.deEsserFrequency,
                range = 3000f..12000f,
                display = { "${(it / 1000).toInt()}.${((it % 1000) / 100).toInt()} kHz" },
                accentColor = accentColor
            ) { RackSettings.deEsserFrequency = it; RackSettings.touch() }
            RackSlider(
                label = "Amount",
                value = RackSettings.deEsserAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.deEsserAmount = it; RackSettings.touch() }
        }

        RackSection("Dynamics", accentColor)

        EffectUnit(
            title = "Compressor",
            subtitle = "Full-band glue",
            enabled = RackSettings.compressorEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.compressorEnabled = it; RackSettings.touch() }
        ) {
            MeterBar("Reduction", RackSettings.meterCompressor, DangerColor)
            RackSlider(
                label = "Threshold",
                value = RackSettings.compressorThresholdDb,
                range = -48f..0f,
                display = { "${it.toInt()} dB" },
                accentColor = accentColor
            ) { RackSettings.compressorThresholdDb = it; RackSettings.touch() }
            RackSlider(
                label = "Ratio",
                value = RackSettings.compressorRatio,
                range = 1f..20f,
                display = { String.format("%.1f:1", it) },
                accentColor = accentColor
            ) { RackSettings.compressorRatio = it; RackSettings.touch() }
            RackSlider(
                label = "Makeup",
                value = RackSettings.compressorMakeupDb,
                range = 0f..18f,
                display = { "+${it.toInt()} dB" },
                accentColor = accentColor
            ) { RackSettings.compressorMakeupDb = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Multiband Compressor",
            subtitle = "Evens out low, mid and high separately",
            enabled = RackSettings.multibandEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.multibandEnabled = it; RackSettings.touch() }
        ) {
            val (lowGr, midGr, highGr) = RackSettings.meterMultiband
            MeterBar("Low", lowGr, DangerColor)
            MeterBar("Mid", midGr, DangerColor)
            MeterBar("High", highGr, DangerColor)
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
            title = "Exciter",
            subtitle = "Adds air and detail up top",
            enabled = RackSettings.exciterEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.exciterEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Frequency",
                value = RackSettings.exciterFrequency,
                range = 1500f..10000f,
                display = { "${(it / 1000).toInt()} kHz" },
                accentColor = accentColor
            ) { RackSettings.exciterFrequency = it; RackSettings.touch() }
            RackSlider(
                label = "Amount",
                value = RackSettings.exciterAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.exciterAmount = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Sub Bass",
            subtitle = "Synthesises an octave below the bass",
            enabled = RackSettings.subBassEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.subBassEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Amount",
                value = RackSettings.subBassAmount,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.subBassAmount = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Ring Modulator",
            subtitle = "Metallic, robotic tones",
            enabled = RackSettings.ringModEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.ringModEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Frequency",
                value = RackSettings.ringModFrequency,
                range = 20f..2000f,
                display = { "${it.toInt()} Hz" },
                accentColor = accentColor
            ) { RackSettings.ringModFrequency = it; RackSettings.touch() }
            RackSlider(
                label = "Mix",
                value = RackSettings.ringModMix,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.ringModMix = it; RackSettings.touch() }
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
            title = "Auto-Wah",
            subtitle = "Filter sweep driven by the signal",
            enabled = RackSettings.autoWahEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.autoWahEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Sensitivity",
                value = RackSettings.autoWahSensitivity,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.autoWahSensitivity = it; RackSettings.touch() }
            RackSlider(
                label = "Resonance",
                value = RackSettings.autoWahResonance,
                range = 1f..12f,
                display = { String.format("Q %.1f", it) },
                accentColor = accentColor
            ) { RackSettings.autoWahResonance = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Auto-Pan",
            subtitle = "Sweeps the image between the ears",
            enabled = RackSettings.autoPanEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.autoPanEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Rate",
                value = RackSettings.autoPanRate,
                range = 0.05f..6f,
                display = { String.format("%.2f Hz", it) },
                accentColor = accentColor
            ) { RackSettings.autoPanRate = it; RackSettings.touch() }
            RackSlider(
                label = "Depth",
                value = RackSettings.autoPanDepth,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.autoPanDepth = it; RackSettings.touch() }
        }

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
            title = "Convolution Reverb",
            subtitle = "Modelled spaces. Heavier on the CPU.",
            enabled = RackSettings.convolutionEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.convolutionEnabled = it; RackSettings.touch() }
        ) {
            RackChoice(
                options = listOf(
                    ConvolutionReverb.Space.ROOM to "Room",
                    ConvolutionReverb.Space.HALL to "Hall",
                    ConvolutionReverb.Space.PLATE to "Plate",
                    ConvolutionReverb.Space.CATHEDRAL to "Cathedral"
                ),
                selected = RackSettings.convolutionSpace,
                accentColor = accentColor
            ) { RackSettings.convolutionSpace = it; RackSettings.touch() }
            RackSlider(
                label = "Mix",
                value = RackSettings.convolutionMix,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.convolutionMix = it; RackSettings.touch() }
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

        RackSection("Voice & Pitch", accentColor)

        EffectUnit(
            title = "Pitch Shift",
            subtitle = "Transpose without changing tempo",
            enabled = RackSettings.pitchShiftEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.pitchShiftEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Shift",
                value = RackSettings.pitchShiftSemitones,
                range = -12f..12f,
                display = { if (it > 0) "+${it.toInt()} st" else "${it.toInt()} st" },
                accentColor = accentColor
            ) { RackSettings.pitchShiftSemitones = it; RackSettings.touch() }
        }

        EffectUnit(
            title = "Scale Snap",
            subtitle = "Pulls pitch onto a key. The autotune effect.",
            enabled = RackSettings.scaleSnapEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.scaleSnapEnabled = it; RackSettings.touch() }
        ) {
            RackChoice(
                options = listOf(
                    "Major" to "Major",
                    "Minor" to "Minor",
                    "Pentatonic" to "Penta",
                    "Chromatic" to "Chrom"
                ),
                selected = RackSettings.scaleSnapScale,
                accentColor = accentColor
            ) { RackSettings.scaleSnapScale = it; RackSettings.touch() }
            RackSlider(
                label = "Key",
                value = RackSettings.scaleSnapRoot.toFloat(),
                range = 0f..11f,
                display = { NOTE_NAMES[it.toInt().coerceIn(0, 11)] },
                accentColor = accentColor
            ) { RackSettings.scaleSnapRoot = it.toInt(); RackSettings.touch() }
            RackSlider(
                label = "Strength",
                value = RackSettings.scaleSnapStrength,
                range = 0f..1f,
                display = { "${(it * 100).toInt()}%" },
                accentColor = accentColor
            ) { RackSettings.scaleSnapStrength = it; RackSettings.touch() }
        }

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

        RackSection("Transport", accentColor)

        EffectUnit(
            title = "Tape Stop",
            subtitle = "Brakes the track to a halt, then releases",
            enabled = RackSettings.tapeStopEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.tapeStopEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Duration",
                value = RackSettings.tapeStopDurationMs,
                range = 150f..4000f,
                display = { "${it.toInt()} ms" },
                accentColor = accentColor
            ) { RackSettings.tapeStopDurationMs = it; RackSettings.touch() }

            // Momentary: hold to brake, release to spin back up.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (RackSettings.tapeStopEngaged) DangerColor else BgCardColor)
                    .clickable {
                        RackSettings.tapeStopEngaged = !RackSettings.tapeStopEngaged
                        RackSettings.touch()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (RackSettings.tapeStopEngaged) "Release" else "Engage brake",
                    color = if (RackSettings.tapeStopEngaged) Color.White else TextPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        EffectUnit(
            title = "Reverse",
            subtitle = "Plays each slice backwards",
            enabled = RackSettings.reverseEnabled,
            accentColor = accentColor,
            onToggle = { RackSettings.reverseEnabled = it; RackSettings.touch() }
        ) {
            RackSlider(
                label = "Slice",
                value = RackSettings.reverseSliceMs,
                range = 60f..2000f,
                display = { "${it.toInt()} ms" },
                accentColor = accentColor
            ) { RackSettings.reverseSliceMs = it; RackSettings.touch() }
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

    }
}

private val NOTE_NAMES = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
)

/**
 * Master strip: bypass for instant A/B, a global dry/wet, and the output and limiter
 * meters. Without these you are editing 20-odd units with no way to hear or see what
 * they are collectively doing.
 */
@Composable
private fun MasterStrip(accentColor: Color) {
    // Meters are pulled on a timer rather than per audio frame.
    LaunchedEffect(Unit) {
        while (true) {
            PlaybackManager.publishRackMeters()
            kotlinx.coroutines.delay(60)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSunkenColor)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Master", color = TextPrimaryColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (RackSettings.masterBypass) "Bypassed - hearing the original" else "Rack engaged",
                    color = if (RackSettings.masterBypass) DangerColor else TextMutedColor,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (RackSettings.masterBypass) DangerColor else accentColor)
                    .clickable {
                        RackSettings.masterBypass = !RackSettings.masterBypass
                        RackSettings.touch()
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (RackSettings.masterBypass) "A" else "B",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        MeterBar("Output", RackSettings.meterOutput, accentColor)
        MeterBar("Limiting", RackSettings.meterLimiter, DangerColor)

        Spacer(Modifier.height(6.dp))
        RackSlider(
            label = "Dry / Wet",
            value = RackSettings.dryWet,
            range = 0f..1f,
            display = { "${(it * 100).toInt()}% wet" },
            accentColor = accentColor
        ) { RackSettings.dryWet = it; RackSettings.touch() }
    }
}

/** Save, load and delete named chains. */
@Composable
private fun PresetBar(accentColor: Color) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(BgSunkenColor)
                    .clickable { presetName = ""; showSaveDialog = true }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Save chain", color = TextPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(BgSunkenColor)
                    .clickable { RackSettings.resetAll() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Reset all", color = DangerColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        val presets = PlaybackManager.userRackPresets
        if (presets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            presets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSunkenColor)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preset.name,
                        color = TextPrimaryColor,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { PlaybackManager.loadRackPreset(preset) }
                    )
                    Text(
                        text = "Load",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { PlaybackManager.loadRackPreset(preset) }
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "✕",
                        color = TextMutedColor,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { PlaybackManager.deleteRackPreset(preset.id) }
                            .padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = BgModalColor,
            title = { Text("Save chain", color = TextPrimaryColor, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    singleLine = true,
                    label = { Text("Name", color = TextMutedColor) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryColor,
                        unfocusedTextColor = TextPrimaryColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = BorderColor
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = presetName.trim()
                    if (name.isNotEmpty()) PlaybackManager.saveRackPreset(name)
                    showSaveDialog = false
                }) {
                    Text("Save", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = TextSecondaryColor)
                }
            }
        )
    }
}

/** Horizontal level bar, 0..1. */
@Composable
private fun MeterBar(label: String, value: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMutedColor,
            fontSize = 11.sp,
            modifier = Modifier.width(64.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BorderColor)
                .semantics {
                    contentDescription = label
                    stateDescription = "${(value.coerceIn(0f, 1f) * 100).toInt()} percent"
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
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
            // One label for the pair, so a screen reader reads the effect name and
            // its description together instead of two orphaned fragments.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$title. $subtitle"
                    }
            ) {
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
                ),
                modifier = Modifier.semantics { contentDescription = "Enable $title" }
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
            // Without these a screen reader announces "50 percent" with no idea of
            // which control, and the numeric label is invisible to it.
            modifier = Modifier
                .height(28.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = display(value)
                }
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
