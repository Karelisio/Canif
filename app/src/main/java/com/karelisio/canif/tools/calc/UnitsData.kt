package com.karelisio.canif.tools.calc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.settings.UnitSystem

/** A unit: converts to and from the base unit of its quantity. */
internal class MeasureUnit(
    val id: String,
    val name: Txt,
    val symbol: Txt,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double,
    val approx: Boolean = false,
) {
    constructor(id: String, name: Txt, symbol: String, factor: Double, approx: Boolean = false) :
        this(id, name, Txt(symbol, symbol), { it * factor }, { it / factor }, approx)
    constructor(id: String, name: Txt, symbol: Txt, factor: Double, approx: Boolean = false) :
        this(id, name, symbol, { it * factor }, { it / factor }, approx)
}

internal class Quantity(
    val id: String,
    val name: Txt,
    val icon: ImageVector,
    val units: List<MeasureUnit>,
    /** Default (from, to) unit ids for metric and imperial users. */
    val metricPair: Pair<String, String>,
    val imperialPair: Pair<String, String>,
) {
    fun unit(id: String) = units.firstOrNull { it.id == id } ?: units.first()
    fun defaults(system: UnitSystem) = if (system == UnitSystem.IMPERIAL) imperialPair else metricPair
}

private fun t(fr: String, en: String) = Txt(fr, en)

// US customary cup used by most recipes.
private const val CUP_ML = 236.5882365

