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

package io.github.axolotlclient.modules.auth

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.util.GsonHelper
import io.github.axolotlclient.util.Logger

abstract class Accounts {
    val category: OptionCategory = OptionCategory.create("auth")

    val accounts: MutableList<Account> = ArrayList()
    private var current: Account? = null

    fun getCurrent(): Account? = current

    fun setCurrent(account: Account?) {
        current = account
    }

    fun load() {
        val legacy = AxolotlClientCommon.resolveConfigFile("../accounts.json")
        val saveFile = accountsSaveFile
        if (Files.exists(legacy)) {
            try {
                if (Files.exists(saveFile)) {
                    Files.move(legacy, legacy.resolveSibling("accounts.json.old"))
                    logger.info("Renaming legacy accounts save file since new one exists already.")
                } else {
                    Files.createDirectories(saveFile.parent)
                    Files.move(legacy, saveFile)
                }
            } catch (e: IOException) {
                logger.warn("Failed to move legacy accounts file to new location", e)
            }
        }
        if (Files.exists(saveFile)) {
            try {
                val list = GsonHelper.GSON.fromJson(Files.newBufferedReader(saveFile), JsonObject::class.java)
                if (list != null) {
                    list.get("accounts").asJsonArray.forEach { jsonElement ->
                        accounts.add(Account.deserialize(jsonElement.asJsonObject))
                    }
                }
            } catch (e: IOException) {
                logger.warn("Failed to load accounts file!", e)
            }
        } else {
            try {
                Files.createDirectories(accountsSaveFile.parent)
                Files.createFile(accountsSaveFile)
            } catch (e: IOException) {
                logger.warn("Failed to create accounts file", e)
            }
        }
    }

    protected open val accountsSaveFile: Path
        get() = AxolotlClientCommon.resolveConfigFile("accounts.json")

    fun addAccount(account: Account) {
        accounts.add(account)
    }

    abstract fun login(account: Account)

    fun removeAccount(account: Account) {
        accounts.remove(account)
        save()
    }

    fun save() {
        val array = JsonArray()
        accounts.forEach { account -> array.add(account.serialize()) }
        val obj = JsonObject()
        obj.add("accounts", array)
        try {
            Files.createDirectories(accountsSaveFile.parent)
            Files.writeString(accountsSaveFile, GsonHelper.GSON.toJson(obj))
        } catch (e: IOException) {
            logger.error("Failed to save acounts config!", e)
        }
    }

    private val logger: Logger
        get() = AxolotlClientCommon.getInstance().logger

    protected fun isContained(uuid: String): Boolean =
        accounts.any { account -> account.uuid == uuid }

    fun allowOfflineAccounts(): Boolean =
        accounts.isNotEmpty() && !accounts.all { it.isOffline }

    abstract fun showAccountsExpiredScreen(account: Account): CompletableFuture<Account>

    abstract fun displayDeviceCode(data: DeviceFlowData)
}
