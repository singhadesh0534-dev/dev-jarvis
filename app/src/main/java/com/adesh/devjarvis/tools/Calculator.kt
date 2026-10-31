package com.adesh.devjarvis.tools

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Chhota safe calculator: + - * / % ^, brackets, decimals, sqrt(). Koi eval() nahi.
 * "x" aur "×" ko bhi multiply maanta hai, "÷" ko divide.
 */
object Calculator {

    class CalcError(message: String) : Exception(message)

    fun evaluate(expression: String): Double {
        val src = expression.lowercase()
            .replace("×", "*").replace("÷", "/").replace(",", "")
            .replace(Regex("""(?<=[\d)\s])x(?=[\d(\s])"""), "*")
        return Parser(src).parse()
    }

    /** 12.0 -> "12", 3.333333 -> "3.3333". */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) throw CalcError("undefined")
        val rounded = Math.round(value * 10_000) / 10_000.0
        return if (rounded == Math.floor(rounded) && kotlin.math.abs(rounded) < 1e15) rounded.toLong().toString() else rounded.toString()
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            skip()
            if (pos < s.length) throw CalcError("unexpected '${s[pos]}'")
            return v
        }

        private fun skip() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

        private fun eat(c: Char): Boolean {
            skip()
            if (pos < s.length && s[pos] == c) { pos++; return true }
            return false
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                v = when {
                    eat('*') -> v * power()
                    eat('/') -> { val d = power(); if (d == 0.0) throw CalcError("division by zero"); v / d }
                    eat('%') -> v % power()
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            return if (eat('^')) base.pow(power()) else base
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> atom()
        }

        private fun atom(): Double {
            skip()
            if (s.startsWith("sqrt", pos)) {
                pos += 4
                if (!eat('(')) throw CalcError("expected (")
                val v = expr()
                if (!eat(')')) throw CalcError("expected )")
                if (v < 0) throw CalcError("negative sqrt")
                return sqrt(v)
            }
            if (eat('(')) {
                val v = expr()
                if (!eat(')')) throw CalcError("expected )")
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw CalcError("expected number")
            return s.substring(start, pos).toDoubleOrNull() ?: throw CalcError("bad number")
        }
    }
}
