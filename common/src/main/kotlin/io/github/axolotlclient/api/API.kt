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

import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.net.http.HttpTimeoutException
import java.time.Instant
import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.api.handlers.ChannelInviteHandler
import io.github.axolotlclient.api.handlers.ChatHandler
import io.github.axolotlclient.api.handlers.FriendRequestHandler
import io.github.axolotlclient.api.handlers.FriendRequestReactionHandler
import io.github.axolotlclient.api.handlers.StatusUpdateHandler
import io.github.axolotlclient.api.requests.AccountSettingsRequest
import io.github.axolotlclient.api.requests.GlobalDataRequest
import io.github.axolotlclient.api.types.AccountSettings
import io.github.axolotlclient.api.types.Relation
import io.github.axolotlclient.api.types.User
import io.github.axolotlclient.api.util.Authentication
import io.github.axolotlclient.api.util.BiContainer
import io.github.axolotlclient.api.util.MojangAuth
import io.github.axolotlclient.api.util.SocketMessageHandler
import io.github.axolotlclient.api.util.StatusUpdateProvider
import io.github.axolotlclient.modules.auth.Account
import io.github.axolotlclient.util.GsonHelper
import io.github.axolotlclient.util.Logger
import io.github.axolotlclient.util.NetworkUtil
import io.github.axolotlclient.util.ThreadExecuter
import io.github.axolotlclient.util.notifications.NotificationProvider
import kotlin.math.max
import kotlin.math.min

class API(statusUpdateProvider: StatusUpdateProvider) {

    val logger: Logger = AxolotlClientCommon.getInstance().logger
    val notificationProvider: NotificationProvider = AxolotlClientCommon.getInstance().notificationProvider
    private val statusUpdateProvider: StatusUpdateProvider
    val apiOptions: Options
    private val handlers: MutableCollection<SocketMessageHandler>
    private var socket: WebSocket? = null
    var self: User? = null
        private set
    private var account: Account? = null
    private var auth: Authentication? = null
    var settings: AccountSettings? = null
    private var client: HttpClient? = null
    private var restartingFuture: CompletableFuture<*>? = null
    private var nextRestartSecs = 0
    private var statusUpdateFuture: Future<*>? = null
    private val statusUpdateExecutor: ScheduledExecutorService
    private val starting = AtomicBoolean()

    init {
        if (Instance != null) {
            throw IllegalStateException("API may only be instantiated once!")
        }
        this.statusUpdateProvider = statusUpdateProvider
        this.apiOptions = AxolotlClientCommon.getInstance().apiOptions
        handlers = HashSet()
        handlers.add(ChatHandler.getInstance())
        handlers.add(FriendRequestHandler())
        handlers.add(FriendRequestReactionHandler())
        handlers.add(StatusUpdateHandler())
        handlers.add(ChannelInviteHandler())
        Instance = this
        statusUpdateExecutor = Executors.newSingleThreadScheduledExecutor()
    }

    fun onOpen(channel: WebSocket) {
        logger.debug("API connected!")
        afterStartupListeners.forEach { p -> p.left!!.run() }
        afterStartupListeners.removeIf { p -> p.right == ListenerType.ONCE }
        socket = channel
    }

