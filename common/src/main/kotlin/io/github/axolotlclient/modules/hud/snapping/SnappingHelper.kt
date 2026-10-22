/*
 * Copyright © 2023 moehreag <moehreag@gmail.com> & Contributors
 *
 * This file is part of AxolotlClient.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.modules.hud.snapping

import java.util.HashMap
import java.util.Optional

import com.google.common.collect.Sets
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.api.util.BiContainer
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.render.AxoSprites
import io.github.axolotlclient.bridge.render.AxoWindow
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.component.HudEntry
import io.github.axolotlclient.modules.hud.gui.layout.SnapAnchorType
import io.github.axolotlclient.modules.hud.util.Rectangle
import io.github.axolotlclient.util.ClientColors

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class SnappingHelper(rects: List<Rectangle>, current: Rectangle) {
    private val distance = 4
    private val x: MutableSet<Int> = HashSet()
    private val y: MutableSet<Int> = HashSet()
    private val window: AxoWindow?

    var current: Rectangle = current

    init {
        rects.forEach { addRect(it) }
        this.window = AxoWindow.getWindow()
    }

    fun addRect(rect: Rectangle) {
        x.add(rect.x())
        x.add(rect.xEnd())
        y.add(rect.y())
        y.add(rect.yEnd())
    }

    fun renderSnaps(graphics: AxoRenderContext) {
        val curx = getRawXSnap()
        if (curx.isPresent) {
            val x = curx.get()
            graphics.`br$fillRect`(x, 0, 1, window!!.`br$getScaledHeight`().toInt(), LINE_COLOR)
            val isStart = x - distance <= current.x() && x + distance >= current.x()
            if ((isStart && this.x.contains(x + current.width())) || (!isStart && this.x.contains(current.x()))) {
                graphics.`br$fillRect`(
                    if (isStart) x + current.width() else current.x(), 0, 1,
                    window!!.`br$getScaledHeight`().toInt(), LINE_COLOR
                )
            }
        }
        val cury = getRawYSnap()
        if (cury.isPresent) {
            val y = cury.get()
            graphics.`br$fillRect`(0, y, window!!.`br$getScaledWidth`().toInt(), 1, LINE_COLOR)
            val isStart = y - distance <= current.y() && y + distance >= current.y()
            if ((isStart && this.y.contains(y + current.height())) || (!isStart && this.y.contains(current.y()))) {
                graphics.`br$fillRect`(
                    0, if (isStart) y + current.height() else current.y(),
                    window!!.`br$getScaledWidth`().toInt(), 1, LINE_COLOR
                )
            }
        }
        //renderAll();
    }

    fun renderHighlights(ctx: AxoRenderContext, current: HudEntry) {
        val entries = HudManagerCommon.getInstance()!!.getMoveableEntries()
        for (entry in entries) {
            if (entry === current) continue
            if (entry.dependsOnX(current).isPresent || entry.dependsOnY(current).isPresent) continue
            val touchingX = isTouchingX(current, entry)
            val touchingY = isTouchingY(current, entry)
            val xM = getRawXSnap()
            val yM = getRawYSnap()
            if ((touchingX && xM.isEmpty) || (touchingY && yM.isEmpty)) return
            if (touchingX || touchingY) {
                ctx.`br$fillRect`(entry.getTrueBounds(), ClientColors.SELECTOR_GREEN.withAlpha(100))
            }
        }
    }

    fun getRawXSnap(): Optional<Int> {
        var xSnap = getNearby(current.x, x, distance)
        if (xSnap.isPresent) {
            return xSnap
        } else {
            xSnap = getNearby(current.x + current.width, x, distance)
            if (xSnap.isPresent) {
                return xSnap
            } else {
                xSnap = getHalfXSnap()
                if (xSnap.isPresent) {
                    return xSnap
                }
            }
        }
        return Optional.empty()
    }

    fun getRawYSnap(): Optional<Int> {
        var ySnap = getNearby(current.y, y, distance)
        if (ySnap.isPresent) {
            return ySnap
        } else {
            ySnap = getNearby(current.y + current.height, y, distance)
            if (ySnap.isPresent) {
                return ySnap
            } else {
                ySnap = getHalfYSnap()
                if (ySnap.isPresent) {
                    return ySnap
                }
            }
        }
        return Optional.empty()
    }

    fun getHalfXSnap(): Optional<Int> {
        val width = (window!!.`br$getScaledWidth`() / 2).toInt()
        val pos = current.x + Math.round(current.width / 2f)
        if (width - distance <= pos && width + distance >= pos) {
            return Optional.of(width)
        }
        return Optional.empty()
    }

    fun getHalfYSnap(): Optional<Int> {
        val height = (window!!.`br$getScaledHeight`() / 2).toInt()
        val pos = current.y + Math.round(current.height / 2f)
        if (height - distance <= pos && height + distance >= pos) {
            return Optional.of(height)
        }
        return Optional.empty()
    }

    @Suppress("unused")
    fun renderAll(graphics: AxoRenderContext) {
        for (xval in x) {
            graphics.`br$fillRect`(
                Rectangle(xval, 0, 1, window!!.`br$getScaledHeight`().toInt()), ClientColors.WHITE
            )
        }
        for (yval in y) {
            graphics.`br$fillRect`(
                Rectangle(0, yval, window!!.`br$getScaledWidth`().toInt(), 1), ClientColors.WHITE
            )
        }
    }

    fun getCurrentXSnap(): Optional<Int> {
        var xSnap = getNearby(current.x, x, distance)
        if (xSnap.isPresent) {
            return xSnap
        } else {
            xSnap = getNearby(current.x + current.width, x, distance)
            if (xSnap.isPresent) {
                return xSnap.map { i -> i - current.width() }
            } else {
                xSnap = getHalfXSnap()
                if (xSnap.isPresent) {
                    return xSnap.map { i -> i - (current.width / 2) }
                }
            }
        }
        return Optional.empty()
    }

    fun getCurrentYSnap(): Optional<Int> {
        var ySnap = getNearby(current.y, y, distance)
        if (ySnap.isPresent) {
            return ySnap
        } else {
            ySnap = getNearby(current.y + current.height, y, distance)
            if (ySnap.isPresent) {
                return ySnap.map { i -> i - current.height }
            } else {
                ySnap = getHalfYSnap()
                if (ySnap.isPresent) {
                    return ySnap.map { i -> i - (current.height / 2) }
                }
            }
        }
        return Optional.empty()
    }

    fun getXTouching(entries: Collection<HudEntry>, current: HudEntry): Collection<BiContainer<HudEntry, SnapAnchorType>> {
        val cur = current.getTrueBounds()
        return entries.asSequence()
            .filter { e -> e !== current && isTouchingX(current, e) }
            .mapNotNull { e ->
                val bounds = e.getTrueBounds()
                val type = when {
                    cur.x() == bounds.x() -> SnapAnchorType.X_X
                    cur.x() == bounds.xEnd() -> SnapAnchorType.X_XEND
                    cur.xEnd() == bounds.x() -> SnapAnchorType.XEND_X
                    cur.xEnd() == bounds.xEnd() -> SnapAnchorType.XEND_XEND
                    else -> return@mapNotNull null
                }
                BiContainer.of(e, type)
            }
            .toList()
    }

    fun getYTouching(entries: Collection<HudEntry>, current: HudEntry): Collection<BiContainer<HudEntry, SnapAnchorType>> {
        val cur = current.getTrueBounds()
        return entries.asSequence()
            .filter { e -> e !== current && isTouchingY(current, e) }
            .mapNotNull { e ->
                val bounds = e.getTrueBounds()
                val type = when {
                    cur.y() == bounds.y() -> SnapAnchorType.Y_Y
                    cur.y() == bounds.yEnd() -> SnapAnchorType.Y_YEND
                    cur.yEnd() == bounds.y() -> SnapAnchorType.YEND_Y
                    cur.yEnd() == bounds.yEnd() -> SnapAnchorType.YEND_YEND
                    else -> return@mapNotNull null
                }
                BiContainer.of(e, type)
            }
            .toList()
    }

    companion object {
        private val LINE_COLOR: Color = ClientColors.SELECTOR_BLUE

        @JvmStatic
        fun getNearby(pos: Int, set: Set<Int>, distance: Int): Optional<Int> {
            for (integer in set) {
                if (integer - distance <= pos && integer + distance >= pos) {
                    return Optional.of(integer)
                }
            }
            return Optional.empty()
        }

        @JvmStatic
        fun renderLinks(graphics: AxoRenderContext, current: HudEntry, lineWidth: Float) {
            val entriesX = HashMap(current.getDependenciesX())
            val entriesY = HashMap(current.getDependenciesY())
            val xEmpty = entriesX.isEmpty()
            val yEmpty = entriesY.isEmpty()
            val cBounds = current.getTrueBounds()
            if (!xEmpty && !yEmpty) {
                val union = Sets.intersection(entriesX.keys, entriesY.keys).immutableCopy()
                for (entry in union) {
                    val xType = entriesX[entry]!!
                    val yType = entriesY[entry]!!
                    val cXMatch = when (xType) {
                        SnapAnchorType.X_X, SnapAnchorType.X_XEND -> cBounds.x()
                        SnapAnchorType.XEND_X, SnapAnchorType.XEND_XEND -> cBounds.xEnd()
                        else -> throw IllegalArgumentException()
                    }
                    val cYMatch = when (yType) {
                        SnapAnchorType.Y_Y, SnapAnchorType.Y_YEND -> cBounds.y()
                        SnapAnchorType.YEND_Y, SnapAnchorType.YEND_YEND -> cBounds.yEnd()
                        else -> throw IllegalArgumentException()
                    }
                    val eBounds = entry.getTrueBounds()
                    val eXMatch = when (xType) {
                        SnapAnchorType.X_X, SnapAnchorType.XEND_X -> eBounds.x()
                        SnapAnchorType.X_XEND, SnapAnchorType.XEND_XEND -> eBounds.xEnd()
                        else -> throw IllegalArgumentException()
                    }
                    val eYMatch = when (yType) {
                        SnapAnchorType.Y_Y, SnapAnchorType.YEND_Y -> eBounds.y()
                        SnapAnchorType.Y_YEND, SnapAnchorType.YEND_YEND -> eBounds.yEnd()
                        else -> throw IllegalArgumentException()
                    }
                    graphics.`br$pushMatrix`()
                    graphics.`br$translateMatrix`(cXMatch.toFloat(), cYMatch.toFloat())
                    if (Math.abs(cXMatch - eXMatch) > 2 || Math.abs(cYMatch - eYMatch) > 2) {
                        val c1 = ClientColors.SELECTOR_GREEN.toInt()
                        val c2 = ClientColors.SELECTOR_RED.toInt()
                        graphics.`br$fillSegment`(0, 0, eXMatch - cXMatch, eYMatch - cYMatch, c1, c2, c2, c2, lineWidth)
                    }
                    var ang = 0
                    if (cXMatch == cBounds.x() && cYMatch == cBounds.y()) {
                        ang = -45
                    } else if (cXMatch == cBounds.x() && cYMatch == cBounds.yEnd()) {
                        ang = 45 + 180
                    } else if (cXMatch == cBounds.xEnd() && cYMatch == cBounds.y()) {
                        ang = 45
                    } else if (cXMatch == cBounds.xEnd() && cYMatch == cBounds.yEnd()) {
                        ang = 180 - 45
                    }
                    graphics.`br$translateMatrix`(-4.5f, -4.5f)
                    graphics.`br$rotateMatrixAround`(Math.toRadians(ang.toDouble()).toFloat(), 4.5f, 4.5f)
                    graphics.`br$drawTexture`(AxoSprites.MAGNET_ICON, 0, 0, 9, 9)
                    graphics.`br$popMatrix`()
                }
                union.forEach { e ->
                    entriesX.remove(e)
                    entriesY.remove(e)
                }
            }
            if (!xEmpty) {
                for (entry in entriesX.entries) {
                    val xBounds = entry.key.getTrueBounds()
                    val xType = entry.value
                    val x1 = when (xType) {
                        SnapAnchorType.X_X, SnapAnchorType.X_XEND -> cBounds.x()
                        SnapAnchorType.XEND_X, SnapAnchorType.XEND_XEND -> cBounds.xEnd()
                        else -> throw IllegalArgumentException()
                    }
                    val x2 = when (xType) {
                        SnapAnchorType.X_X, SnapAnchorType.XEND_X -> xBounds.x()
                        SnapAnchorType.X_XEND, SnapAnchorType.XEND_XEND -> xBounds.xEnd()
                        else -> throw IllegalArgumentException()
                    }
                    val y1 = if (xBounds.y() > cBounds.y()) cBounds.yEnd() else cBounds.y()
                    val y2 = if (xBounds.y() > cBounds.y()) xBounds.y() else xBounds.yEnd()
                    graphics.`br$pushMatrix`()
                    val overlap = minOf(cBounds.yEnd(), xBounds.yEnd()) - maxOf(cBounds.y(), xBounds.y())
                    if (overlap <= 0 /*&& Math.abs(y2 - y1) > 2*/) {
                        graphics.`br$translateMatrix`(x1.toFloat(), y1.toFloat())
                        val x = x2 - x1
                        val y = y2 - y1
                        val c1 = ClientColors.SELECTOR_GREEN.toInt()
                        val c2 = ClientColors.SELECTOR_RED.toInt()
                        graphics.`br$fillSegment`(0, 0, x, y, c1, c2, c2, c1, lineWidth)
                        val ang = Math.atan2(x.toDouble(), y.toDouble()).toFloat()
                        val touchLen = Math.sqrt((x * x + y * y).toDouble()).toInt()
                        graphics.`br$rotateMatrix`(-ang)
                        graphics.`br$translateMatrix`(0f, touchLen / 2f)
                        graphics.`br$rotateMatrix`(ang)
                    } else {
                        graphics.`br$translateMatrix`(x1.toFloat(), maxOf(cBounds.y(), xBounds.y()).toFloat())
                        graphics.`br$translateMatrix`(0f, overlap / 2f)
                    }
                    graphics.`br$translateMatrix`(-4.5f, -4.5f)
                    var ang = -90
                    if (x1 == cBounds.xEnd()) {
                        ang += 180
                    }
                    graphics.`br$rotateMatrixAround`(Math.toRadians(ang.toDouble()).toFloat(), 4.5f, 4.5f)
                    graphics.`br$drawTexture`(AxoSprites.MAGNET_ICON, 0, 0, 9, 9)
                    graphics.`br$popMatrix`()
                }
            }
            if (!yEmpty) {
                for (entry in entriesY.entries) {
                    val yBounds = entry.key.getTrueBounds()
                    val yType = entry.value
                    val y1 = when (yType) {
                        SnapAnchorType.Y_Y, SnapAnchorType.Y_YEND -> cBounds.y()
                        SnapAnchorType.YEND_Y, SnapAnchorType.YEND_YEND -> cBounds.yEnd()
                        else -> throw IllegalArgumentException()
                    }
                    val y2 = when (yType) {
                        SnapAnchorType.Y_Y, SnapAnchorType.YEND_Y -> yBounds.y()
                        SnapAnchorType.Y_YEND, SnapAnchorType.YEND_YEND -> yBounds.yEnd()
                        else -> throw IllegalArgumentException()
                    }
                    val x1 = if (yBounds.x() > cBounds.x()) cBounds.xEnd() else cBounds.x()
                    val x2 = if (yBounds.x() > cBounds.x()) yBounds.x() else yBounds.xEnd()
                    graphics.`br$pushMatrix`()
                    val overlap = minOf(cBounds.xEnd(), yBounds.xEnd()) - maxOf(cBounds.x(), yBounds.x())
                    if (overlap <= 0 /*|| Math.abs(x2 - x1) > 2*/) {
                        graphics.`br$translateMatrix`(x1.toFloat(), y1.toFloat())
                        val x = x2 - x1
                        val y = y2 - y1
                        val c1 = ClientColors.SELECTOR_GREEN.toInt()
                        val c2 = ClientColors.SELECTOR_RED.toInt()
                        graphics.`br$fillSegment`(0, 0, x, y, c1, c1, c2, c2, lineWidth)
                        val ang = Math.atan2(x.toDouble(), y.toDouble()).toFloat()
                        val touchLen = Math.sqrt((x * x + y * y).toDouble()).toInt()
                        graphics.`br$rotateMatrix`(-ang)
                        graphics.`br$translateMatrix`(0f, touchLen / 2f)
                        graphics.`br$rotateMatrix`(ang)
                    } else {
                        graphics.`br$translateMatrix`(maxOf(cBounds.x(), yBounds.x()).toFloat(), y1.toFloat())
                        graphics.`br$translateMatrix`(overlap / 2f, 0f)
                    }
                    graphics.`br$translateMatrix`(-4.5f, -4.5f)
                    if (y1 == cBounds.yEnd()) {
                        graphics.`br$rotateMatrixAround`(Math.PI.toFloat(), 4.5f, 4.5f)
                    }
                    graphics.`br$drawTexture`(AxoSprites.MAGNET_ICON, 0, 0, 9, 9)
                    graphics.`br$popMatrix`()
                }
            }
        }

        private fun isTouchingY(current: HudEntry, e: HudEntry?): Boolean {
            val cur = current.getTrueBounds()
            if (e === current || e == null) return false
            val bounds = e.getTrueBounds()
            return containsSide(cur.x(), cur.xEnd(), bounds.x(), bounds.xEnd()) &&
                (bounds.y() == cur.y() || bounds.yEnd() == cur.y() || bounds.y() == cur.yEnd() || bounds.yEnd() == cur.yEnd())
        }

        private fun isTouchingX(current: HudEntry, e: HudEntry?): Boolean {
            val cur = current.getTrueBounds()
            if (e === current || e == null) return false
            val bounds = e.getTrueBounds()
            return containsSide(cur.y(), cur.yEnd(), bounds.y(), bounds.yEnd()) &&
                (bounds.x() == cur.x() || bounds.xEnd() == cur.x() || bounds.x() == cur.xEnd() || bounds.xEnd() == cur.xEnd())
        }

        private fun containsSide(min1: Int, max1: Int, min2: Int, max2: Int): Boolean =
            (min1 >= min2 && min1 <= max2) || (max1 >= min2 && max1 <= max2) || (min2 >= min1 && max2 <= max1)

        private fun iterateHudDependencyTree(
            entry: HudEntry,
            set: MutableSet<HudEntry>,
            dependencies: Map<HudEntry, Set<HudEntry>>
        ) {
            set.add(entry)
            if (dependencies.containsKey(entry)) {
                dependencies[entry]!!.stream().filter { e -> !set.contains(e) }
                    .forEach { e -> iterateHudDependencyTree(e, set, dependencies) }
            }
        }

        @JvmStatic
        fun getNonDependentEntries(current: HudEntry, entries: Collection<HudEntry>): Collection<HudEntry> {
            val reverseDependencies = HashMap<HudEntry, MutableSet<HudEntry>>()
            for (e in entries) {
                Sets.union(e.getDependenciesX().keys, e.getDependenciesY().keys)
                    .forEach { d ->
                        reverseDependencies.computeIfAbsent(d) { HashSet() }.add(e)
                    }
            }
            if (!reverseDependencies.containsKey(current)) {
                return entries
            }

            val depTree = HashSet<HudEntry>()
            reverseDependencies[current]!!.forEach { e -> iterateHudDependencyTree(e, depTree, reverseDependencies) }
            val ret = ArrayList(entries)
            ret.removeAll(depTree)
            return ret
        }
    }
}
