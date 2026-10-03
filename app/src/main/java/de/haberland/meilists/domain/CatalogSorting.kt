package de.haberland.meilists.domain

import java.text.Collator
import java.util.Locale

/** German alphabetical order, including umlauts, independent of the device language. */
fun <T> List<T>.sortedByCatalogName(name: (T) -> String): List<T> {
    val collator = Collator.getInstance(Locale.GERMAN).apply {
        strength = Collator.SECONDARY
        decomposition = Collator.CANONICAL_DECOMPOSITION
    }
    return sortedWith { first, second ->
        val firstName = name(first)
        val secondName = name(second)
        val comparison = collator.compare(firstName, secondName)
        if (comparison != 0) comparison else firstName.compareTo(secondName)
    }
}
