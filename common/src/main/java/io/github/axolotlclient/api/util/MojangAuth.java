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

package io.github.axolotlclient.api.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import io.github.axolotlclient.api.API;
import io.github.axolotlclient.bridge.PlatformDispatch;
import io.github.axolotlclient.modules.auth.Account;
import org.apache.commons.lang3.RandomStringUtils;

public class MojangAuth {

	public static String authenticate(Account account) {
		try {

			String serverId = minecraftSha1(RandomStringUtils.random(40).getBytes(StandardCharsets.UTF_8));
			PlatformDispatch.api$login(UUIDHelper.fromUndashed(account.getUuid()), account.getAuthToken(), serverId);
			return serverId;
		} catch (Exception e) {
			API.getInstance().logDetailed("MojangAuth Exception: ", e);
		}
		return null;
	}

	private static String minecraftSha1(byte[]... bytes) {
		int length = Arrays.stream(bytes).mapToInt(a -> a.length).sum();
		byte[] data = new byte[length];

		int index = 0;

		for (byte[] arr : bytes) {
			int size = arr.length;
			System.arraycopy(arr, 0, data, index, size);
			index += size;
		}

		try {
			return new BigInteger(MessageDigest.getInstance("SHA-1").digest(data)).toString(16);
		} catch (NoSuchAlgorithmException ignored) {
		}
		return null;
	}
}
