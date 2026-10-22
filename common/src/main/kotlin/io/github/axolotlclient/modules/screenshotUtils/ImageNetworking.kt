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

package io.github.axolotlclient.modules.screenshotUtils

import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Constants
import io.github.axolotlclient.api.Request

abstract class ImageNetworking {
    abstract fun uploadImage(file: java.nio.file.Path)

    protected fun upload(file: java.nio.file.Path): CompletableFuture<String> =
        try {
            upload(file.fileName.toString(), Files.readAllBytes(file))
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.error("Failed to upload image", e)
            CompletableFuture.completedFuture("")
        }

    protected fun upload(name: String, data: ByteArray): CompletableFuture<String> =
        API.getInstance().post(Request.Route.IMAGE.builder().path(name).rawBody(data).build())
            .thenApply { response ->
                if (response.isError) {
                    AxolotlClientCommon.getInstance().logger
                        .error("Failed to upload image, server responded with $response")
                    ""
                } else {
                    idToUrl(response.plainBody!!)
                }
            }

    @JvmRecord
    data class ImageData(val name: String, val data: ByteArray, val uploader: String?, val sharedAt: Instant?) {
        companion object {
            @JvmField
            val EMPTY = ImageData("", ByteArray(0), null, null)
        }
    }

    companion object {
        private val URL_PATTERN = Pattern.compile("(?:.+/)?(\\d+)(?:/?.+)?")

        @JvmStatic
        protected fun idToUrl(id: String): String =
            Request.Route.IMAGE.builder().path(id).path("view").build().resolve().toString()

        @JvmStatic
        protected fun urlToId(url: String): Optional<String> {
            if (url.contains("/") && !url.startsWith(Constants.API_URL)) {
                return Optional.empty()
            }
            val matcher = URL_PATTERN.matcher(url)
            if (!matcher.matches()) {
                return Optional.empty()
            }
            return Optional.of(matcher.group(1))
        }

        @JvmStatic
        protected fun ensureUrl(urlOrId: String): Optional<String> =
            urlToId(urlOrId).map { idToUrl(it) }
    }

    protected fun download(url: String): CompletableFuture<ImageData> {
        val id = urlToId(url)
        return id.map { s ->
            API.getInstance().get(
                Request.Route.IMAGE.builder().requiresAuthentication(false).path(s).build()
            )
                .thenApply { res ->
                    if (res.isError) {
                        return@thenApply ImageData.EMPTY
                    }
                    val name = res.getBody<String>("filename")
                    val base64 = res.getBody<String>("file")
                    val uploader = res.getBody<String>("uploader")
                    val sharedAt = res.getBody("shared_at") { obj: String -> Instant.parse(obj) }
                    ImageData(name!!, Base64.getDecoder().decode(base64!!), uploader, sharedAt)
                }
        }.orElseGet { CompletableFuture.completedFuture(ImageData.EMPTY) }
    }
}
