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
package io.github.axolotlclient.api.types

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Locale
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.axolotlclient.api.API
import io.github.axolotlclient.util.GsonHelper
import io.github.axolotlclient.util.NetworkUtil
import io.github.axolotlclient.util.ThreadExecuter

class PkSystem(
    val id: String?,
    val name: String?,
    val members: List<Member>?,
    val fronters: List<Member>?,
    val firstFronter: Member?,
    val tag: String?,
    val latchTimeout: Int,
    val avatarUrl: String?
) {
    private var lastLatchProxy: Member? = null
    private var lastLatchProxyTime: Long = 0
    private var autoproxyMember: Member? = null

    init {
        updateAutoproxyMember(API.getInstance().apiOptions.autoproxyMember.get())
    }

    fun updateAutoproxyMember(value: String) {
        autoproxyMember = members!!.stream()
            .filter { m: Member -> value.lowercase(Locale.ROOT) == m.displayName || value.lowercase(Locale.ROOT) == m.id }
            .findFirst().orElse(null)
    }

    fun getProxy(message: String?): Optional<String> {
        return getProxyMember(message)
            .map { obj: Member -> decorateMemberName(obj) }
    }

    private fun getProxyMember(message: String?): Optional<Member> {
        if (!API.getInstance().apiOptions.autoproxy.get()) {
            return Optional.empty()
        }
        val proxy = members!!.stream()
            .filter { m: Member -> m.proxyTags.stream().anyMatch { p: Pattern -> p.matcher(message!!).matches() } }
            .findFirst()
        return when (API.getInstance().apiOptions.autoproxyMode.get()) {
            ProxyMode.PROXY_FRONT -> Optional.ofNullable(proxy.orElse(firstFronter))
            ProxyMode.PROXY_LATCH -> {
                if ((lastLatchProxyTime - System.currentTimeMillis()) / 1000 > latchTimeout) {
                    lastLatchProxy = null
                    return proxy
                }
                lastLatchProxyTime = System.currentTimeMillis()
                proxy.ifPresent { member: Member -> lastLatchProxy = member }
                if (lastLatchProxy != null) {
                    return Optional.of(lastLatchProxy!!)
                }
                proxy
            }
            ProxyMode.PROXY_MEMBER -> Optional.ofNullable(proxy.orElse(autoproxyMember))
            ProxyMode.PROXY_OFF -> Optional.empty()
        }
    }

    fun decorateMemberName(member: Member): String {
        var name = member.displayName
        if (name.isEmpty()) {
            name = member.name
        }
        return name + tag
    }

    fun getProxyAvatarUrl(member: Member): Optional<String> {
        val urls = arrayOf(member.webhookAvatarUrl, member.avatarUrl, avatarUrl)
        for (url in urls) {
            if (!url!!.isEmpty()) {
                return Optional.of(url)
            }
        }
        return Optional.empty()
    }

    class Member(
        val id: String,
        val name: String,
        val displayName: String,
        val proxyTags: List<Pattern>,
        val autoProxy: Boolean,
        val avatarUrl: String,
        val webhookAvatarUrl: String
    ) {
        companion object {
            @JvmStatic
            fun fromId(id: String): Member {
                return fromObject(queryPkAPI("members/$id").thenApply { obj: JsonElement -> obj.asJsonObject }.join())
            }

            @JvmStatic
            fun fromObject(`object`: JsonObject): Member {
                val id = getString(`object`, "id")
                val name = getString(`object`, "name")
                val tags = `object`.get("proxy_tags").asJsonArray
                val proxyTags = ArrayList<Pattern>()
                tags.forEach { e: JsonElement ->
                    if (e.isJsonObject) {
                        val o = e.asJsonObject
                        val prefix = getString(o, "prefix")
                        val suffix = getString(o, "suffix")
                        proxyTags.add(Pattern.compile(Pattern.quote(prefix) + ".*" + Pattern.quote(suffix)))
                    }
                }
                val autoProxy = `object`.has("autoproxy_enabled") && `object`.get("autoproxy_enabled").asBoolean
                return Member(
                    id, name, getString(`object`, "display_name"), proxyTags, autoProxy,
                    getString(`object`, "avatar_url"), getString(`object`, "webhook_avatar_url")
                )
            }
        }
    }

    enum class ProxyMode {
        PROXY_OFF,
        PROXY_FRONT,
        PROXY_LATCH,
        PROXY_MEMBER;

        companion object {
            @JvmStatic
            fun ofMode(mode: String): ProxyMode {
                return valueOf(mode.uppercase(Locale.ROOT))
            }
        }
    }

    companion object {
        // internal (not private) so the file-private PluralKitApi below can attach it to requests
        internal var token: String? = null

        private fun log(message: String) {
            API.getInstance().logger.debug("[PluralKit] $message")
        }

        @JvmStatic
        fun queryPkAPI(route: String): CompletableFuture<JsonElement> {
            return PluralKitApi.request("https://api.pluralkit.me/v2/" + route)
        }

        @JvmStatic
        fun fromToken(token: String?): CompletableFuture<PkSystem?> {
            if (token!!.length != 64) {
                return CompletableFuture.completedFuture(null)
            }
            Companion.token = token
            return queryPkAPI("systems/@me").thenApply { obj: JsonElement -> obj.asJsonObject }
                .thenCompose<PkSystem?> { `object`: JsonObject ->
                    if (`object`.has("id")) {
                        create(`object`)
                    } else {
                        null
                    }
                }
        }

        private fun create(system: JsonObject): CompletableFuture<PkSystem> {
            val id = getString(system, "id")
            val name = getString(system, "name")
            val tag = getString(system, "tag")
            val avatarUrl = getString(system, "avatar_url")

            var fronters: List<Member>? = null
            var firstFronter: Member? = null
            var members: List<Member>? = null
            var latchTimeout = 0

            return queryPkAPI("systems/@me/fronters")
                .thenApply { obj: JsonElement -> obj.asJsonObject }.thenAccept { `object`: JsonObject ->
                    val array = `object`.getAsJsonArray("members")
                    val list = ArrayList<Member>()
                    array.forEach { e: JsonElement -> list.add(Member.fromObject(e.asJsonObject)) }
                    fronters = list
                    firstFronter = list[0]
                    log("Fetched fronters list")
                }.thenCompose {
                    queryPkAPI("systems/@me/members").thenAccept { `object`: JsonElement ->
                        val array = `object`.asJsonArray
                        val list = ArrayList<Member>()
                        array.forEach { e: JsonElement -> list.add(Member.fromObject(e.asJsonObject)) }
                        members = list
                        log("Fetched Members list")
                    }
                }.thenCompose {
                    queryPkAPI("systems/@me/settings").thenApply { obj: JsonElement -> obj.asJsonObject }
                        .thenAccept { `object`: JsonObject ->
                            log("Fetched Latch Timeout Settings")
                            if (`object`.has("latch_timeout") && !`object`.get("latch_timeout").isJsonNull) {
                                latchTimeout = `object`.get("latch_timeout").asInt
                            }
                        }
                }
                .thenApply {
                    val sys = PkSystem(id, name, members, fronters, firstFronter, tag, latchTimeout, avatarUrl)
                    log("Logged in as system: " + sys.name)
                    sys
                }
        }

        private fun getString(`object`: JsonObject, key: String): String {
            if (`object`.has(key)) {
                val e = `object`.get(key)
                if (e.isJsonPrimitive && (e as JsonPrimitive).isString) {
                    return e.getAsString()
                }
            }
            return ""
        }
    }
}

