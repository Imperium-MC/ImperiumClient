/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.legions

import java.util.concurrent.CopyOnWriteArrayList

import com.google.gson.JsonArray
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import kotlin.math.max

/**
 * Owns the breadcrumb list and its persistence (Alpine "factions breadcrumbs" parity,
 * reimplemented for Legions).
 */
class BreadcrumbManager private constructor() {
    /** Auto-expire choices in minutes; OFF keeps crumbs until cleared manually. */
    enum class Expiry(@JvmField val maxAgeMs: Long) {
        OFF(0),
        MINUTES_5(5 * 60_000L),
        MINUTES_15(15 * 60_000L),
        MINUTES_60(60 * 60_000L);
    }

    /** The persisted blob; config-UI edits re-parse through the change listener. */
    private val data = StringOption("breadcrumbs_data", "[]") { loadFromOption() }

    val breadcrumbs: MutableList<Breadcrumb> = CopyOnWriteArrayList()

    fun getDataOption(): StringOption = data

    /** Re-parses the breadcrumb list from the persisted option value. */
    fun loadFromOption() {
        val parsed: MutableList<Breadcrumb> = ArrayList()
        try {
            val array = JsonParser.parseString(data.get() ?: "[]").asJsonArray
            array.forEach { element ->
                if (element.isJsonObject) {
                    parsed.add(Breadcrumb.fromJson(element.asJsonObject))
                }
            }
        } catch (e: IllegalStateException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to parse breadcrumbs, resetting list!", e)
        } catch (e: JsonParseException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to parse breadcrumbs, resetting list!", e)
        }
        breadcrumbs.clear()
        breadcrumbs.addAll(parsed)
    }

    fun save() {
        val array = JsonArray()
        breadcrumbs.forEach { crumb -> array.add(crumb.toJson()) }
        data.set(array.toString())
        AxolotlClientCommon.getInstance().saveConfig()
    }

    /** Drops a sequentially-named crumb at the given position and persists the list. */
    fun drop(x: Double, y: Double, z: Double, dimension: String): Breadcrumb {
        val crumb = Breadcrumb(nextName(), x, y, z, dimension, System.currentTimeMillis())
        breadcrumbs.add(crumb)
        save()
        return crumb
    }

    /** Names are the next free number, so relogging never restarts at 1. */
    private fun nextName(): String {
        var maxNum = 0
        for (crumb in breadcrumbs) {
            try {
                maxNum = max(maxNum, crumb.name.toInt())
            } catch (_: NumberFormatException) {
                // non-numeric names (future renames) don't participate in the sequence
            }
        }
        return (maxNum + 1).toString()
    }

    fun clearAll() {
        if (breadcrumbs.isEmpty()) {
            return
        }
        breadcrumbs.clear()
        save()
    }

    /** Unexpired crumbs of one dimension; pruning side effect. */
    fun getForDimension(dimension: String, maxAgeMs: Long): List<Breadcrumb> {
        val now = System.currentTimeMillis()
        breadcrumbs.removeIf { crumb -> crumb.isExpired(now, maxAgeMs) }
        val out: MutableList<Breadcrumb> = ArrayList()
        for (crumb in breadcrumbs) {
            if (crumb.dimension == dimension) {
                out.add(crumb)
            }
        }
        return out
    }

    companion object {
        private val instance = BreadcrumbManager()

        @JvmStatic
        fun getInstance(): BreadcrumbManager = instance
    }
}
