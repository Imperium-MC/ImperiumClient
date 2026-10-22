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

import java.net.URI
import java.util.ArrayList
import java.util.HashMap

@JvmRecord
data class Request(
    val route: Route,
    val path: List<String>?,
    val query: List<String>?,
    val bodyFields: Map<String, Any?>?,
    val rawBody: ByteArray?,
    val headers: Map<String, String>?,
    val requiresAuthentication: Boolean
) {
    fun resolve(): URI {
        return API.getInstance().getUrl(this)
    }

    enum class Route(val path: String, val requiresAuthentication: Boolean) {
        AUTHENTICATE("authenticate", false),
        USER("user", false),
        ACCOUNT("account", true),
        GATEWAY("gateway", true),
        CHANNELS("channels", true),
        CHANNELS_INVITES("channels/invites", true),
        CHANNEL("channel", true),
        ACCOUNT_SETTINGS("account/settings", true),
        ACCOUNT_ACTIVITY("account/activity", true),
        ACCOUNT_DATA("account/data", true),
        ACCOUNT_USERNAME("account/username", true),
        ACCOUNT_RELATIONS_FRIENDS("account/relations/friends", true),
        ACCOUNT_RELATIONS_BLOCKED("account/relations/blocked", true),
        ACCOUNT_RELATIONS_REQUESTS("account/relations/requests", true),
        REPORT("report", true),
        GLOBAL_DATA("global_data", false),
        IMAGE("image", true),
        HYPIXEL("hypixel", true);

        fun create(): Request {
            return builder().build()
        }

        fun builder(): RequestBuilder {
            return RequestBuilder(this)
        }
    }

    class RequestBuilder internal constructor(private val route: Route) {
        private var path: ArrayList<String>? = null
        private var query: ArrayList<String>? = null
        private var bodyFields: HashMap<String, Any?>? = null
        private var headers: HashMap<String, String>? = null
        private var requiresAuthentication: Boolean
        private var rawBody: ByteArray? = null

        init {
            this.requiresAuthentication = route.requiresAuthentication
        }

        fun path(parameter: String): RequestBuilder {
            val p = (path ?: ArrayList<String>().also { path = it })
            p.add(parameter)
            return this
        }

        fun query(key: String, value: String): RequestBuilder {
            val q = (query ?: ArrayList<String>().also { query = it })
            q.add("$key=$value")
            return this
        }

        fun query(key: String, value: Any): RequestBuilder {
            val q = (query ?: ArrayList<String>().also { query = it })
            q.add("$key=$value")
            return this
        }

        fun field(key: String, value: Any?): RequestBuilder {
            val f = (bodyFields ?: HashMap<String, Any?>().also { bodyFields = it })
            f[key] = value
            return this
        }

        fun rawBody(body: ByteArray?): RequestBuilder {
            this.rawBody = body
            return this
        }

        fun header(key: String, value: String): RequestBuilder {
            val h = (headers ?: HashMap<String, String>().also { headers = it })
            h[key] = value
            return this
        }

        fun requiresAuthentication(): RequestBuilder {
            requiresAuthentication = true
            return this
        }

        fun requiresAuthentication(requiresAuthentication: Boolean): RequestBuilder {
            this.requiresAuthentication = requiresAuthentication
            return this
        }

        fun build(): Request {
            if (rawBody != null && bodyFields != null) {
                throw IllegalArgumentException("Request cannot have json body and raw body at the same time!")
            }
            return Request(route, path, query, bodyFields, rawBody, headers, requiresAuthentication)
        }

        override fun toString(): String {
            return "Request.RequestBuilder(route=$route, path=${listToString(path)}, query=${listToString(query)}, bodyFields=${mapToString(bodyFields)})"
        }

        private fun mapToString(map: Map<*, *>?): String {
            val builder = StringBuilder("{")
            map?.forEach { (o, o2) ->
                builder.append(o).append(": ").append(o2).append(",\n")
            }
            if (builder.length >= 1 && map?.isNotEmpty() == true) {
                val length = builder.length
                builder.delete(length - 2, length - 1)
            }
            builder.append("}")
            return builder.toString()
        }

        private fun listToString(c: Collection<*>?): String {
            val builder = StringBuilder("[")
            c?.forEach { o -> builder.append(o).append(",\n") }
            builder.append("]")
            return builder.toString()
        }
    }
}
