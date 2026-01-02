package app.luzzy.extensions

fun String.getExtensionFromMimeType(): String {
    return when (lowercase()) {
        "image/png" -> ".png"
        "image/apng" -> ".apng"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}

fun String.isImageMimeType(): Boolean {
    return lowercase().startsWith("image")
}

fun String.isGifMimeType(): Boolean {
    return lowercase().endsWith("gif")
}

fun String.isVideoMimeType(): Boolean {
    return lowercase().startsWith("video")
}

fun String.isVCardMimeType(): Boolean {
    val lowercase = lowercase()
    return lowercase.endsWith("x-vcard") || lowercase.endsWith("vcard")
}

fun String.isAudioMimeType(): Boolean {
    return lowercase().startsWith("audio")
}

fun String.isCalendarMimeType(): Boolean {
    return lowercase().endsWith("calendar")
}

fun String.isPdfMimeType(): Boolean {
    return lowercase().endsWith("pdf")
}

fun String.isZipMimeType(): Boolean {
    return lowercase().endsWith("zip")
}

fun String.isPlainTextMimeType(): Boolean {
    return lowercase() == "text/plain"
}

fun String.getListNumbersFromText(): List<String> {
    val numbers = Regex("(?=.*\\d)[\\d.,]+").findAll(this)
        .map(MatchResult::value)
        .toList()
    return numbers.filter { it.count() in 4..30 }
}

fun String.getOTPFromText(): String? {

    val allNumbers = Regex("""\d{4,8}""").findAll(this)
        .map { it.value }
        .filter { it.length in 4..8 }
        .toList()

    return allNumbers.firstOrNull { code ->
        val index = this.indexOf(code)
        index != -1 &&
            !hasAsteriskBefore(code, index) &&
            !isPartOfLargerNumber(code, index) &&
            !isPhoneNumberOrDate(code)
    }
}

private fun String.hasAsteriskBefore(code: String, index: Int): Boolean {
    return index > 0 && this[index - 1] == '*'
}

private fun String.isPartOfLargerNumber(code: String, index: Int): Boolean {
    val before = if (index > 0) this[index - 1] else null
    val after = if (index + code.length < this.length) this[index + code.length] else null
    return (before?.isDigit() == true) || (after?.isDigit() == true)
}

private fun isPhoneNumberOrDate(code: String): Boolean {

    return code.length == 4 && code.startsWith("19") ||
        code.length == 4 && code.startsWith("20") ||
        code.length == 8 && code.contains(Regex("""[/.-]"""))
}
