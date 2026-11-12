/*
   Android Session Auth Dialogs

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz
   Copyright 2026 Ibrahim Sevinc <ibrahim.sevinc.mail@gmail.com>

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
 */

package com.freerdp.freerdpcore.presentation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.freerdp.freerdpcore.R;
import com.freerdp.freerdpcore.services.LibFreeRDP;
import com.freerdp.freerdpcore.utils.ClipboardManagerProxy;

/**
 * Owns every dialog that the FreeRDP session may need: the connect-progress
 * spinner, the credentials prompt, and the certificate verification dialogs.
 * The auth/verify methods block the calling (background) thread until the user
 * dismisses the dialog on the UI thread.
 */
public class SessionDialogs
{
	/** Notified when the user cancels any of the dialogs. */
	public interface OnUserCancelListener
	{
		void onUserCancel();
	}

	/** Notified when the user cancels the connection-progress dialog. */
	public interface OnConnectCancelListener
	{
		void onConnectCancel();
	}

	private final Activity activity;
	private final OnUserCancelListener cancelListener;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	private final AlertDialog dlgVerifyCertificate;
	private final AlertDialog dlgUserCredentials;
	private final AlertDialog dlgExperimental;
	private final View userCredView;

	private AlertDialog progressDialog;
	private TextView progressMessage;
	private boolean showingReconnect;
	private AlertDialog connectionEndedDialog;

	private final Object callbackLock = new Object();
	private DialogInterface waitingDialog;
	private boolean callbackComplete;
	private boolean callbackDialogResult;
	private boolean destroyed;

	public SessionDialogs(Activity activity, OnUserCancelListener cancelListener)
	{
		this.activity = activity;
		this.cancelListener = cancelListener;

		dlgVerifyCertificate = new AlertDialog.Builder(activity)
		                           .setTitle(R.string.dlg_title_verify_certificate)
		                           .setPositiveButton(android.R.string.yes,
		                                              (dialog, which) -> {
			                                              completeDialog(dialog, true);
		                                              })
		                           .setNegativeButton(android.R.string.no,
		                                              (dialog, which) -> {
			                                              notifyCancel();
			                                              completeDialog(dialog, false);
		                                              })
		                           .setCancelable(false)
		                           .create();

		userCredView = activity.getLayoutInflater().inflate(R.layout.credentials, null, true);
		dlgUserCredentials = new AlertDialog.Builder(activity)
		                         .setView(userCredView)
		                         .setTitle(R.string.dlg_title_credentials)
		                         .setPositiveButton(android.R.string.ok,
		                                            (dialog, which) -> {
			                                            completeDialog(dialog, true);
		                                            })
		                         .setNegativeButton(android.R.string.cancel,
		                                            (dialog, which) -> {
			                                            notifyCancel();
			                                            completeDialog(dialog, false);
		                                            })
		                         .setCancelable(false)
		                         .create();

		dlgExperimental = new AlertDialog.Builder(activity)
		                      .setTitle(R.string.dlg_title_experimental_feature)
		                      .setPositiveButton(R.string.menu_app_settings,
		                                         (dialog, which) -> {
			                                         openExperimentalSettings();
			                                         completeDialog(dialog, true);
		                                         })
		                      .setNegativeButton(android.R.string.cancel,
		                                         (dialog, which) -> {
			                                         notifyCancel();
			                                         completeDialog(dialog, false);
		                                         })
		                      .setCancelable(false)
		                      .create();
	}

	/**
	 * Shows the credentials dialog and blocks until the user dismisses it.
	 * On OK the buffers are overwritten with the entered values.
	 */
	public boolean promptCredentials(StringBuilder username, StringBuilder domain,
	                                 StringBuilder password)
	{
		if (!awaitDialog(dlgUserCredentials, () -> {
			    ((EditText)userCredView.findViewById(R.id.editTextUsername)).setText(username);
			    ((EditText)userCredView.findViewById(R.id.editTextDomain)).setText(domain);
			    ((EditText)userCredView.findViewById(R.id.editTextPassword)).setText(password);
		    }))
			return false;

		username.setLength(0);
		domain.setLength(0);
		password.setLength(0);

		username.append(
		    ((EditText)userCredView.findViewById(R.id.editTextUsername)).getText().toString());
		domain.append(
		    ((EditText)userCredView.findViewById(R.id.editTextDomain)).getText().toString());
		password.append(
		    ((EditText)userCredView.findViewById(R.id.editTextPassword)).getText().toString());

		return true;
	}

