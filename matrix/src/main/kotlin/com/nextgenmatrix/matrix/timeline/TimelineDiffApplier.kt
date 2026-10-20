package com.nextgenmatrix.matrix.timeline

import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItem

internal object TimelineDiffApplier {
    fun apply(items: MutableList<TimelineItem>, diff: TimelineDiff) {
        when (diff) {
            is TimelineDiff.Reset -> {
                items.clear()
                items.addAll(diff.values)
            }
            is TimelineDiff.Append -> items.addAll(diff.values)
            is TimelineDiff.Insert -> items.add(diff.index.toInt(), diff.value)
            is TimelineDiff.Set -> items[diff.index.toInt()] = diff.value
            is TimelineDiff.Remove -> items.removeAt(diff.index.toInt())
            is TimelineDiff.PushBack -> items.add(diff.value)
            is TimelineDiff.PushFront -> items.add(0, diff.value)
            is TimelineDiff.PopBack -> if (items.isNotEmpty()) items.removeAt(items.lastIndex)
            is TimelineDiff.PopFront -> if (items.isNotEmpty()) items.removeAt(0)
            is TimelineDiff.Clear -> items.clear()
            is TimelineDiff.Truncate -> {
                val len = diff.length.toInt()
                while (items.size > len) items.removeAt(items.lastIndex)
            }
        }
    }
}
