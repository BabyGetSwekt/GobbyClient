package gobby.gui.click

import java.awt.Color

private const val RGB_LENGTH = 6
private const val RGBA_LENGTH = 8
private const val RADIX = 16
private const val BYTE_MASK = 0xFF
private const val OPAQUE = 255

object HexColor {

    const val MAX_LENGTH = RGBA_LENGTH

    fun sanitize(raw: String): String =
        raw.trim().removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(RGBA_LENGTH)

    fun isComplete(text: String): Boolean = text.length == RGB_LENGTH || text.length == RGBA_LENGTH

    fun parse(text: String): Color? {
        if (!isComplete(text)) return null
        val packed = text.toLongOrNull(RADIX)?.toInt() ?: return null
        return if (text.length == RGBA_LENGTH) {
            Color(packed byteAt 3, packed byteAt 2, packed byteAt 1, packed byteAt 0)
        } else {
            Color(packed byteAt 2, packed byteAt 1, packed byteAt 0, OPAQUE)
        }
    }

    fun format(color: Color): String = String.format("%02X%02X%02X%02X", color.red, color.green, color.blue, color.alpha)

    private infix fun Int.byteAt(index: Int) = (this shr (index * Byte.SIZE_BITS)) and BYTE_MASK
}