	/**
	 * Shows the certificate verification dialog. Returns 1 if accepted, 0 otherwise.
	 */
	public int verifyCertificate(String host, long port, String subject, String issuer,
	                             String fingerprint, long flags)
	{
		int peerType = R.string.certificate_peer_server;
		if ((flags & LibFreeRDP.VERIFY_CERT_FLAG_GATEWAY) != 0)
			peerType = R.string.certificate_peer_gateway;
		if ((flags & LibFreeRDP.VERIFY_CERT_FLAG_REDIRECT) != 0)
			peerType = R.string.certificate_peer_redirect;
		int certificateDetails = (flags & LibFreeRDP.VERIFY_CERT_FLAG_FP_IS_PEM) != 0
		    ? R.string.certificate_pem_details
		    : R.string.certificate_fingerprint_details;
		String msg = activity.getString(R.string.dlg_msg_verify_certificate) + "\n\n" +
		             activity.getString(R.string.certificate_identity_details,
		                                activity.getString(peerType), host, Long.toString(port),
		                                subject, issuer) + "\n" +
		             activity.getString(certificateDetails, fingerprint);

		return showVerifyDialog(msg);
	}

	/**
	 * Shows the certificate-changed verification dialog. Returns 1 if accepted, 0 otherwise.
	 */
	public int verifyChangedCertificate(String host, long port, String subject, String issuer,
	                                    String fingerprint, long flags)
	{
		// The "changed" variant currently shows the same information as the
		// regular verify dialog (matches prior behaviour).
		return verifyCertificate(host, port, subject, issuer, fingerprint, flags);
	}

	/** Shows an "experimental feature" notice and blocks the calling thread until dismissed. */
	public void showExperimentalBlocked(String featureName)
	{
		awaitDialog(dlgExperimental, () -> dlgExperimental.setMessage(
		    activity.getString(R.string.dlg_msg_experimental_feature, featureName)));
	}

	private void openExperimentalSettings()
	{
		activity.startActivity(new Intent(activity, ApplicationSettingsActivity.class));
	}

	private int showVerifyDialog(String msg)
	{
		return awaitDialog(dlgVerifyCertificate, () -> dlgVerifyCertificate.setMessage(msg)) ? 1 : 0;
	}

	private boolean awaitDialog(AlertDialog dialog, Runnable prepare)
	{
		synchronized (callbackLock)
		{
			if (destroyed || waitingDialog != null)
				return false;
			waitingDialog = dialog;
			callbackComplete = false;
			callbackDialogResult = false;
			mainHandler.post(() -> {
				synchronized (callbackLock)
				{
					if (destroyed || waitingDialog != dialog)
						return;
					if (activity.isFinishing())
					{
						completeDialog(dialog, false);
						return;
					}
					prepare.run();
					dialog.show();
				}
			});
			try
			{
				while (!destroyed && !callbackComplete)
					callbackLock.wait();
				return !destroyed && callbackDialogResult;
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return false;
			}
			finally
			{
				waitingDialog = null;
			}
		}
	}

	private void completeDialog(DialogInterface dialog, boolean accepted)
	{
		synchronized (callbackLock)
		{
			if (waitingDialog != dialog)
				return;
			callbackDialogResult = accepted;
			callbackComplete = true;
			callbackLock.notifyAll();
		}
	}

	private void notifyCancel()
	{
		if (cancelListener != null)
			cancelListener.onUserCancel();
	}

	/**
	 * Builds and shows a non-blocking connect-progress dialog. Subsequent calls
	 * replace any previously shown progress dialog.
	 */
	public void showProgress(String title, final OnConnectCancelListener listener)
	{
		showProgress(title, activity.getString(R.string.dlg_msg_connecting), listener);
	}

	public void showReconnect(ConnectionDiagnostic diagnostic, OnConnectCancelListener listener)
	{
		int attempt = diagnostic == null ? 1 : diagnostic.attempt;
		String message = activity.getString(connectionReason(diagnostic, true)) + "\n\n" +
		                 activity.getString(R.string.connection_reconnect_attempt, attempt);
		if (showingReconnect && progressDialog != null && progressDialog.isShowing())
		{
			progressMessage.setText(message);
			return;
		}
		showProgress(activity.getString(R.string.connection_reconnecting_title), message, listener);
		showingReconnect = true;
	}

