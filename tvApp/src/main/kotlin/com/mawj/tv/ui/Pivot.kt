@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mawj.tv.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * The way Nuvio TV moves its lists: the focused item stays at one point (a third into the list)
 * and the list glides under it on a soft spring, instead of the focus running to the edge.
 */
object PivotScroll : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.95f, stiffness = 380f)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (containerSize <= 0f || size <= 0f) return 0f
        val target = containerSize * 0.3f
        val distance = (offset + size / 2f) - target
        // Already in place: don't nudge for a few pixels.
        if (offset >= 0f && offset + size <= containerSize && kotlin.math.abs(distance) < 12f) return 0f
        return distance
    }
}
