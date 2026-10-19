/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.imperium.enforcement;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Outgoing plugin-channel guard (docs/wave4-roadmap.md W4-C). On ImperiumMC servers only the
 * approved channel set may be registered or spoken on; third-party mod channels are silently
 * not sent, so mod fingerprints never reach the server's brand/channel review
 * (the plugin's {@code ClientFingerprintListener} — banned/suspicious channel lists and the
 * {@code minecraft:register} payload review).
 *
 * <p>Allowlist shape (owner spec): vanilla brand/channels, the standard Fabric client channels
 * (both the {@code fabric:} and modern {@code c:} namespaces, which carry Fabric's own
 * register/unregister protocol), {@code lunar:}/{@code lunarclient:} style channels,
 * {@code imperiumclient:*} (our handshake), and WorldEdit CUI. Everything a third-party mod
 * would register or send — {@code sodium:}, {@code meteor:}, {@code hypixel:}, … — is refused
 * while scoped to our server, and untouched everywhere else.</p>
 *
 * <p>Kept free of Minecraft imports except through reflection so the decision logic is
 * unit-testable; the Fabric register-payload rebuild uses reflection
 * ({@code net.fabricmc.fabric.impl.networking.CommonRegisterPayload}) so this module never
 * hard-links Fabric networking internals. If the reflective rebuild ever fails the payload is
 * passed through unfiltered (fail-open: never break legitimate networking) and the failure is
 * reported once so it can be fixed.</p>
 */
public final class ChannelGuard {

	/** The Fabric API's register/unregister payload class (channel {@code c:register}). */
	private static final String FABRIC_REGISTER_PAYLOAD_CLASS =
		"net.fabricmc.fabric.impl.networking.CommonRegisterPayload";

	/** Namespaces always allowed on an enforced server (vanilla + standard client namespaces). */
	private static final Set<String> ALLOWED_NAMESPACES = Set.of(
		"minecraft", // vanilla brand/register/unregister and every vanilla channel
		"fabric", // Fabric API client channels
		"c", // modern "standard client channels" namespace (c:register et al.)
		"lunar", "lunarclient", // Lunar-style client channels
		"imperiumclient" // our own handshake (hello/features) and future client channels
	);

	/** Individual non-namespaced channels allowed besides the namespaces above. */
	private static final Set<String> ALLOWED_CHANNELS = Set.of(
		"worldedit:cui" // WorldEdit CUI protocol (docs/wecui-server.md)
	);

	private static volatile boolean registerRebuildBroken = false;

	private ChannelGuard() {
	}

	/** True when the full channel id ({@code namespace:path}) may be used on our server. */
	public static boolean isChannelAllowed(String channel) {
		if (channel == null || channel.isEmpty()) {
			return false;
		}
		String normalized = channel.toLowerCase(Locale.ROOT);
		if (ALLOWED_CHANNELS.contains(normalized)) {
			return true;
		}
		int separator = normalized.indexOf(':');
		String namespace = separator > 0 ? normalized.substring(0, separator) : normalized;
		return ALLOWED_NAMESPACES.contains(namespace);
	}

	/** Filters a channel-name list (as carried by register/unregister payloads) to the allowlist. */
	public static List<String> filterChannelNames(List<String> channels) {
		List<String> kept = new ArrayList<>(channels.size());
		for (String channel : channels) {
			if (channel != null && !channel.isBlank() && isChannelAllowed(channel)) {
				kept.add(channel);
			}
		}
		return kept;
	}

	/** True when the reflective Fabric register rebuild has failed at least once (test hook). */
	public static boolean isRegisterRebuildBroken() {
		return registerRebuildBroken;
	}

	/**
	 * Filters a Fabric {@code CommonRegisterPayload} (register/unregister announcement) to the
	 * allowlisted channels.
	 *
	 * @return the same instance when nothing needed filtering; a rebuilt payload when some
	 *         channels were removed; {@code null} when the filtered set is empty (suppress the
	 *         whole announcement); the original payload on a reflective failure (fail-open).
	 */
	public static Object filterFabricRegisterPayload(Object payload) {
		try {
			Class<?> payloadClass = payload.getClass();
			Method version = payloadClass.getMethod("version");
			Method protocol = payloadClass.getMethod("protocol");
			Method channels = payloadClass.getMethod("channels");
			Set<?> channelSet = (Set<?>) channels.invoke(payload);

			List<Object> kept = new ArrayList<>(channelSet.size());
			boolean changed = false;
			for (Object channel : channelSet) {
				if (isChannelAllowed(String.valueOf(channel))) {
					kept.add(channel);
				} else {
					changed = true;
				}
			}
			if (!changed) {
				return payload;
			}
			if (kept.isEmpty()) {
				return null;
			}
			Constructor<?> constructor = payloadClass.getConstructor(int.class, String.class, Set.class);
			// Rebuild with a LinkedHashSet to keep announcement order stable.
			Set<Object> rebuilt = new java.util.LinkedHashSet<>(kept);
			return constructor.newInstance(version.invoke(payload), protocol.invoke(payload), rebuilt);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException failure) {
			registerRebuildBroken = true;
			return payload;
		}
	}

	/** True when the object is a Fabric register/unregister announcement payload. */
	public static boolean isFabricRegisterPayload(Object payload) {
		return payload != null && payload.getClass().getName().equals(FABRIC_REGISTER_PAYLOAD_CLASS);
	}
}