	public void showReconnected(ConnectionDiagnostic diagnostic)
	{
		if (diagnostic != null && diagnostic.attempt > 0)
			Toast.makeText(activity, activity.getString(R.string.connection_reconnected_message,
			                   activity.getString(connectionReason(diagnostic, true))),
			               Toast.LENGTH_LONG).show();
	}

	private void showProgress(String title, String message, OnConnectCancelListener listener)
	{
		dismissProgress();

		int pad = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
		                                         activity.getResources().getDisplayMetrics());
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.HORIZONTAL);
		content.setPadding(pad, pad, pad, pad);
		content.setGravity(Gravity.CENTER_VERTICAL);

		ProgressBar progressBar = new ProgressBar(activity);
		progressBar.setIndeterminate(true);
		content.addView(progressBar,
		                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
		                                              ViewGroup.LayoutParams.WRAP_CONTENT));

		progressMessage = new TextView(activity);
		progressMessage.setText(message);
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
		    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		textParams.leftMargin = pad;
		content.addView(progressMessage, textParams);

		progressDialog = new AlertDialog.Builder(activity)
		                     .setTitle(title)
		                     .setView(content)
		                     .setNegativeButton(R.string.cancel,
		                                        (dialog, which) -> {
			                                        if (listener != null)
				                                        listener.onConnectCancel();
		                                        })
		                     .setCancelable(false)
		                     .create();
		progressDialog.show();
	}

	/** Dismisses the connect-progress dialog if it is currently shown. */
	public void dismissProgress()
	{
		if (progressDialog != null)
		{
			progressDialog.dismiss();
			progressDialog = null;
		}
		progressMessage = null;
		showingReconnect = false;
	}

	public void showConnectionEnded(boolean wasConnected, ConnectionDiagnostic diagnostic,
	                                Runnable retry, Runnable close)
	{
		dismissConnectionEnded();
		String message = activity.getString(connectionReason(diagnostic, wasConnected));
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
		    .setTitle(wasConnected ? R.string.connection_ended_title : R.string.connection_failed_title)
		    .setMessage(message)
		    .setNegativeButton(R.string.connection_close, (dialog, which) -> close.run())
		    .setCancelable(false);
		if (retry != null)
			builder.setPositiveButton(R.string.connection_retry, (dialog, which) -> retry.run());
		if (diagnostic != null)
			builder.setNeutralButton(R.string.connection_copy_diagnostics, null);
		connectionEndedDialog = builder.show();
		if (diagnostic != null)
		{
			// Leave Retry and Close available after copying the details.
			connectionEndedDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
				ClipboardManagerProxy.getClipboardManager(activity)
				    .setClipboardData(message + "\n\n" + diagnostic.toShareText());
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
					Toast.makeText(activity, R.string.connection_diagnostics_copied,
					               Toast.LENGTH_SHORT).show();
			});
		}
	}

	private static int connectionReason(ConnectionDiagnostic diagnostic, boolean wasConnected)
	{
		if (diagnostic != null)
		{
			switch (diagnostic.reason)
			{
				case SERVER_ENDED:
					return R.string.connection_reason_server_ended;
				case SIGNED_OUT:
					return R.string.connection_reason_signed_out;
				case IDLE_TIMEOUT:
					return R.string.connection_reason_idle_timeout;
				case TIME_LIMIT:
					return R.string.connection_reason_timeout;
				case REPLACED:
					return R.string.connection_reason_replaced;
				case ACCESS_DENIED:
					return R.string.connection_reason_access_denied;
				case AUTHENTICATION:
					return R.string.connection_reason_authentication;
				case TRANSPORT:
					return R.string.connection_reason_transport;
				case SERVER_ERROR:
					return R.string.connection_reason_server_error;
				default:
					break;
			}
		}
		return wasConnected ? R.string.connection_reason_unknown : R.string.error_connection_failure;
	}

	public void dismissConnectionEnded()
	{
		if (connectionEndedDialog != null)
		{
			connectionEndedDialog.dismiss();
			connectionEndedDialog = null;
		}
	}

	public void destroy()
	{
		synchronized (callbackLock)
		{
			destroyed = true;
			callbackLock.notifyAll();
		}
		mainHandler.removeCallbacksAndMessages(null);
		dismissProgress();
		dismissConnectionEnded();
		dlgUserCredentials.dismiss();
		dlgVerifyCertificate.dismiss();
		dlgExperimental.dismiss();
	}
}
