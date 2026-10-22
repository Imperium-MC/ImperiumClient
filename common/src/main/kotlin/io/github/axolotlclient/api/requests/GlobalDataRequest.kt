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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.api.types.GlobalData
import io.github.axolotlclient.api.types.SemVer

object GlobalDataRequest {
    private var cachedData: GlobalData? = null
    private var nextRequest: Instant? = null
    private val lock = Semaphore(1)

    @JvmStatic
    fun get(): CompletableFuture<GlobalData> {
        return get(false)
    }

    @JvmStatic
    fun get(forceRequest: Boolean): CompletableFuture<GlobalData> {
        return CompletableFuture.supplyAsync {
            if (API.getInstance().apiOptions.enabled.get()) {
                try {
                    lock.acquire()
                } catch (ignored: InterruptedException) {
                }
                val cached = cachedData
                if (cached != null) {
                    val now = Instant.now()
                    if (!forceRequest && nextRequest!!.isAfter(now)) {
                        lock.release()
                        return@supplyAsync cached
                    }
                }
                return@supplyAsync API.getInstance().get(Request.Route.GLOBAL_DATA.create())
                    .thenApply { res ->
                        if (res.isError) {
                            GlobalData.EMPTY
                        } else {
                            GlobalData(
                                true, res.getBody("total_players")!!,
                                res.getBody("online_players")!!,
                                SemVer.parse(res.getBody("latest_version")!!), res.getBodyOrElse("notes", "")
                            )
                        }
                    }
                    .thenApply { d ->
                        nextRequest = Instant.now().plusSeconds(300)
                        cachedData = d
                        lock.release()
                        d
                    }.join()
            }
            GlobalData.EMPTY
        }
    }
}
