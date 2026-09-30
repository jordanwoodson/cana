package io.github.samolego.canta.util

import java.util.TreeSet

/**
 * Matches many class names against Exodus code signatures quickly, with the semantics of
 * [TrackerSignatures.matches]: a prefix matches from the start of the name and a pattern that
 * begins with a dot matches anywhere. Prefixes are bucketed by their first two segments, so each
 * class name is compared with a handful of candidates instead of every signature.
 */
class TrackerMatcher(signatures: List<TrackerSignature>) {
    private class Candidate(val prefix: String, val tracker: String)

    private val buckets = HashMap<String, MutableList<Candidate>>()
    /** Prefixes with fewer than two complete segments; checked for every class name. */
    private val unbucketed = ArrayList<Candidate>()
    /** Patterns starting with a dot; matched with `contains`. */
    private val infix = ArrayList<Candidate>()

    init {
        for (signature in signatures) for (prefix in signature.prefixes) {
            val candidate = Candidate(prefix, signature.name)
            if (prefix.startsWith('.')) { infix.add(candidate); continue }
            val key = bucketKey(prefix)
            if (key == null) unbucketed.add(candidate) else buckets.getOrPut(key) { ArrayList() }.add(candidate)
        }
    }

    /** Distinct tracker names whose signature matches [className], sorted. */
    fun matches(className: String): List<String> {
        val found = TreeSet<String>()
        bucketKey(className)?.let { key -> buckets[key]?.forEach { if (className.startsWith(it.prefix)) found.add(it.tracker) } }
        unbucketed.forEach { if (className.startsWith(it.prefix)) found.add(it.tracker) }
        infix.forEach { if (className.contains(it.prefix)) found.add(it.tracker) }
        return found.toList()
    }

    /** Distinct tracker names across [classNames], sorted. */
    fun scan(classNames: Iterable<String>): List<String> {
        val found = TreeSet<String>()
        for (name in classNames) found.addAll(matches(name))
        return found.toList()
    }

    private companion object {
        /** "com.google.analytics.Foo" → "com.google"; null when [text] lacks two complete segments. */
        fun bucketKey(text: String): String? {
            val first = text.indexOf('.')
            if (first <= 0) return null
            val second = text.indexOf('.', first + 1)
            if (second <= first + 1) return null
            return text.substring(0, second)
        }
    }
}
