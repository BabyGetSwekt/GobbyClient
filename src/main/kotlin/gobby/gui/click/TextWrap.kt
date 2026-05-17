package gobby.gui.click

object TextWrap {

    fun scaledLineHeight(scale: Float, gap: Int = 1): Int = (tr.lineHeight * scale).toInt() + gap

    fun wrap(text: String, maxWidth: Int, scale: Float, maxLines: Int): List<String> {
        if (text.isEmpty() || maxWidth <= 0 || maxLines <= 0) return emptyList()
        if (textWScaled(text, scale) <= maxWidth) return listOf(text)
        val out = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty() && out.size < maxLines) {
            val isLast = out.size == maxLines - 1
            val hardCut = maxFit(remaining, maxWidth, scale)
            val cut = if (!isLast && hardCut < remaining.length) {
                remaining.substring(0, hardCut).lastIndexOf(' ').takeIf { it > 0 } ?: hardCut
            } else hardCut
            val raw = remaining.substring(0, cut)
            val line = if (isLast && cut < remaining.length) truncateToFit(raw, maxWidth, scale) else raw
            out.add(line.trim())
            remaining = remaining.drop(cut).trimStart()
        }
        return out
    }

    private fun maxFit(text: String, maxWidth: Int, scale: Float): Int =
        (text.length downTo 1).firstOrNull { textWScaled(text.substring(0, it), scale) <= maxWidth } ?: 1

    private fun truncateToFit(text: String, maxWidth: Int, scale: Float): String =
        if (textWScaled("$text…", scale) <= maxWidth) "$text…"
        else generateSequence(text) { it.dropLast(1).takeIf(String::isNotEmpty) }
            .first { textWScaled("$it…", scale) <= maxWidth } + "…"
}
