/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util

import com.google.common.base.Preconditions
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.item.AxoItem
import io.github.axolotlclient.bridge.item.AxoItemStack
import io.github.axolotlclient.bridge.item.AxoPlayerInventory
import java.util.Optional

object ItemUtil {
    @JvmStatic
    fun getTotal(inventory: AxoPlayerInventory, item: AxoItem): Int =
        inventory.`br$getItems`().stream()
            .filter { it.`br$getItem`() === item }
            .mapToInt { it.`br$getCount`() }
            .sum()

    @JvmStatic
    fun getTotal(inventory: AxoMinecraftClient, item: AxoItem): Int {
        val player = inventory.`br$getPlayer`()

        return player?.`br$getInventory`()?.let { getTotal(it, item) } ?: 0
    }

    @JvmStatic
    fun getItemFromItem(item: AxoItemStack, list: List<ItemStorage>): Optional<ItemStorage> {
        val compare = item.`br$copy`()
        compare.`br$setCount`(1)
        return Optional.ofNullable(list.firstOrNull { isEqual(it.stack, compare) })
    }

    private fun isEqual(stack: AxoItemStack?, compare: AxoItemStack?): Boolean =
        stack != null && compare != null && stack.`br$getItem`() === compare.`br$getItem`()

    @JvmStatic
    fun removeOld(list: List<TimedItemStorage>, time: Int): ArrayList<TimedItemStorage> =
        ArrayList<TimedItemStorage>().apply { list.forEach { if (it.getPassedTime() <= time) add(it) } }

    @JvmStatic
    fun getTimedItemFromItem(item: AxoItemStack, list: List<TimedItemStorage>): Optional<TimedItemStorage> {
        val compare = item.`br$copy`()
        compare.`br$setCount`(1)
        return Optional.ofNullable(list.firstOrNull { isEqual(it.stack, compare) })
    }

    @JvmStatic
    fun storageFromItem(items: List<AxoItemStack?>): List<ItemStorage> {
        val storage = ArrayList<ItemStorage>()
        for (item in items) {
            if (item == null || item.`br$isEmpty`()) {
                continue
            }
            val s = getItemFromItem(item, storage)
            if (s.isPresent) {
                s.get().incrementTimes(item.`br$getCount`())
            } else {
                storage.add(ItemStorage(item, item.`br$getCount`()))
            }
        }
        return storage
    }

    @JvmStatic
    fun untimedToTimed(list: List<ItemStorage>): List<TimedItemStorage> = list.map { it.timed() }

    @JvmStatic
    fun getItems(client: AxoMinecraftClient): MutableList<AxoItemStack> {
        Preconditions.checkArgument(client.`br$getPlayer`() != null)
        return ArrayList(client.`br$getPlayer`()!!.`br$getInventory`().`br$getItems`())
    }

    /**
     * Compares two ItemStorage Lists.
     * If list1.get(1) is 10, and list2 is 5, it will return 5.
     * Will return nothing if negative...
     *
     * @param list1 one to be based off of
     * @param list2 one to compare to
     * @return the item storage
     */
    @JvmStatic
    fun compare(list1: List<ItemStorage>, list2: List<ItemStorage>): List<ItemStorage> {
        val list = ArrayList<ItemStorage>()
        for (current in list1) {
            val other = getItemFromItem(current.stack, list2).orElse(null)
            if (other != null) {
                if (current.times - other.times <= 0) {
                    continue
                }
                list.add(ItemStorage(other.stack.`br$copy`(), current.times - other.times))
            } else {
                list.add(current.copy())
            }
        }
        return list
    }

    open class ItemStorage(stack: AxoItemStack, @JvmField var times: Int) {
        @JvmField
        val stack: AxoItemStack = stack.`br$copy`().apply { `br$setCount`(1) }

        open fun incrementTimes(num: Int) {
            times += num
        }

        open fun copy(): ItemStorage = ItemStorage(stack.`br$copy`(), times)

        open fun timed(): TimedItemStorage = TimedItemStorage(stack.`br$copy`(), times)
    }

    open class TimedItemStorage(stack: AxoItemStack, times: Int) : ItemStorage(stack, times) {
        @JvmField
        var start: Float = Platform.getMeasuringTimeMs().toFloat()

        fun getPassedTime(): Float = Platform.getMeasuringTimeMs() - start

        override fun incrementTimes(num: Int) {
            super.incrementTimes(num)
            refresh()
        }

        fun refresh() {
            start = Platform.getMeasuringTimeMs().toFloat()
        }
    }
}
