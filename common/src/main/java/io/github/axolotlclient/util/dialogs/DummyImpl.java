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
