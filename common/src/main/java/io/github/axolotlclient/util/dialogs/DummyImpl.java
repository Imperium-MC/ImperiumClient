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

package io.github.axolotlclient.util.dialogs;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class DummyImpl implements DialogProvider {
	@Override
	public CompletableFuture<Optional<Path>> save(String title, Path origin, String filter) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("No dialogs are supported"));
	}

	@Override
	public CompletableFuture<Optional<Path>> open(String title, Path origin, String filter) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("No dialogs are supported"));
	}

	@Override
	public CompletableFuture<Optional<List<Path>>> openMultiple(String title, Path origin, String filter) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("No dialogs are supported"));
	}
}
