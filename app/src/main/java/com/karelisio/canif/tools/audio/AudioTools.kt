package com.karelisio.canif.tools.audio

import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.Waves
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val audioTools: List<Tool> = listOf(
    Tool(
        id = "tuner",
        name = Txt("Accordeur", "Tuner"),
        category = Category.AUDIO,
        icon = Icons.Rounded.MusicNote,
        keywords = listOf("accordeur", "tuner", "guitare", "guitar", "basse", "bass", "ukulele", "note", "chromatique", "chromatic", "justesse", "pitch", "la 440", "a440", "violon"),
        description = Txt("Accordeur chromatique et cordes (guitare, basse, ukulélé)", "Chromatic and string tuner (guitar, bass, ukulele)"),
        features = listOf(PackageManager.FEATURE_MICROPHONE),
        permissions = listOf(AppPermission.MICROPHONE),
    ) { TunerScreen() },
    Tool(
        id = "metronome",
        name = Txt("Métronome", "Metronome"),
        category = Category.AUDIO,
        icon = Icons.Rounded.AvTimer,
        keywords = listOf("metronome", "tempo", "bpm", "rythme", "rhythm", "mesure", "beat", "tap", "click", "battement"),
        description = Txt("Tempo précis, mesures, accents et subdivisions", "Precise tempo, time signatures, accents and subdivisions"),
    ) { MetronomeScreen() },
    Tool(
        id = "tone-gen",
        name = Txt("Générateur de fréquences", "Tone generator"),
        category = Category.AUDIO,
        icon = Icons.Rounded.Waves,
        keywords = listOf("frequence", "frequency", "tone", "son", "sound", "sinus", "sine", "carre", "square", "triangle", "scie", "saw", "hz", "signal", "test"),
        description = Txt("Sinus, carré, triangle, dent de scie de 20 Hz à 20 kHz", "Sine, square, triangle, sawtooth from 20 Hz to 20 kHz"),
    ) { ToneGeneratorScreen() },
    Tool(
        id = "sound-meter",
        name = Txt("Sonomètre", "Sound meter"),
        category = Category.INSTRUMENTS,
        icon = Icons.Rounded.NoiseAware,
        keywords = listOf("sonometre", "sound meter", "decibel", "db", "bruit", "noise", "volume", "niveau sonore", "sound level", "spl", "leq"),
        description = Txt("Niveau sonore en dB, min / moyenne / max", "Sound level in dB, min / average / max"),
        features = listOf(PackageManager.FEATURE_MICROPHONE),
        permissions = listOf(AppPermission.MICROPHONE),
    ) { SoundMeterScreen() },
)