    private fun authenticate(): CompletableFuture<*> {
        //if (client != null) {
        // We have to rely on the gc to collect previous client objects as close() was only implemented in java 21.
        // However, we are currently compiling against java 17.
        //client.close();
        //}

        try {
            if (!GlobalDataRequest.get(true)[1, TimeUnit.MINUTES].success) {
                logger.warn("Not trying to start API as it couldn't be reached!")
                return scheduleRestart()
            }
        } catch (e: InterruptedException) {
            logger.warn("Not trying to start API as it couldn't be reached within the timeout of 1 minute!")
            return scheduleRestart()
        } catch (e: ExecutionException) {
            logger.warn("Not trying to start API as it couldn't be reached within the timeout of 1 minute!")
            return scheduleRestart()
        } catch (e: TimeoutException) {
            logger.warn("Not trying to start API as it couldn't be reached within the timeout of 1 minute!")
            return scheduleRestart()
        }

        nextRestartSecs = 2
        logDetailed("Authenticating with Mojang...")

        val serverId = MojangAuth.authenticate(account!!)

        if (serverId == null) {
            logger.error("Failed to authenticate with Mojang!")
            return CompletableFuture.failedFuture<Any?>(UnsupportedOperationException("Failed to authenticate with mojang"))
        }

        logDetailed("Requesting authentication from backend...")

        return get(
            Request.Route.AUTHENTICATE.builder()
                .query("username", account!!.name)
                .query("server_id", serverId)
                .build()
        ).whenComplete { response, throwable ->
            if (throwable != null) {
                logger.error("Failed to authenticate!", throwable)
                return@whenComplete
            }
            if (response.isError) {
                logger.error("Failed to authenticate!", response.error!!.description)
                return@whenComplete
            }

            auth = Authentication(response.getBody("access_token")!!)
            logDetailed("Obtained token!")
            CompletableFuture.allOf(
                get(Request.Route.ACCOUNT.builder().build())
                    .thenAccept { r ->
                        val user = User(
                            sanitizeUUID(r.getBody("uuid")!!),
                            r.getBody("username"), Relation.NONE,
                            r.getBody("registered", java.util.function.Function<String, Instant> { obj -> Instant.parse(obj) }),
                            io.github.axolotlclient.api.types.Status.UNKNOWN,
                            r.ifBodyHas(
                                "previous_usernames",
                                Supplier {
                                    val previous: List<Map<*, *>> = r.getBody("previous_usernames")!!
                                    previous.map { m ->
                                        User.OldUsername(m["username"] as String?, m["public"] as Boolean)
                                    }
                                }
                            )
                        )
                        user.system = io.github.axolotlclient.api.types.PkSystem.fromToken(apiOptions.pkToken.get()).join()
                        self = user
                        logDetailed("Created self user!")
                    },
                AccountSettingsRequest.get().thenAccept { r ->
                    apiOptions.retainUsernames.set(r.retainUsernames)
                    apiOptions.showActivity.set(r.showActivity)
                    apiOptions.showLastOnline.set(r.showLastOnline)
                    apiOptions.showRegistered.set(r.showRegistered)
                    apiOptions.allowFriendsImageAccess.set(r.allowFriendsImageAccess)
                }
            ).thenRun { logDetailed("completed data requests") }.join()
            createSession()
            startStatusUpdateThread()
        }
    }

    fun get(request: Request): CompletableFuture<Response> {
        return request(request, "GET")
    }

    fun patch(request: Request): CompletableFuture<Response> {
        return request(request, "PATCH")
    }

    fun post(request: Request): CompletableFuture<Response> {
        return request(request, "POST")
    }

    fun delete(request: Request): CompletableFuture<Response> {
        return request(request, "DELETE")
    }

    private fun request(request: Request, method: String): CompletableFuture<Response> {
        if (!apiOptions.enabled.get()) {
            return CompletableFuture.completedFuture(
                Response.builder().status(0).body("{\"description\":\"Integration disabled!\"}").build()
            )
        }
        if (!apiOptions.privacyAccepted.get().isAccepted()) {
            return CompletableFuture.completedFuture(Response.CLIENT_ERROR)
        }
        if (request.requiresAuthentication && !isAuthenticated()) {
            logger.debug("Tried to request {} {} without authentication, but this request requires it!", method, request)
            return CompletableFuture.completedFuture(Response.CLIENT_ERROR)
        }
        val route = getUrl(request)
        return request(route, request.bodyFields, request.rawBody, method, request.headers)
    }

