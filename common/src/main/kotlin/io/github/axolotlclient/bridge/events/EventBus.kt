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

package io.github.axolotlclient.bridge.events

import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

/**
 * Probably an overengineered event bus.
 */
class EventBus<T>(private val combiner: Function<Iterable<T>, T>) {
	private val events: MutableSet<T> = HashSet()
	private var cachedInvoker: T? = null

	fun invoker(): T {
		if (cachedInvoker == null) {
			// copy to arraylist for iteration performance
			cachedInvoker = combiner.apply(ArrayList(events))
		}

		return cachedInvoker!!
	}

	fun register(event: T): T {
		events.add(event)
		cachedInvoker = null
		return event
	}

	fun unregister(event: T): Boolean {
		val res = events.remove(event)
		cachedInvoker = null
		return res
	}

	companion object {
		@JvmStatic
		fun broadcast0(): EventBus<Runnable> =
			EventBus { input: Iterable<Runnable> -> Runnable { for (op in input) op.run() } }

		@JvmStatic
		fun <T> broadcast1(): EventBus<Consumer<T>> =
			EventBus { input: Iterable<Consumer<T>> -> Consumer { value -> for (op in input) op.accept(value) } }

		@JvmStatic
		fun <T, U> broadcast2(): EventBus<BiConsumer<T, U>> =
			EventBus { input: Iterable<BiConsumer<T, U>> -> BiConsumer { a, b -> for (op in input) op.accept(a, b) } }
	}
}
