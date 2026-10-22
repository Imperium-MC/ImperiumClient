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
package io.github.axolotlclient.api.requests

import java.time.Instant
import java.util.Collections
import java.util.Optional
import java.util.TreeSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.RateLimiter
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.types.Relation
import io.github.axolotlclient.api.types.Status
import io.github.axolotlclient.api.types.User
import io.github.axolotlclient.util.GsonHelper
import java.lang.Long.toUnsignedString

object UserRequest {

    private val userCache: Cache<String, Optional<User>> = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(400).build()
    private val limiter = RateLimiter.create(2.0)
    private val onlineCache: Cache<String, Boolean> = CacheBuilder.newBuilder().maximumSize(100)
        .expireAfterAccess(10, TimeUnit.SECONDS)
        .expireAfterWrite(2, TimeUnit.MINUTES).build()
    private val onlineRequests: MutableSet<String> = Collections.synchronizedSet(TreeSet())

    @JvmStatic
    fun getOnline(uuid: String?): Boolean {
        if (uuid == null || !API.getInstance().isAuthenticated()) {
            return false
        }

        val sanitized = API.sanitizeUUID(uuid)

        if (API.getInstance().self != null && sanitized == API.getInstance().self!!.uuid) {
            return true
        }

        val isOnline = onlineCache.getIfPresent(sanitized)
        if (isOnline == null) {
            if (!onlineRequests.contains(sanitized)) {
                onlineRequests.add(sanitized)
                CompletableFuture.runAsync(Runnable {
                    limiter.acquire()
                    get(sanitized).thenApply { u -> u.isPresent && u.get().status!!.isOnline() }
                        .thenAccept { b -> onlineCache.put(sanitized, b) }
                        .thenRun { onlineRequests.remove(sanitized) }
                })
            }
            return false
        }
        return isOnline
    }

    private class UserResponse {
        var uuid: String? = null
        var username: String? = null
        var relation: String? = null
        var registered: String? = null
        var status: Status? = null
        var previousUsernames: List<String>? = null

        fun toUser(): User {
            val registered = this.registered?.let { Instant.parse(it) }
            val previousNames = previousUsernames?.map { s -> User.OldUsername(s, true) }
            return User(uuid!!, username!!, Relation.get(relation), registered, this.status, previousNames)
        }
    }

    @JvmStatic
    fun get(dUuid: String): CompletableFuture<Optional<User>> {
        val uuid = API.sanitizeUUID(dUuid)
        val cached = userCache.getIfPresent(uuid)
        // OptionalAssignedToNull guard: getIfPresent returns null for absent, an Optional for cached
        if (cached != null) {
            return CompletableFuture.completedFuture(cached)
        }
        return API.getInstance().get(Request.Route.USER.builder().path(uuid).build()).thenApply { response ->
            if (response.isError) {
                null
            } else {
                val res = GsonHelper.GSON.fromJson(response.plainBody, UserResponse::class.java)
                res.toUser()
            }
        }.thenApply { u ->
            val opt = Optional.ofNullable(u)
            userCache.put(uuid, opt)
            opt
        }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun getUploadedImages(userUuid: String): CompletableFuture<Optional<List<String>>> {
        return API.getInstance().get(Request.Route.USER.builder().path(userUuid).path("images").build())
            .thenApply { r ->
                if (!r.isError) {
                    val list = r.body as List<Long>
                    Optional.of(list.stream().map { id: Long? -> toUnsignedString(id!!) }.toList())
                } else {
                    Optional.empty()
                }
            }
    }
}
