package com.captainduckay.dictator.core

import java.text.BreakIterator

/** Splits text into user-perceived characters (grapheme clusters), like Swift's `Character`. */
fun String.graphemes(): List<String> {
    if (isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    val result = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += substring(start, end)
        start = end
        end = iterator.next()
    }
    return result
}

/** The number of user-perceived characters, as Swift's `String.count` reports it. */
fun String.graphemeCount(): Int = graphemes().size