internal val Quantities: List<Quantity> = listOf(
    Quantity(
        "length", t("Longueur", "Length"), Icons.Rounded.Straighten,
        listOf(
            MeasureUnit("mm", t("Millimètre", "Millimeter"), "mm", 0.001),
            MeasureUnit("cm", t("Centimètre", "Centimeter"), "cm", 0.01),
            MeasureUnit("m", t("Mètre", "Meter"), "m", 1.0),
            MeasureUnit("km", t("Kilomètre", "Kilometer"), "km", 1000.0),
            MeasureUnit("in", t("Pouce", "Inch"), "in", 0.0254),
            MeasureUnit("ft", t("Pied", "Foot"), "ft", 0.3048),
            MeasureUnit("yd", t("Yard", "Yard"), "yd", 0.9144),
            MeasureUnit("mi", t("Mile", "Mile"), "mi", 1609.344),
            MeasureUnit("nmi", t("Mille marin", "Nautical mile"), t("M", "nmi"), 1852.0),
        ),
        "m" to "ft", "ft" to "m",
    ),
    Quantity(
        "mass", t("Masse", "Mass"), Icons.Rounded.Scale,
        listOf(
            MeasureUnit("mg", t("Milligramme", "Milligram"), "mg", 1e-6),
            MeasureUnit("g", t("Gramme", "Gram"), "g", 0.001),
            MeasureUnit("kg", t("Kilogramme", "Kilogram"), "kg", 1.0),
            MeasureUnit("t", t("Tonne", "Tonne"), "t", 1000.0),
            MeasureUnit("oz", t("Once", "Ounce"), "oz", 0.028349523125),
            MeasureUnit("lb", t("Livre", "Pound"), "lb", 0.45359237),
            MeasureUnit("st", t("Stone", "Stone"), "st", 6.35029318),
        ),
        "kg" to "lb", "lb" to "kg",
    ),
    Quantity(
        "volume", t("Volume", "Volume"), Icons.Rounded.WaterDrop,
        listOf(
            MeasureUnit("ml", t("Millilitre", "Milliliter"), "ml", 0.001),
            MeasureUnit("cl", t("Centilitre", "Centiliter"), "cl", 0.01),
            MeasureUnit("l", t("Litre", "Liter"), "l", 1.0),
            MeasureUnit("m3", t("Mètre cube", "Cubic meter"), "m³", 1000.0),
            MeasureUnit("floz", t("Once liquide (US)", "Fluid ounce (US)"), "fl oz", 0.0295735295625),
            MeasureUnit("pt", t("Pinte (US)", "Pint (US)"), "pt", 0.473176473),
            MeasureUnit("ptuk", t("Pinte (UK)", "Pint (UK)"), "pt UK", 0.56826125),
            MeasureUnit("gal", t("Gallon (US)", "Gallon (US)"), "gal", 3.785411784),
            MeasureUnit("galuk", t("Gallon (UK)", "Gallon (UK)"), "gal UK", 4.54609),
        ),
        "l" to "gal", "gal" to "l",
    ),
    Quantity(
        "temperature", t("Température", "Temperature"), Icons.Rounded.Thermostat,
        listOf(
            MeasureUnit("c", t("Degré Celsius", "Degree Celsius"), t("°C", "°C"), { it + 273.15 }, { it - 273.15 }),
            MeasureUnit("f", t("Degré Fahrenheit", "Degree Fahrenheit"), t("°F", "°F"), { (it - 32) * 5 / 9 + 273.15 }, { (it - 273.15) * 9 / 5 + 32 }),
            MeasureUnit("k", t("Kelvin", "Kelvin"), t("K", "K"), { it }, { it }),
            MeasureUnit("r", t("Degré Rankine", "Degree Rankine"), t("°R", "°R"), { it * 5 / 9 }, { it * 9 / 5 }),
        ),
        "c" to "f", "f" to "c",
    ),
    Quantity(
        "speed", t("Vitesse", "Speed"), Icons.Rounded.Speed,
        listOf(
            MeasureUnit("ms", t("Mètre par seconde", "Meter per second"), "m/s", 1.0),
            MeasureUnit("kmh", t("Kilomètre par heure", "Kilometer per hour"), "km/h", 1 / 3.6),
            MeasureUnit("mph", t("Mile par heure", "Mile per hour"), "mph", 0.44704),
            MeasureUnit("kn", t("Nœud", "Knot"), t("nd", "kn"), 1852.0 / 3600),
            MeasureUnit("fts", t("Pied par seconde", "Foot per second"), "ft/s", 0.3048),
            MeasureUnit("mach", t("Mach (niveau de la mer)", "Mach (sea level)"), "Ma", 340.29, approx = true),
        ),
        "kmh" to "mph", "mph" to "kmh",
    ),
    Quantity(
        "area", t("Surface", "Area"), Icons.Rounded.SquareFoot,
        listOf(
            MeasureUnit("cm2", t("Centimètre carré", "Square centimeter"), "cm²", 1e-4),
            MeasureUnit("m2", t("Mètre carré", "Square meter"), "m²", 1.0),
            MeasureUnit("a", t("Are", "Are"), "a", 100.0),
            MeasureUnit("ha", t("Hectare", "Hectare"), "ha", 10_000.0),
            MeasureUnit("km2", t("Kilomètre carré", "Square kilometer"), "km²", 1e6),
            MeasureUnit("in2", t("Pouce carré", "Square inch"), "in²", 0.00064516),
            MeasureUnit("ft2", t("Pied carré", "Square foot"), "ft²", 0.09290304),
            MeasureUnit("yd2", t("Yard carré", "Square yard"), "yd²", 0.83612736),
            MeasureUnit("ac", t("Acre", "Acre"), "ac", 4046.8564224),
            MeasureUnit("mi2", t("Mile carré", "Square mile"), "mi²", 2_589_988.110336),
        ),
        "m2" to "ft2", "ft2" to "m2",
    ),
    Quantity(
        "data", t("Données", "Data"), Icons.Rounded.Storage,
        listOf(
            MeasureUnit("bit", t("Bit", "Bit"), "bit", 0.125),
            MeasureUnit("B", t("Octet", "Byte"), t("o", "B"), 1.0),
            MeasureUnit("kB", t("Kilooctet", "Kilobyte"), t("Ko", "kB"), 1e3),
            MeasureUnit("MB", t("Mégaoctet", "Megabyte"), t("Mo", "MB"), 1e6),
            MeasureUnit("GB", t("Gigaoctet", "Gigabyte"), t("Go", "GB"), 1e9),
            MeasureUnit("TB", t("Téraoctet", "Terabyte"), t("To", "TB"), 1e12),
            MeasureUnit("KiB", t("Kibioctet", "Kibibyte"), t("Kio", "KiB"), 1024.0),
            MeasureUnit("MiB", t("Mébioctet", "Mebibyte"), t("Mio", "MiB"), 1024.0 * 1024),
            MeasureUnit("GiB", t("Gibioctet", "Gibibyte"), t("Gio", "GiB"), 1024.0 * 1024 * 1024),
            MeasureUnit("TiB", t("Tébioctet", "Tebibyte"), t("Tio", "TiB"), 1024.0 * 1024 * 1024 * 1024),
        ),
        "GB" to "GiB", "GB" to "GiB",
    ),
    Quantity(
        "cooking", t("Cuisine", "Cooking"), Icons.Rounded.Kitchen,
        listOf(
            MeasureUnit("ml", t("Millilitre", "Milliliter"), "ml", 1.0),
            MeasureUnit("cl", t("Centilitre", "Centiliter"), "cl", 10.0),
            MeasureUnit("l", t("Litre", "Liter"), "l", 1000.0),
            MeasureUnit("tsp", t("Cuillère à café", "Teaspoon"), t("c. à c.", "tsp"), CUP_ML / 48),
            MeasureUnit("tbsp", t("Cuillère à soupe", "Tablespoon"), t("c. à s.", "tbsp"), CUP_ML / 16),
            MeasureUnit("floz", t("Once liquide (US)", "Fluid ounce (US)"), "fl oz", CUP_ML / 8),
            MeasureUnit("cup", t("Tasse (US)", "Cup (US)"), t("tasse", "cup"), CUP_ML),
            MeasureUnit("cupm", t("Tasse métrique", "Metric cup"), t("tasse m.", "metric cup"), 250.0),
            MeasureUnit("water", t("Grammes d'eau", "Grams of water"), t("g d'eau", "g water"), 1.0),
            // Densities: 1 US cup ≈ 120 g flour, 200 g sugar, 227 g butter.
            MeasureUnit("flour", t("Grammes de farine", "Grams of flour"), t("g farine", "g flour"), CUP_ML / 120, approx = true),
            MeasureUnit("sugar", t("Grammes de sucre", "Grams of sugar"), t("g sucre", "g sugar"), CUP_ML / 200, approx = true),
            MeasureUnit("butter", t("Grammes de beurre", "Grams of butter"), t("g beurre", "g butter"), CUP_ML / 227, approx = true),
        ),
        "cup" to "ml", "ml" to "cup",
    ),
)
