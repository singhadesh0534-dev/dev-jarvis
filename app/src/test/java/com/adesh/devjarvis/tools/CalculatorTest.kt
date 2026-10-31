package com.adesh.devjarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalculatorTest {
    private fun calc(s: String) = Calculator.format(Calculator.evaluate(s))

    @Test fun basics() {
        assertEquals("14", calc("2 + 3 * 4"))
        assertEquals("20", calc("(2 + 3) * 4"))
        assertEquals("2.5", calc("5 / 2"))
        assertEquals("-3", calc("-5 + 2"))
        assertEquals("1024", calc("2^10"))
        assertEquals("1", calc("10 % 3"))
        assertEquals("12", calc("sqrt(144)"))
        assertEquals("3.3333", calc("10/3"))
    }

    @Test fun humanSymbols() {
        assertEquals("56", calc("7 x 8"))
        assertEquals("56", calc("7×8"))
        assertEquals("4", calc("8÷2"))
        assertEquals("1500", calc("1,000 + 500"))
    }

    @Test fun errors() {
        assertThrows(Calculator.CalcError::class.java) { Calculator.evaluate("1/0") }
        assertThrows(Calculator.CalcError::class.java) { Calculator.evaluate("2 +") }
        assertThrows(Calculator.CalcError::class.java) { Calculator.evaluate("rm -rf") }
    }
}
