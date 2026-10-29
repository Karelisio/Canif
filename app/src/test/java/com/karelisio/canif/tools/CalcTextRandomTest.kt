package com.karelisio.canif.tools

import com.karelisio.canif.tools.calc.Quantities
import com.karelisio.canif.tools.calc.computeLoan
import com.karelisio.canif.tools.calc.simulateGrowth
import com.karelisio.canif.tools.random.drawNumbers
import com.karelisio.canif.tools.random.makeTeams
import com.karelisio.canif.tools.text.CleanOptions
import com.karelisio.canif.tools.text.DiffOp
import com.karelisio.canif.tools.text.LineOp
import com.karelisio.canif.tools.text.cleanText
import com.karelisio.canif.tools.text.diff
import com.karelisio.canif.tools.text.processLines
import com.karelisio.canif.tools.text.textStats
import com.karelisio.canif.tools.text.titleCase
import com.karelisio.canif.tools.text.tokenizeWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.random.Random

class CalcTextRandomTest {
    private fun convert(q: String, from: String, to: String, v: Double): Double {
        val qty = Quantities.first { it.id == q }
        return qty.unit(to).fromBase(qty.unit(from).toBase(v))
    }

    @Test
    fun units() {
        assertEquals(212.0, convert("temperature", "c", "f", 100.0), 1e-9)
        assertEquals(-40.0, convert("temperature", "f", "c", -40.0), 1e-9)
        assertEquals(1.609344, convert("length", "mi", "km", 1.0), 1e-12)
        assertEquals(2.20462262, convert("mass", "kg", "lb", 1.0), 1e-8)
        assertEquals(3.0, convert("cooking", "tbsp", "tsp", 1.0), 1e-9)
        assertEquals(1024.0, convert("data", "MiB", "KiB", 1.0), 1e-9)
        assertEquals(120.0, convert("cooking", "cup", "flour", 1.0), 1e-9)
    }

    @Test
    fun loan() {
        val r = computeLoan(200_000.0, 3.5, 240)!!
        assertEquals(1159.92, r.monthly, 0.01)
        assertEquals(0.0, r.years.last().balance, 1e-6)
        assertEquals(r.total - 200_000.0, r.interest, 1e-6)
        assertEquals(500.0, computeLoan(6000.0, 0.0, 12)!!.monthly, 1e-9)
    }

    @Test
    fun compound() {
        val p = simulateGrowth(10_000.0, 0.0, 12, 4.0, 1, 15)
        assertEquals(10_000 * 1.04.pow(15), p.last().balance, 1e-6)
        val m = simulateGrowth(0.0, 100.0, 12, 0.0, 12, 2)
        assertEquals(2400.0, m.last().balance, 1e-9)
    }

    @Test
    fun diffReconstructsBothTexts() {
        val rnd = Random(42)
        repeat(200) {
            val a = List(rnd.nextInt(0, 60)) { "w${rnd.nextInt(8)}" }
            val b = a.toMutableList().apply {
                repeat(rnd.nextInt(0, 10)) { if (isNotEmpty() && rnd.nextBoolean()) removeAt(rnd.nextInt(size)) else add(rnd.nextInt(size + 1), "x${rnd.nextInt(5)}") }
            }
            val e = diff(a, b)
            assertEquals(a, e.filter { it.op != DiffOp.INS }.map { it.token })
            assertEquals(b, e.filter { it.op != DiffOp.DEL }.map { it.token })
        }
        // Large inputs take the Myers path.
        val big = List(5000) { "t${it % 97}" }
        val big2 = big.toMutableList().apply { removeAt(4900); add(10, "new") }
        val e = diff(big, big2)
        assertEquals(big2, e.filter { it.op != DiffOp.DEL }.map { it.token })
        assertEquals(2, e.count { it.op != DiffOp.EQ })
        assertEquals(listOf("Bonjour", " ", "le", " ", "monde", "!"), tokenizeWords("Bonjour le monde!"))
    }

    @Test
    fun text() {
        assertEquals("Le Seigneur des Anneaux", titleCase("le seigneur des anneaux", fr = true))
        assertEquals("L'Arbre de la Vie", titleCase("l'arbre de la vie", fr = true))
        assertEquals("The Lord of the Rings", titleCase("the lord of the rings", fr = false))
        val s = textStats("Bonjour le monde. Ça va ?\n\nOui !")
        assertEquals(6, s.words)
        assertEquals(3, s.sentences)
        assertEquals(2, s.paragraphs)
        val c = cleanText("a  b \n\n\n c\nc", CleanOptions(duplicates = true))
        assertEquals("a b\n\nc", c.text)
        assertEquals(listOf("file1", "file2", "file10"), processLines("file10\nfile2\nfile1", LineOp.NATURAL, true, true, 0))
    }

    @Test
    fun random() {
        val d = drawNumbers(1, 10, 10, unique = true)
        assertEquals((1L..10L).toList(), d.sorted())
        assertTrue(drawNumbers(-5, 5, 500, unique = false).all { it in -5..5 })
        val t = makeTeams(List(7) { "p$it" }, 3)
        assertEquals(listOf(3, 2, 2), t.map { it.size })
    }
}
