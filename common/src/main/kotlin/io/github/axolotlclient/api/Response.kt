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
package io.github.axolotlclient.api

import java.io.IOException
import java.util.Objects
import java.util.Optional
import java.util.function.Function
import java.util.function.Supplier

import io.github.axolotlclient.util.GsonHelper

class Response private constructor(
    val body: Any?,
    val plainBody: String?,
    val status: Int,
    val headers: Map<String, List<String>>?,
    var error: Error?
) {
    constructor(body: String?, status: Int, headers: Map<String, List<String>>?) : this(
        try {
            parseJson(body!!)
        } catch (ignored: IOException) {
            null
        },
        body, status, headers, null
    ) {
        this.error = Error.of(this)
    }

    val isError: Boolean
        get() = error != null

    private fun mapToString(map: Map<*, *>): String {
        val builder = StringBuilder("$map{")
        map.forEach { (o, o2) -> builder.append(o).append(": ").append(o2).append("\n") }
        builder.append("}")
        return builder.toString()
    }

    private fun listToString(c: Collection<*>): String {
        val builder = StringBuilder("$c{")
        c.forEach { o -> builder.append(o).append("\n") }
        builder.append("}")
        return builder.toString()
    }

    private fun bodyToString(body: Any?): String {
        return if (body is Collection<*>) {
            listToString(body)
        } else if (body is Map<*, *>) {
            mapToString(body)
        } else {
            body.toString()
        }
    }

    override fun toString(): String {
        return "Response(headers=$headers, body=${bodyToString(body)}, plainBody=$plainBody, status=$status, error=$error)"
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getBody(path: String): T? {
        val p = path.replace("\\.", "_#+#_")
        val elements = p.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        var o = body
        for (s in elements) {
            val element = s.replace("_#+#_", ".")
            if (o is Map<*, *>) {
                if (o.containsKey(element)) {
                    o = o[element]
                    continue
                }
            } else if (o is List<*>) {
                try {
                    val i = element.toInt()
                    o = o[i]
                    continue
                } catch (ignored: Exception) {
                }
            }
            return null
        }
        return o as T?
    }

    fun <T> getBodyOrElse(path: String, other: T): T {
        val element = getBody<T>(path)
        return element ?: other
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getBodyOpt(path: String): Optional<T> {
        return Optional.ofNullable(getBody<T>(path)) as Optional<T>
    }

    fun <T, U> getBody(path: String, mapper: Function<T, U>): U? {
        val element = getBody<T>(path)
        return element?.let { mapper.apply(it) }
    }

    fun bodyHas(path: String): Boolean {
        return getBody<Any>(path) != null
    }

    fun <T> ifBodyHas(path: String, supplier: Supplier<T>): T? {
        val content = getBody<Any>(path)
        return if (content != null) {
            supplier.get()
        } else {
            null
        }
    }

    fun firstHeader(name: String): Optional<String> {
        return Optional.ofNullable(headers?.getOrDefault(name, null)).map { index -> index[0] }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Response) return false
        return body == other.body && plainBody == other.plainBody && status == other.status &&
            headers == other.headers && error == other.error
    }

    override fun hashCode(): Int {
        return Objects.hash(body, plainBody, status, headers, error)
    }

    @JvmRecord
    data class Error(val httpCode: Int, val description: String) {
        companion object {
            @JvmStatic
            fun of(response: Response): Error? {
                if (response.status in 200..299) {
                    return null
                }
                return Error(response.status, response.getBodyOrElse("description", "Unexpected internal error"))
            }
        }
    }

    class ResponseBuilder {
        private var body: String? = null
        private var status = 0
        private var headers: Map<String, List<String>>? = null

        fun body(body: String?): ResponseBuilder {
            this.body = body
            return this
        }

        fun status(status: Int): ResponseBuilder {
            this.status = status
            return this
        }

        fun headers(headers: Map<String, List<String>>?): ResponseBuilder {
            this.headers = headers
            return this
        }

        fun build(): Response {
            return Response(body, status, headers)
        }
    }

    companion object {
        @JvmField
        val CLIENT_ERROR: Response =
            Response(emptyMap<Any, Any>(), "", 0, emptyMap<String, List<String>>(), Error(0, "Client Request Error!"))

        @JvmStatic
        fun builder(): ResponseBuilder {
            return ResponseBuilder()
        }

        @Throws(IOException::class)
        private fun parseJson(json: String): Any? {
            if (json.isNotEmpty()) {
                return GsonHelper.read(json)
            }
            return emptyMap<String, Any>()
        }
    }
}
