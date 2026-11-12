/*
   SessionViewModel — exposes RDP connection state as LiveData

   Copyright 2026 Ibrahim Sevinc <ibrahim.sevinc.mail@gmail.com>

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.freerdp.freerdpcore.application.GlobalApp;
import com.freerdp.freerdpcore.data.AppDatabase;
import com.freerdp.freerdpcore.data.HistoryDatabase;
import com.freerdp.freerdpcore.domain.BookmarkBase;
import com.freerdp.freerdpcore.services.ManualBookmarkGateway;
import com.freerdp.freerdpcore.services.QuickConnectHistoryGateway;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SessionViewModel extends AndroidViewModel
{
	public enum ConnectionState
	{
		IDLE,
		CONNECTING,
		CONNECTED,
		RECONNECTING,
		RECONNECTED,
		FAILED,
		DISCONNECTED
	}

	private final MutableLiveData<ConnectionState> state =
	    new MutableLiveData<>(ConnectionState.IDLE);
	private long registeredInstance = 0;
	private GlobalApp.SessionEventListener registeredListener;
	private ConnectionDiagnostic connectionDiagnostic;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final ManualBookmarkGateway manualBookmarkGateway;
	private final QuickConnectHistoryGateway quickConnectHistoryGateway;

	public SessionViewModel(@NonNull Application application)
	{
		super(application);
		manualBookmarkGateway =
		    new ManualBookmarkGateway(AppDatabase.getInstance(application).bookmarkDao());
		quickConnectHistoryGateway =
		    new QuickConnectHistoryGateway(HistoryDatabase.getInstance(application).historyDao());
	}

	public LiveData<ConnectionState> getState()
	{
		return state;
	}

	/** Read on the main thread alongside the observed connection state. */
	@Nullable public ConnectionDiagnostic getConnectionDiagnostic()
	{
		return connectionDiagnostic;
	}

	public void register(long instance)
	{
		if (registeredListener != null && registeredInstance == instance)
			return;
		unregister();
		registeredInstance = instance;
		connectionDiagnostic = null;
		final GlobalApp.SessionEventListener listener = new GlobalApp.SessionEventListener() {
			@Override public void onConnectionSuccess()
			{
				// Some reconnects also enter PostConnect. Wait for the explicit recovery result.
				if (state.getValue() == ConnectionState.CONNECTING)
					state.setValue(ConnectionState.CONNECTED);
			}

			@Override public void onReconnectAttempt(int attempt, int freeRdpError, int serverError,
			                                          long timestampMillis)
			{
				connectionDiagnostic = new ConnectionDiagnostic(ConnectionDiagnostic.Result.RECONNECTING,
				    timestampMillis, attempt, false, 0, freeRdpError, serverError);
				state.setValue(ConnectionState.RECONNECTING);
			}

			@Override public void onReconnected(long timestampMillis)
			{
				ConnectionDiagnostic previous = currentReconnect();
				connectionDiagnostic = new ConnectionDiagnostic(ConnectionDiagnostic.Result.RECONNECTED,
				    timestampMillis, previous == null ? 0 : previous.attempt, false, 0,
				    previous == null ? 0 : (int)previous.freeRdpError,
				    previous == null ? 0 : (int)previous.serverError);
				state.setValue(ConnectionState.RECONNECTED);
			}

			@Override public void onConnectionEnded(int status, int freeRdpError, int serverError,
			                                        boolean intentional, long timestampMillis)
			{
				ConnectionDiagnostic previous = currentReconnect();
				boolean failed = !intentional &&
				    (state.getValue() == ConnectionState.CONNECTING || previous != null ||
				     status != 0 || freeRdpError != 0 || serverError != 0);
				connectionDiagnostic = new ConnectionDiagnostic(
				    failed ? ConnectionDiagnostic.Result.FAILED : ConnectionDiagnostic.Result.DISCONNECTED,
				    timestampMillis, previous == null ? 0 : previous.attempt, intentional,
				    status, freeRdpError, serverError,
				    previous == null ? ConnectionDiagnostic.Reason.UNKNOWN : previous.reason);
				state.setValue(failed ? ConnectionState.FAILED : ConnectionState.DISCONNECTED);
			}
		};
		registeredListener = listener;
		state.setValue(ConnectionState.CONNECTING);
		if (instance != 0)
			GlobalApp.registerSessionListener(instance, listener);
		else
		{
			mainHandler.post(() -> {
				if (registeredListener == listener)
					listener.onConnectionEnded(0x1F, 0, 0, false, System.currentTimeMillis());
			});
		}
	}

	@Nullable private ConnectionDiagnostic currentReconnect()
	{
		return state.getValue() == ConnectionState.RECONNECTING ? connectionDiagnostic : null;
	}

	public void unregister()
	{
		if (registeredInstance != 0 && registeredListener != null)
			GlobalApp.unregisterSessionListener(registeredInstance, registeredListener);
		registeredInstance = 0;
		registeredListener = null;
	}

	/**
	 * Loads a bookmark by id off the UI thread and delivers the result on the main thread.
	 */
	public void loadBookmarkById(long bookmarkId, @NonNull Consumer<BookmarkBase> callback)
	{
		executor.execute(() -> {
			final BookmarkBase bookmark = manualBookmarkGateway.findById(bookmarkId);
			mainHandler.post(() -> callback.accept(bookmark));
		});
	}

	/**
	 * Inserts the given hostname into quick-connect history off the UI thread (no-op on duplicate).
	 */
	public void recordQuickConnectHistory(@NonNull String hostname)
	{
		executor.execute(() -> {
			if (!quickConnectHistoryGateway.historyItemExists(hostname))
				quickConnectHistoryGateway.addHistoryItem(hostname);
		});
	}

	@Override protected void onCleared()
	{
		unregister();
		executor.shutdown();
		super.onCleared();
	}
}
