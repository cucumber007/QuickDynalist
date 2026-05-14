package com.louiskirsch.quickdynalist.compat.anko.collections

inline fun <T> Iterable<T>.forEachWithIndex(action: (Int, T) -> Unit) {
    forEachIndexed(action)
}
