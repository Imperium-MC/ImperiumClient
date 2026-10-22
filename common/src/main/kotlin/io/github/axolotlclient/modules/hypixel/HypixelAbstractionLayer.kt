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

package io.github.axolotlclient.modules.hypixel

import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Function

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.Response
import io.github.axolotlclient.util.CachedAPI

class HypixelAbstractionLayer {
    private enum class RequestDataType(@JvmField val id: String) {
        NETWORK_LEVEL("network_level"),
        BEDWARS_LEVEL("bedwars_level"),
        SKYWARS_EXPERIENCE("skywars_experience"),
        BEDWARS_DATA("bedwars_data"),
        PLAYER_DATA("player_data");
    }

    private val service: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    val bedwarsDataApi: CachedAPI<String, BedwarsData> = create(
        RequestDataType.BEDWARS_DATA
    ) { res ->
        BedwarsData(
            (res.getBody<Number>("final_kills_bedwars")!!).toInt(),
            (res.getBody<Number>("final_deaths_bedwars")!!).toInt(),
            (res.getBody<Number>("beds_broken_bedwars")!!).toInt(),
            (res.getBody<Number>("deaths_bedwars")!!).toInt(),
            (res.getBody<Number>("kills_bedwars")!!).toInt(),
            (res.getBody<Number>("losses_bedwars")!!).toInt(),
            (res.getBody<Number>("wins_bedwars")!!).toInt(),
            (res.getBody<Number>("winstreak")!!).toInt()
        )
    }

    val networkLevelApi: CachedAPI<String, Int> = createLevel(RequestDataType.NETWORK_LEVEL)

    val bedwarsLevelApi: CachedAPI<String, Int> = createLevel(RequestDataType.BEDWARS_LEVEL)

    val skywarsExpApi: CachedAPI<String, Int> = create(RequestDataType.SKYWARS_EXPERIENCE) { res ->
        val exp = res.getBody<Number>(RequestDataType.SKYWARS_EXPERIENCE.id)!!.toInt()
        if (exp == -1) {
            null
        } else {
            Math.round(ExpCalculator.getLevelForExp(exp))
        }
    }

    val playerDataApi: CachedAPI<String, PlayerData> = create(RequestDataType.PLAYER_DATA) { response ->
        GSON.fromJson(response.plainBody, PlayerData::class.java)
    }

    private fun queueRetry(
        attempts: Int, desc: String, request: Request,
        future: CompletableFuture<Optional<Response>>, count: Long, unit: TimeUnit
    ) {
        service.schedule({ queueRequest0(attempts - 1, desc, request, future) }, count, unit)
    }

    private fun queueRequest0(attempts: Int, desc: String, request: Request, future: CompletableFuture<Optional<Response>>) {
        if (attempts <= 0) {
            future.complete(Optional.empty())
            return
        }

        API.getInstance().get(request).whenComplete { response, throwable ->
            if (response === Response.CLIENT_ERROR || (response != null && response.status == 0)) {
                future.complete(Optional.empty())
                return@whenComplete
            }
            if (response == null) {
                API.getInstance().logger.warn("Failed to process request {}: ", desc, throwable)
                future.complete(Optional.empty())
                return@whenComplete
            }

            if (response.status == 429) {
                API.getInstance().logger.warn("Failed to process request {}: rate limited", desc, throwable)

                queueRetry(
                    attempts, desc, request, future,
                    response.firstHeader("RateLimit-Reset").map { obj: String -> obj.toLong() }.orElse(2L),
                    TimeUnit.SECONDS
                )
            } else if (response.status != 200 || response.isError) {
                API.getInstance().logger
                    .debug("Failed to process request {} ({}): {}", desc, response.status, response.body)
                future.complete(Optional.empty())
            } else {
                future.complete(Optional.of(response))
            }
        }
    }

    private fun queueRequest(desc: String, request: Request): CompletableFuture<Optional<Response>> {
        val future = CompletableFuture<Optional<Response>>()
        queueRequest0(MAX_ATTEMPTS, desc, request, future)
        return future
    }

    private fun <V : Any> create(type: RequestDataType, app: Function<Response, V?>): CachedAPI<String, V> =
        CachedAPI(
            { uuid: String ->
                val request = Request.Route.HYPIXEL
                    .builder()
                    .field("request_type", type.id)
                    .field("target_player", uuid)
                    .build()

                queueRequest("[%s, %s]".format(type.id, uuid), request).thenApply { opt ->
                    opt.flatMap { res ->
                        try {
                            Optional.ofNullable(app.apply(res))
                        } catch (e: Throwable) {
                            API.getInstance().logger.warn("Failed to parse request for {} (uuid={})", type.id, uuid)
                            Optional.empty()
                        }
                    }
                }
            }, 128, true
        )

    private fun createLevel(type: RequestDataType): CachedAPI<String, Int> = create(type) { res ->
        val level = res.getBody<Number>(type.id)!!.toInt()
        if (level == -1) null else level
    }

    private fun freePlayerData(uuid: String) {
        bedwarsDataApi.invalidate(uuid)
        networkLevelApi.invalidate(uuid)
        bedwarsLevelApi.invalidate(uuid)
        skywarsExpApi.invalidate(uuid)
    }

    fun clearPlayerData() {
        bedwarsDataApi.invalidate()
        networkLevelApi.invalidate()
        bedwarsLevelApi.invalidate()
        skywarsExpApi.invalidate()
    }

    fun handleDisconnectEvents(uuid: UUID) {
        freePlayerData(uuid.toString())
    }

    companion object {
        private val GSON: Gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        private const val MAX_ATTEMPTS = 5

        private val instance = HypixelAbstractionLayer()

        @JvmStatic
        fun getInstance(): HypixelAbstractionLayer = instance
    }
}
