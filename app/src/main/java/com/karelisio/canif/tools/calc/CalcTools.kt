package com.karelisio.canif.tools.calc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SquareFoot
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val calcTools: List<Tool> = listOf(
    Tool(
        id = "percent",
        name = Txt("Pourcentages", "Percentages"),
        category = Category.CALC,
        icon = Icons.Rounded.Percent,
        keywords = listOf("pourcent", "percent", "remise", "discount", "soldes", "sale", "variation", "change", "hausse", "baisse", "prix barré"),
        description = Txt("Pourcentages, variations, remises et prix barrés", "Percentages, changes, discounts and original prices"),
    ) { PercentScreen() },
    Tool(
        id = "units",
        name = Txt("Unités", "Units"),
        category = Category.CALC,
        icon = Icons.Rounded.SquareFoot,
        keywords = listOf("convertisseur", "converter", "longueur", "length", "masse", "poids", "weight", "volume", "température", "temperature", "vitesse", "speed", "surface", "area", "octets", "bytes", "cuisine", "cooking", "tasse", "cup"),
        description = Txt("Convertir longueurs, masses, volumes, températures…", "Convert lengths, masses, volumes, temperatures…"),
    ) { UnitsScreen() },
    Tool(
        id = "currency",
        name = Txt("Devises", "Currencies"),
        category = Category.CALC,
        icon = Icons.Rounded.CurrencyExchange,
        keywords = listOf("devise", "currency", "change", "exchange", "euro", "dollar", "taux", "rate", "bce", "ecb"),
        description = Txt("Taux de la BCE, utilisables hors ligne", "ECB rates, usable offline"),
    ) { CurrencyScreen() },
    Tool(
        id = "dates",
        name = Txt("Dates", "Dates"),
        category = Category.CALC,
        icon = Icons.Rounded.CalendarMonth,
        keywords = listOf("date", "jours", "days", "écart", "difference", "âge", "age", "anniversaire", "birthday", "calendrier", "calendar"),
        description = Txt("Écart entre dates, ajout de jours, âge exact", "Days between dates, add days, exact age"),
    ) { DatesScreen() },
    Tool(
        id = "vat",
        name = Txt("TVA", "VAT"),
        category = Category.CALC,
        icon = Icons.Rounded.Receipt,
        keywords = listOf("tva", "vat", "tax", "taxe", "ht", "ttc", "hors taxe"),
        description = Txt("Hors taxe ↔ toutes taxes comprises", "Net ↔ gross amounts"),
    ) { VatScreen() },
    Tool(
        id = "loan",
        name = Txt("Prêt", "Loan"),
        category = Category.CALC,
        icon = Icons.Rounded.AccountBalance,
        keywords = listOf("prêt", "loan", "crédit", "credit", "emprunt", "mortgage", "immobilier", "mensualité", "monthly payment", "amortissement", "amortization"),
        description = Txt("Mensualités, coût total et amortissement", "Monthly payment, total cost and amortization"),
    ) { LoanScreen() },
    Tool(
        id = "compound",
        name = Txt("Intérêts composés", "Compound interest"),
        category = Category.CALC,
        icon = Icons.Rounded.Savings,
        keywords = listOf("intérêts", "interest", "épargne", "savings", "placement", "investment", "livret", "capitalisation"),
        description = Txt("Faire fructifier une épargne", "Grow your savings"),
    ) { CompoundScreen() },
    Tool(
        id = "tip",
        name = Txt("Addition et pourboire", "Bill & tip"),
        category = Category.CALC,
        icon = Icons.Rounded.Restaurant,
        keywords = listOf("pourboire", "tip", "addition", "bill", "restaurant", "partager", "split", "personnes"),
        description = Txt("Partager l'addition, pourboire compris", "Split the bill, tip included"),
    ) { TipScreen() },
)
