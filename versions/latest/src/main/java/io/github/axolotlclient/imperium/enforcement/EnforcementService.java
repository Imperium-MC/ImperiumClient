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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.imperium.ImperiumClientSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The local rules enforcement layer (docs/wave4-roadmap.md W4-C) — the trusted-client
 * interceptor. All enforcement happens on OUTGOING packets before the socket, at the single
 * chokepoint every client-bound-to-server packet passes through
 * ({@code Connection.send(Packet, ChannelFutureListener, boolean)}, wrapped by
 * {@code ConnectionEnforcementMixin}).
 *
 * <p><strong>Scope rule (non-negotiable):</strong> the layer is completely inert unless the
 * connection's address matches the configured allowlist (hostname suffix match, dot boundary,
 * default exactly imperiummc.net / play.imperiummc.net). In singleplayer, on LAN (memory
 * connections never match), and on every other server no packet of any kind is touched. There is
 * no player-facing master toggle: enforcement on an ImperiumMC server is server policy, not a
 * client setting (issue #15 item 11).</p>
 *
 * <p>Three enforcement functions while in scope:</p>
 * <ol>
 *   <li><strong>Movement sanitization</strong> — outgoing
 *       {@code ServerboundMovePlayerPacket.Pos/PosRot} deltas are validated against
 *       {@link MovementEnvelope} (mirroring the plugin's CheatPreventionListener /
 *       MovementSimulationCheck thresholds) and CLAMPED to the envelope, never dropped, so a
 *       cheat mod cannot make the player move illegally and a legit player never desyncs.
 *       Movement additionally requires the ImperiumMC handshake to have answered this session
 *       ({@link ImperiumClientSession#serverResponded()}) — on any other server at that
 *       address the clamp stays off and the server's own checks apply.</li>
 *   <li><strong>Exploit payload refusal</strong> — sign and book payloads matching the server's
 *       own exploit checks ({@link PayloadRules}) are refused: the packet is cancelled and the
 *       refusal is logged once per session per type.</li>
 *   <li><strong>Outgoing channel guard</strong> — only allowlisted plugin channels may be
 *       registered or spoken on ({@link ChannelGuard}); third-party channels are silently not
 *       sent, so mod fingerprints never reach the server's brand/channel review.</li>
 * </ol>
 *
 * <p>Threading: {@code Connection.send} runs on the client thread for vanilla traffic but may
 * be called from other threads by mods, so all mutable state here is either volatile, atomic,
 * or guarded; the movement-envelope state is snapshotted once per client tick
 * ({@link #snapshotMovementState}) and read lock-free by the send path.</p>
 */
public final class EnforcementService {

	private static final EnforcementService INSTANCE = new EnforcementService();

	/** Grace windows mirroring the plugin's CheatPreventionListener exemptions (ms). */
	private static final long TELEPORT_GRACE_MS = 2_000L;
	private static final long VELOCITY_GRACE_MS = 1_500L;

	private final AtomicLong hookPulse = new AtomicLong();
	private final AtomicLong enforcedActions = new AtomicLong();

	/** Per-connection scope decision (weak: connections must not leak via the cache). */
	private final WeakHashMap<Connection, Boolean> scopeCache = new WeakHashMap<>();

	/** One log line per refusal type per session. */
	private final Set<String> sessionLogged = ConcurrentHashMap.newKeySet();

	/** Timestamp of the last sign packet allowed through (0 = none this session). */
	private final AtomicLong lastSignAcceptedMs = new AtomicLong();

	// ── Movement path state (guarded by movementLock; written from the send path) ──
	private final Object movementLock = new Object();
	private double lastSentX, lastSentY, lastSentZ;
	private boolean hasLastSent;
	private int airborneTicks;
	private long teleportGraceUntil;
	private long velocityGraceUntil;

	/** Envelope input, snapshotted on the client thread each tick; null when not in a world. */
	private volatile MovementEnvelope.State movementState;

	private volatile boolean initialized = false;

	private EnforcementService() {
	}

	public static EnforcementService instance() {
		return INSTANCE;
	}

	/** Registers the tick snapshot + session lifecycle hooks. Idempotent. */
	public synchronized void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ClientTickEvents.END_CLIENT_TICK.register(client -> snapshotMovementState(client));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetSessionState());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			resetSessionState();
			synchronized (scopeCache) {
				scopeCache.clear();
			}
		});

		AxolotlClientCommon.getInstance().getLogger()
			.info("Local rules enforcement layer ready (scoped to the configured ImperiumMC allowlist)");
	}

	private void resetSessionState() {
		sessionLogged.clear();
		lastSignAcceptedMs.set(0);
		synchronized (movementLock) {
			hasLastSent = false;
			airborneTicks = 0;
			teleportGraceUntil = 0;
			velocityGraceUntil = 0;
		}
	}

	// ── Scope ───────────────────────────────────────────────────────────────────

	/**
	 * True when this connection is an enforced connection: not a memory connection, and the remote
	 * address matches the allowlist. The decision is computed once per connection and cached;
	 * off-scope connections cost one map lookup per packet.
	 *
	 * <p>There is no player-facing toggle to widen this: server rules are not user-disableable
	 * (issue #15 item 11), so scope is exactly the structural conditions above.</p>
	 */
	public boolean isConnectionInScope(Connection connection) {
		if (connection == null || connection.isMemoryConnection()) {
			return false;
		}
		Boolean cached;
		synchronized (scopeCache) {
			cached = scopeCache.get(connection);
		}
		if (cached != null) {
			return cached;
		}
		boolean allowed = computeAddressAllowed(connection);
		synchronized (scopeCache) {
			scopeCache.put(connection, allowed);
		}
		return allowed;
	}

	private boolean computeAddressAllowed(Connection connection) {
		SocketAddress remote = connection.getRemoteAddress();
		String socketHost = remote instanceof InetSocketAddress address ? address.getHostString() : null;
		// The entered address (ServerData.ip) also carries the SRV hostname; benign off-thread
		// read of a stable field, at worst null on a race and the socket-host check above decides.
		var serverData = Minecraft.getInstance().getCurrentServer();
		return wouldBeInScope(socketHost, serverData == null ? null : serverData.ip);
	}

	/**
	 * Pure scope decision over the two address sources a connection offers: the socket host and
	 * the entered server address. Test seam for the scope rule (no real DNS needed).
	 */
	public boolean wouldBeInScope(String socketHost, String serverEntryIp) {
		if (socketHost != null && isAddressAllowed(socketHost, EnforcementConfig.allowlist())) {
			return true;
		}
		return serverEntryIp != null && isAddressAllowed(serverEntryIp, EnforcementConfig.allowlist());
	}

	/**
	 * Hostname suffix match on the dot boundary: {@code play.imperiummc.net} and
	 * {@code imperiummc.net} match the default list; {@code evilimperiummc.net} does not. Strips
	 * a {@code :port} suffix and IPv6 brackets, lowercases, ignores empty entries.
	 */
	public static boolean isAddressAllowed(String rawHost, List<String> allowlist) {
		if (rawHost == null || rawHost.isBlank() || allowlist == null || allowlist.isEmpty()) {
			return false;
		}
		String host = rawHost.trim().toLowerCase(Locale.ROOT);
		if (host.startsWith("[")) {
			int close = host.indexOf(']');
			host = close > 0 ? host.substring(1, close) : host.substring(1);
		} else {
			int colon = host.lastIndexOf(':');
			// Strip a trailing :port only when it is the only colon (otherwise it is IPv6).
			if (colon > 0 && host.indexOf(':') == colon) {
				host = host.substring(0, colon);
			}
		}
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.isEmpty()) {
			return false;
		}
		for (String entry : allowlist) {
			String allowed = entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT);
			if (allowed.isEmpty()) {
				continue;
			}
			if (host.equals(allowed) || host.endsWith("." + allowed)) {
				return true;
			}
		}
		return false;
	}

	// ── The chokepoint ──────────────────────────────────────────────────────────

	/**
	 * The single outgoing decision point, invoked by {@code ConnectionEnforcementMixin} for
	 * every packet on every connection.
	 *
	 * @return the packet to send (possibly a clamped replacement), or {@code null} to refuse the
	 *         packet (it is not sent at all).
	 */
	public Packet<?> processOutgoing(Connection connection, Packet<?> packet) {
		hookPulse.incrementAndGet();
		if (packet == null || !isConnectionInScope(connection)) {
			return packet;
		}

		if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition()) {
			return sanitizeMovement(move);
		}
		if (packet instanceof ServerboundSignUpdatePacket sign) {
			return checkSignPacket(sign);
		}
		if (packet instanceof ServerboundEditBookPacket book) {
			return checkBookPacket(book);
		}
		if (packet instanceof ServerboundCustomPayloadPacket custom) {
			return guardCustomPayload(custom);
		}
		return packet;
	}

	// ── 1. Movement sanitization ────────────────────────────────────────────────

	private Packet<?> sanitizeMovement(ServerboundMovePlayerPacket move) {
		// The movement clamp additionally requires the ImperiumMC handshake to have answered —
		// without first-party confirmation the client behaves like any other modded client.
		if (!ImperiumClientSession.getInstance().serverResponded()) {
			return move;
		}
		MovementEnvelope.State state = movementState;
		if (state == null) {
			return move;
		}

		double x = move.getX(0.0);
		double y = move.getY(0.0);
		double z = move.getZ(0.0);

		synchronized (movementLock) {
			boolean inGrace = System.currentTimeMillis() < teleportGraceUntil || System.currentTimeMillis() < velocityGraceUntil;
			if (!hasLastSent) {
				// First packet of the session / after a server teleport: establish the baseline.
				hasLastSent = true;
				lastSentX = x;
				lastSentY = y;
				lastSentZ = z;
				airborneTicks = move.isOnGround() ? 0 : airborneTicks + 1;
				return move;
			}

			MovementEnvelope.State effective =
				inGrace ? withGrace(state) : withAirborne(state, airborneTicks);
			MovementEnvelope.Result result =
				MovementEnvelope.clamp(x - lastSentX, y - lastSentY, z - lastSentZ, effective);

			double sentX = lastSentX + result.dx();
			double sentY = lastSentY + result.dy();
			double sentZ = lastSentZ + result.dz();
			lastSentX = sentX;
			lastSentY = sentY;
			lastSentZ = sentZ;
			airborneTicks = move.isOnGround() ? 0 : airborneTicks + 1;

			if (!result.clampedHorizontal() && !result.clampedVertical()) {
				return move;
			}
			enforcedActions.incrementAndGet();
			logOnce("movement_clamp",
				"clamped outgoing move delta to the movement envelope (dx={} dy={} dz={})",
				result.dx(), result.dy(), result.dz());
			if (move instanceof ServerboundMovePlayerPacket.PosRot) {
				return new ServerboundMovePlayerPacket.PosRot(sentX, sentY, sentZ,
					move.getYRot(0.0F), move.getXRot(0.0F), move.isOnGround(), move.horizontalCollision());
			}
			return new ServerboundMovePlayerPacket.Pos(sentX, sentY, sentZ, move.isOnGround(), move.horizontalCollision());
		}
	}

	private static MovementEnvelope.State withGrace(MovementEnvelope.State state) {
		return new MovementEnvelope.State(state.sprinting(), state.onGround(), state.airborneTicks(),
			state.speedAmplifier(), state.jumpBoostAmplifier(), state.onIce(), state.inWater(),
			state.gliding(), state.riptiding(), state.vehicle(), state.flying(), state.levitation(),
			state.climbing(), state.onBouncy(), true);
	}

	private static MovementEnvelope.State withAirborne(MovementEnvelope.State state, int airborneTicks) {
		if (state.airborneTicks() == airborneTicks) {
			return state;
		}
		return new MovementEnvelope.State(state.sprinting(), state.onGround(), airborneTicks,
			state.speedAmplifier(), state.jumpBoostAmplifier(), state.onIce(), state.inWater(),
			state.gliding(), state.riptiding(), state.vehicle(), state.flying(), state.levitation(),
			state.climbing(), state.onBouncy(), state.inGrace());
	}

	/** Per-tick snapshot of the local player's movement state (client thread only). */
	private void snapshotMovementState(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			movementState = null;
			return;
		}
		MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
		MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP_BOOST);
		String blockBelow = blockPath(player, -1);
		String blockAtFeet = blockPath(player, 0);

		movementState = new MovementEnvelope.State(
			player.isSprinting(),
			player.onGround(),
			airborneTicksSnapshot(),
			speed == null ? -1 : speed.getAmplifier(),
			jumpBoost == null ? -1 : jumpBoost.getAmplifier(),
			blockBelow != null && blockBelow.contains("ice"),
			player.isInWater(),
			player.isFallFlying(),
			player.isAutoSpinAttack(),
			player.isPassenger(),
			player.getAbilities().flying,
			player.hasEffect(MobEffects.LEVITATION),
			isClimbable(blockAtFeet) || isClimbable(blockBelow),
			blockBelow != null && (blockBelow.contains("slime") || blockBelow.contains("honey")),
			false);
	}

	private int airborneTicksSnapshot() {
		synchronized (movementLock) {
			return airborneTicks;
		}
	}

	private static String blockPath(LocalPlayer player, int yOffset) {
		var state = player.level().getBlockState(player.blockPosition().offset(0, yOffset, 0));
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	private static boolean isClimbable(String blockPath) {
		if (blockPath == null) {
			return false;
		}
		String path = blockPath;
		return path.contains("ladder") || path.contains("vine") || path.contains("scaffolding");
	}

	/** Server position sync (teleport/lagback): re-seed the baseline and open the grace window. */
	public void onServerPositionSync() {
		synchronized (movementLock) {
			hasLastSent = false;
			airborneTicks = 0;
			teleportGraceUntil = System.currentTimeMillis() + TELEPORT_GRACE_MS;
		}
	}

	/** Server-applied motion for OUR entity (knockback/explosion/piston): grace window. */
	public void onEntityMotion(int entityId, int selfEntityId) {
		if (entityId == selfEntityId) {
			synchronized (movementLock) {
				velocityGraceUntil = System.currentTimeMillis() + VELOCITY_GRACE_MS;
			}
		}
	}

	// ── 2. Exploit payload refusal ──────────────────────────────────────────────

	private Packet<?> checkSignPacket(ServerboundSignUpdatePacket sign) {
		String violation = PayloadRules.signViolation(sign.lines());
		if (violation != null) {
			enforcedActions.incrementAndGet();
			logOnce(violation, "refused outgoing sign packet ({})", violation);
			return null;
		}
		long now = System.currentTimeMillis();
		long last = lastSignAcceptedMs.get();
		if (!PayloadRules.signWithinRate(last, now)) {
			// Same player-visible outcome as the server's 1-per-2s cancel, without the VL entry.
			logOnce("sign_rate", "held outgoing sign packet inside the 2s sign-edit rate window");
			return null;
		}
		lastSignAcceptedMs.compareAndSet(last, now);
		return sign;
	}

	private Packet<?> checkBookPacket(ServerboundEditBookPacket book) {
		String violation = PayloadRules.bookViolation(book.pages(), book.title().orElse(null));
		if (violation != null) {
			enforcedActions.incrementAndGet();
			logOnce(violation, "refused outgoing book packet ({})", violation);
			return null;
		}
		return book;
	}

	// ── 3. Outgoing channel guard ───────────────────────────────────────────────

	private Packet<?> guardCustomPayload(ServerboundCustomPayloadPacket packet) {
		CustomPacketPayload payload = packet.payload();

		if (ChannelGuard.isFabricRegisterPayload(payload)) {
			Object rebuilt = ChannelGuard.filterFabricRegisterPayload(payload);
			if (rebuilt == null) {
				enforcedActions.incrementAndGet();
				logOnce("channel_register_suppressed", "suppressed a plugin-channel registration with no allowed channels left");
				return null;
			}
			if (rebuilt == payload) {
				if (ChannelGuard.isRegisterRebuildBroken()) {
					logOnce("channel_register_filter_unavailable",
						"could not filter a channel registration payload (fabric internals changed); passing through");
				}
				return packet;
			}
			enforcedActions.incrementAndGet();
			logOnce("channel_register_filtered", "removed non-allowlisted channels from the registration announcement");
			return new ServerboundCustomPayloadPacket((CustomPacketPayload) rebuilt);
		}

		String channel = payload.type().id().toString();
		if (!ChannelGuard.isChannelAllowed(channel)) {
			enforcedActions.incrementAndGet();
			logOnce("channel_blocked:" + channel, "suppressed outgoing plugin channel {} (not on the enforcement allowlist)", channel);
			return null;
		}
		return packet;
	}

	// ── Logging + test hooks ────────────────────────────────────────────────────

	private void logOnce(String type, String message, Object... args) {
		if (sessionLogged.add(type)) {
			AxolotlClientCommon.getInstance().getLogger().info("[Enforcement] " + message, args);
		}
	}

	/** Total calls into the send hook (any connection, scoped or not). Mixin-liveness evidence. */
	public long hookPulse() {
		return hookPulse.get();
	}

	/** Times the layer actually acted on a scoped connection (clamp/refusal/suppression). */
	public long enforcedActions() {
		return enforcedActions.get();
	}
}