private object PluralKitApi {
    private val client: HttpClient = NetworkUtil.createHttpClient(true)
    private var remaining = 1
    private var resetsInMillis: Long = 0
    private var limit = 2

    fun request(request: HttpRequest): CompletableFuture<JsonElement> {
        val cF = CompletableFuture<JsonElement>()
        ThreadExecuter.scheduleTask(Runnable { cF.complete(schedule(request)) })
        return cF
    }

    fun request(url: String?): CompletableFuture<JsonElement> {
        val builder = HttpRequest.newBuilder(URI.create(url))
        val token = PkSystem.token
        if (token != null && token.isNotEmpty()) {
            builder.header("Authorization", token)
        }
        return request(builder.build())
    }

    @Synchronized
    private fun schedule(request: HttpRequest): JsonElement {
        if (remaining == 0) {
            try {
                Thread.sleep(resetsInMillis)
            } catch (ignored: InterruptedException) {
            }
            remaining = limit
        }
        return query(request)
    }

    private fun query(request: HttpRequest): JsonElement {
        return try {
            log("Requesting: $request")
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            val responseBody = response.body()
            log("Response: $responseBody")

            remaining = response.headers().firstValue("X-RateLimit-Remaining").orElseThrow().toInt()
            val resetsInMillisHeader =
                response.headers().firstValueAsLong("X-RateLimit-Reset").orElseThrow() * 1000 - System.currentTimeMillis()
            // If the header value is bogus just reset in 0.5 seconds
            this.resetsInMillis = if (resetsInMillisHeader < 0) 500 else resetsInMillisHeader
            limit = response.headers().firstValue("X-RateLimit-Limit").orElseThrow().toInt()

            GsonHelper.GSON.fromJson(responseBody, JsonElement::class.java)
        } catch (e: Exception) {
            JsonObject()
        }
    }

    private fun log(message: String) {
        API.getInstance().logger.debug("[PluralKit] $message")
    }
}