    private fun request(
        url: URI,
        payload: Map<String, *>?,
        rawBody: ByteArray?,
        method: String,
        headers: Map<String, String>?
    ): CompletableFuture<Response> {
        return CompletableFuture.supplyAsync({
            try {
                logDetailed("Starting request to $method $url")

                val builder = HttpRequest.newBuilder(url)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")

                if (auth != null) {
                    if (auth!!.expiration.isBefore(Instant.now())) {
                        authenticate().join()
                    }
                    builder.header("Authorization", auth!!.token)
                }

                if (headers != null) {
                    headers.forEach { (key, value) -> builder.header(key, value) }
                }

                if (rawBody != null) {
                    builder.method(method, HttpRequest.BodyPublishers.ofByteArray(rawBody))
                } else if (!(payload == null || payload.isEmpty())) {
                    val body = StringBuilder()
                    GsonHelper.GSON.toJson(payload, body)
                    logDetailed("Sending payload: \n$body")
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.noBody())
                }
                if (client == null) {
                    client = NetworkUtil.createHttpClient()
                }

                val response = client!!.send(builder.build(), HttpResponse.BodyHandlers.ofString())

                val body = response.body()

                val code = response.statusCode()
                if (apiOptions.detailedLogging.get()) {
                    if (!url.path.endsWith(Request.Route.AUTHENTICATE.path)) {
                        logDetailed("Response: code: $code body: $body")
                    } else {
                        logDetailed(
                            "Response: code: $code body: " +
                                body.toString().replace("(\"access_token\": ?\")[^\"]+(\")".toRegex(), "$1[token redacted]$2")
                        )
                    }
                }
                return@supplyAsync Response.builder().body(body).status(code).headers(response.headers().map()).build()
            } catch (e: ConnectException) {
                logger.warn("Backend unreachable!")
                Response.CLIENT_ERROR
            } catch (e: HttpTimeoutException) {
                logger.warn("Backend unreachable!")
                Response.CLIENT_ERROR
            } catch (e: Exception) {
                onError(e)
                Response.CLIENT_ERROR
            }
        }, ThreadExecuter.service())
    }

    internal fun getUrl(request: Request): URI {
        val url = StringBuilder(if (Constants.API_URL.endsWith("/")) Constants.API_URL else Constants.API_URL + "/")
        url.append(request.route.path)
        if (request.path != null) {
            for (p in request.path!!) {
                url.append("/").append(p)
            }
        }
        if (request.query != null && request.query!!.isNotEmpty()) {
            url.append("?")
            request.query!!.forEach { v ->
                if (url[url.length - 1] != '?') {
                    url.append("&")
                }
                url.append(v)
            }
        }
        return URI.create(url.toString())
    }

    fun shutdown() {
        if (restartingFuture != null) {
            restartingFuture!!.cancel(true)
            restartingFuture = null
        }
        if (statusUpdateFuture != null) {
            statusUpdateFuture!!.cancel(true)
            statusUpdateExecutor.shutdown()
            statusUpdateFuture = null
        }
        if (isAuthenticated()) {
            logger.debug("Shutting down API")
            if (isSocketConnected()) {
                socket!!.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown")
                socket = null
            }
            // We have to rely on the gc to collect previous client objects as close() was only implemented in java 21.
            // However, we are currently compiling against java 17.
            //client.close();
            auth = null
        }
        client = null
    }

    fun isSocketConnected(): Boolean {
        return socket != null
    }

    fun isAuthenticated(): Boolean {
        return auth != null
    }

    fun getIndicatorColor(): Int {
        var color = if (isAuthenticated()) 0xFFFFCC00.toInt() else 0xFFFF0000.toInt()
        if (isSocketConnected()) {
            color = 0xFF009000.toInt()
        }
        return color
    }

    fun logDetailed(message: String, vararg args: Any?) {
        if (apiOptions.detailedLogging.get()) {
            logger.debug("[DETAIL] $message", *args)
        }
    }

    fun onMessage(message: String) {
        logDetailed("Handling socket message: {}", message)

        val res = Response.builder().status(200).body(message).build()
        val target: String = res.getBody("target")!!
        var handled = false
        for (handler in handlers) {
            if (handler.isApplicable(target)) {
                handler.handle(res)
                handled = true
            }
        }
        if (!handled) {
            logger.warn("Unhandled socket message target {}! This may be caused by using an outdated client.", target)
        }
    }

    fun onError(throwable: Throwable) {
        logger.error("Error while handling API traffic:", throwable)
    }

    fun onClose(statusCode: Int, reason: String) {
        logDetailed("Session closed! code: $statusCode reason: $reason")
        val errorCodes = intArrayOf(1011, 1007, 1014)
        if (errorCodes.any { it == statusCode } && apiOptions.enabled.get()) {
            scheduleRestart()
        }
    }

    private fun scheduleRestart(): CompletableFuture<*> {
        if (restartingFuture != null) {
            restartingFuture!!.cancel(true)
        }
        nextRestartSecs = max(2, min(nextRestartSecs * 2, 60 * 3))

        logger.info("Trying restart in $nextRestartSecs seconds.")
        restartingFuture = CompletableFuture.supplyAsync<Any?>({
            logDetailed("Restarting API session...")
            startup(account!!).join()
        }, CompletableFuture.delayedExecutor(nextRestartSecs.toLong(), TimeUnit.SECONDS, ThreadExecuter.service()))
        return restartingFuture!!
    }

    private fun createSession() {
        if (!Constants.TESTING) {
            try {
                logDetailed("Connecting to websocket..")
                val gateway = Request.Route.GATEWAY.create().resolve()
                val uri = (if (gateway.scheme!!.endsWith("s")) "wss" else "ws") +
                    gateway.toString().substring(gateway.scheme!!.length)
                socket = client!!.newWebSocketBuilder()
                    .header("Authorization", auth!!.token)
                    .header("User-Agent", NetworkUtil.getUserAgent())
                    .buildAsync(URI.create(uri), ClientEndpoint()).join()
                logDetailed("Socket connected")
            } catch (e: Exception) {
                logger.error("Failed to start Socket! ", e)
            }
        }
    }

    fun restart() {
        if (isSocketConnected()) {
            shutdown()
        }
        if (account != null) {
            startup(account!!)
        } else {
            apiOptions.enabled.set(false)
        }
    }

    fun startup(account: Account): CompletableFuture<*> {
        this.account = account
        if (!Constants.ENABLED) {
            return CompletableFuture.failedFuture<Any?>(UnsupportedOperationException("API is disabled at compile-time"))
        }

        if (account.isOffline) {
            return CompletableFuture.failedFuture<Any?>(UnsupportedOperationException("Account is offline"))
        }

        if (apiOptions.enabled.get()) {
            when (apiOptions.privacyAccepted.get()) {
                Options.PrivacyPolicyState.UNSET -> return apiOptions.openPrivacyNoteScreen.get()
                    .thenCompose<Any?> { v: Boolean ->
                        if (v) {
                            startupAPI()
                        } else {
                            CompletableFuture.failedStage<Any?>(UnsupportedOperationException("Terms not accepted"))
                        }
                    }

                Options.PrivacyPolicyState.ACCEPTED -> return startupAPI()
                Options.PrivacyPolicyState.DENIED -> {
                }
            }
        }
        return CompletableFuture.failedFuture<Any?>(UnsupportedOperationException("API is disabled"))
    }

    private fun startupAPI(): CompletableFuture<Any?> {
        if (!isSocketConnected()) {
            if (Constants.TESTING) {
                return CompletableFuture.failedFuture<Any?>(UnsupportedOperationException("API is disabled for testing!"))
            }
            if (starting.getAndSet(true)) {
                return CompletableFuture.completedFuture(null)
            }

            logger.info("Starting API...")
            return CompletableFuture.runAsync(Runnable {
                this.authenticate().join()
                starting.set(false)
            }, ThreadExecuter.service()).thenApply<Any?> { null }
        } else {
            logger.warn("API is already running!")
        }
        return CompletableFuture.failedFuture<Any?>(UnsupportedOperationException("API is already running"))
    }

    private fun startStatusUpdateThread() {
        statusUpdateProvider.initialize()
        if (statusUpdateFuture != null) {
            statusUpdateFuture!!.cancel(true)
        }
        statusUpdateFuture = statusUpdateExecutor.scheduleAtFixedRate(
            {
                try {
                    if (apiOptions.sendStatusUpdates.get()) {
                        val request = statusUpdateProvider.getStatus()
                        if (request != null) {
                            post(request)
                        }
                    }
                } catch (e: Throwable) {
                    logger.warn("Failed to send status update! Skipping... ", e)
                }
            },
            50, (Constants.STATUS_UPDATE_DELAY * 1000).toLong(), TimeUnit.MILLISECONDS
        )
    }

    enum class ListenerType {
        ONCE, REPEATING
    }

    companion object {
        private var Instance: API? = null

        private val afterStartupListeners: MutableList<BiContainer<Runnable, ListenerType>> = ArrayList()

        @JvmStatic
        fun addStartupListener(listener: Runnable) {
            afterStartupListeners.add(BiContainer.of(listener, ListenerType.REPEATING))
        }

        @JvmStatic
        fun addStartupListener(listener: Runnable, type: ListenerType) {
            afterStartupListeners.add(BiContainer.of(listener, type))
        }

        @JvmStatic
        fun getInstance(): API {
            return Instance!!
        }

        @JvmStatic
        fun sanitizeUUID(uuid: String): String {
            if (uuid.contains("-")) {
                return validateUUID(uuid.replace("-", ""))
            }
            return validateUUID(uuid)
        }

        private fun validateUUID(uuid: String): String {
            require(uuid.length == 32) { "Not a valid UUID (undashed): $uuid" }
            return uuid
        }
    }
}
