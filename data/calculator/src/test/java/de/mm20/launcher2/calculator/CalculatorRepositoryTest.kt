package de.mm20.launcher2.calculator

import de.mm20.launcher2.preferences.search.CalculatorSearchSettings
import de.mm20.launcher2.search.data.Calculator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorRepositoryTest {

    private fun calculate(query: String, enabled: Boolean = true): Calculator? {
        val settings = mockk<CalculatorSearchSettings> {
            every { this@mockk.enabled } returns flowOf(enabled)
        }
        return runBlocking { CalculatorRepositoryImpl(settings).search(query).first() }
    }

    @Test
    fun calculatesExpressions() {
        assertEquals(7.0, calculate("1+2*3")!!.solution, 0.0)
        assertEquals(20.0, calculate("(2+3)*4")!!.solution, 0.0)
    }

    @Test
    fun convertsNumberLiterals() {
        assertEquals(31.0, calculate("0x1F")!!.solution, 0.0)
        assertEquals(5.0, calculate("0b101")!!.solution, 0.0)
        assertEquals(15.0, calculate("017")!!.solution, 0.0)
    }

    @Test
    fun ignoresOtherQueries() {
        assertNull(calculate("hello"))
        assertNull(calculate(" "))
    }

    @Test
    fun canBeDisabled() {
        assertNull(calculate("1+2*3", enabled = false))
    }
}
