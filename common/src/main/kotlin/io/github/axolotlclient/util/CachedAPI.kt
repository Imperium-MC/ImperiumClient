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
package io.github.axolotlclient.util

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.util.NoSuchElementException
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Supplier

class CachedAPI<K : Any, V : Any>(apiHandler: APIHandler<K, V>, size: Int, private val retainFails: Boolean) {

    private val cache: LoadingCache<K, CompletableFuture<Optional<V>>> = CacheBuilder
        .newBuilder()
        .maximumSize(size.toLong())
        .build(object : CacheLoader<K, CompletableFuture<Optional<V>>>() {
            override fun load(key: K): CompletableFuture<Optional<V>> = apiHandler.makeRequest(key)
        })

    fun invalidate() {
        cache.invalidateAll()
    }

    fun invalidate(key: K) {
        cache.invalidate(key)
    }

    fun getAsync(value: K): CompletableFuture<Optional<V>> {
        if (retainFails) {
            return cache.getUnchecked(value)
        }
        val res = cache.getUnchecked(value)

        // this doesn't need to be perfect, as long as we don't keep a bad value in the cache for too long
        // therefore, we can completely ignore possible race conditions
        if (getAsyncNow(value).isError) {
            cache.invalidate(value)
        }

        return res
    }

    fun getAsyncNow(value: K): ApiResult<V> {
        val res = cache.getUnchecked(value)

        if (!res.isDone) {
            return ApiResult.pending()
        }

        val fallback: Optional<V> = Optional.empty()
        val present: Optional<V> = res.getNow(fallback)
        return if (present.isPresent) ApiResult.of(present.get()) else ApiResult.error()
    }

    class ApiResult<T : Any> private constructor(private val value: Any?) {

        fun get(): T {
            if (this === PENDING_INSTANCE || this === ERROR_INSTANCE) {
                throw NoSuchElementException("no value present")
            }

            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        val isPending: Boolean
            get() = this === PENDING_INSTANCE

        val isError: Boolean
            get() = this === ERROR_INSTANCE

        fun hasValue(): Boolean = !isPending && !isError

        fun <U> map(onPending: Supplier<U>, onError: Supplier<U>, mapper: Function<T, U>): U = when {
            isPending -> onPending.get()
            isError -> onError.get()
            else -> mapper.apply(get())
        }

        fun asOptional(): Optional<T> =
            map(Supplier { Optional.empty() }, Supplier { Optional.empty() }, Function { value: T -> Optional.of(value) })

        companion object {
            private val PENDING_INSTANCE = ApiResult<Any>(null)
            private val ERROR_INSTANCE = ApiResult<Any>(null)

            @JvmStatic
            @Suppress("UNCHECKED_CAST")
            fun <T : Any> pending(): ApiResult<T> = PENDING_INSTANCE as ApiResult<T>

            @JvmStatic
            @Suppress("UNCHECKED_CAST")
            fun <T : Any> error(): ApiResult<T> = ERROR_INSTANCE as ApiResult<T>

            @JvmStatic
            fun <T : Any> of(value: T): ApiResult<T> = ApiResult(value)
        }
    }

    fun interface APIHandler<K, V> {
        fun makeRequest(key: K): CompletableFuture<Optional<V>>
    }
}
