package gobby.gui.click

object TextWrap {

    fun scaledLineHeight(scale: Float, gap: Int = 1): Int = (tr.lineHeight * scale).toInt() + gap

    fun wrap(text: String, maxWidth: Int, scale: Float, maxLines: Int): List<String> {
        if (text.isEmpty() || maxWidth <= 0 || maxLines <= 0) return emptyList()
        val out = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            if (out.size >= maxLines) break
            wrapParagraph(paragraph, maxWidth, scale, maxLines - out.size, out)
        }
        return out
    }

    private fun wrapParagraph(text: String, maxWidth: Int, scale: Float, maxLines: Int, out: MutableList<String>) {
        if (maxLines <= 0) return
        if (text.isEmpty()) { out.add(""); return }
        if (textWScaled(text, scale) <= maxWidth) { out.add(text); return }
        var remaining = text
        var added = 0
        while (remaining.isNotEmpty() && added < maxLines) {
            val isLast = added == maxLines - 1
            val hardCut = maxFit(remaining, maxWidth, scale)
            val cut = if (!isLast && hardCut < remaining.length) {
                remaining.substring(0, hardCut).lastIndexOf(' ').takeIf { it > 0 } ?: hardCut
            } else hardCut
            val raw = remaining.substring(0, cut)
            val line = if (isLast && cut < remaining.length) truncateToFit(raw, maxWidth, scale) else raw
            out.add(line.trim())
            added++
            remaining = remaining.drop(cut).trimStart()
        }
    }

    private fun maxFit(text: String, maxWidth: Int, scale: Float): Int =
        (text.length downTo 1).firstOrNull { textWScaled(text.substring(0, it), scale) <= maxWidth } ?: 1

    fun truncateToFit(text: String, maxWidth: Int, scale: Float): String =
        if (textWScaled("$text…", scale) <= maxWidth) "$text…"
        else generateSequence(text) { it.dropLast(1).takeIf(String::isNotEmpty) }
            .first { textWScaled("$it…", scale) <= maxWidth } + "…"
}
