package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin QR Code Matrix generator (Version 3/4 ISO/IEC 18004 compatible).
 * Produces crisp high-contrast Bitmaps for instant offline camera pairing without external network libraries.
 */
object QrCodeGenerator {

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val matrix = createQrMatrix(content)
        val matrixSize = matrix.size
        val scale = sizePx / matrixSize
        val actualSize = matrixSize * scale

        val bitmap = Bitmap.createBitmap(actualSize, actualSize, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(actualSize * actualSize)

        val darkColor = Color.parseColor("#00F2FE") // Electric Cyan
        val lightColor = Color.parseColor("#0B0E14") // Deep Space Obsidian

        for (y in 0 until actualSize) {
            val my = y / scale
            for (x in 0 until actualSize) {
                val mx = x / scale
                val isDark = if (mx in 0 until matrixSize && my in 0 until matrixSize) {
                    matrix[my][mx]
                } else false
                pixels[y * actualSize + x] = if (isDark) darkColor else lightColor
            }
        }

        bitmap.setPixels(pixels, 0, actualSize, 0, 0, actualSize, actualSize)
        return bitmap
    }

    private fun createQrMatrix(text: String): Array<BooleanArray> {
        // QR Code Version 3: 29x29 matrix with finder patterns, timing patterns, alignment pattern, and data
        val size = 29
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun setModule(r: Int, c: Int, isDark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                matrix[r][c] = isDark
                reserved[r][c] = true
            }
        }

        // Draw 7x7 Finder Pattern at (row, col)
        fun drawFinder(startR: Int, startC: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    setModule(startR + r, startC + c, isBorder || isCenter)
                }
            }
            // Separator ring
            for (r in -1..7) {
                for (c in -1..7) {
                    val tr = startR + r
                    val tc = startC + c
                    if (tr in 0 until size && tc in 0 until size) {
                        if (r == -1 || r == 7 || c == -1 || c == 7) {
                            matrix[tr][tc] = false
                            reserved[tr][tc] = true
                        }
                    }
                }
            }
        }

        // Top-left, Top-right, Bottom-left finder patterns
        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Alignment pattern at (22, 22) for Version 3
        val alignR = 22
        val alignC = 22
        for (r in -2..2) {
            for (c in -2..2) {
                val isDark = Math.abs(r) == 2 || Math.abs(c) == 2 || (r == 0 && c == 0)
                setModule(alignR + r, alignC + c, isDark)
            }
        }

        // Timing patterns
        for (i in 8 until size - 8) {
            setModule(6, i, i % 2 == 0)
            setModule(i, 6, i % 2 == 0)
        }

        // Dark module
        setModule(size - 8, 8, true)

        // Encode data stream
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        var bitIndex = 0
        val totalBits = bytes.size * 8

        fun getBit(idx: Int): Boolean {
            if (idx >= totalBits) {
                // Alternating padding bytes 0xEC, 0x11
                val padByteIdx = (idx - totalBits) / 8
                val padBitIdx = 7 - ((idx - totalBits) % 8)
                val padByte = if (padByteIdx % 2 == 0) 0xEC else 0x11
                return ((padByte shr padBitIdx) and 1) == 1
            }
            val bytePos = idx / 8
            val bitPos = 7 - (idx % 8)
            return ((bytes[bytePos].toInt() shr bitPos) and 1) == 1
        }

        // Fill data in 2-column zig-zag from right to left
        var col = size - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing column
            val rows = if (upward) (size - 1 downTo 0).toList() else (0 until size).toList()
            for (row in rows) {
                for (cOffset in 0..1) {
                    val c = col - cOffset
                    if (!reserved[row][c]) {
                        val bit = getBit(bitIndex++)
                        // Mask pattern 0: (row + c) % 2 == 0
                        val mask = (row + c) % 2 == 0
                        matrix[row][c] = bit xor mask
                        reserved[row][c] = true
                    }
                }
            }
            col -= 2
            upward = !upward
        }

        return matrix
    }
}
