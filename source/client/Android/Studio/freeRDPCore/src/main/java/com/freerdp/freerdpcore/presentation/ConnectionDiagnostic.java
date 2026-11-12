/*
   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   https://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import java.time.Instant;
import java.util.Locale;

/** A single connection result containing only allowlisted diagnostic fields. */
public final class ConnectionDiagnostic
{
	public enum Result
	{
		RECONNECTING,
		RECONNECTED,
		FAILED,
		DISCONNECTED
	}

	enum Reason
	{
		SERVER_ENDED,
		SIGNED_OUT,
		IDLE_TIMEOUT,
		TIME_LIMIT,
		REPLACED,
		ACCESS_DENIED,
		AUTHENTICATION,
		TRANSPORT,
		SERVER_ERROR,
		UNKNOWN
	}

	public final Result result;
	final Reason reason;
	public final long timestampMillis;
	public final int attempt;
	public final boolean intentional;
	public final long status;
	public final long freeRdpError;
	public final long serverError;

	ConnectionDiagnostic(Result result, long timestampMillis, int attempt, boolean intentional,
	                     int status, int freeRdpError, int serverError)
	{
		this(result, timestampMillis, attempt, intentional, status, freeRdpError, serverError,
		     Reason.UNKNOWN);
	}

	ConnectionDiagnostic(Result result, long timestampMillis, int attempt, boolean intentional,
	                     int status, int freeRdpError, int serverError, Reason fallbackReason)
	{
		this.result = result;
		this.timestampMillis = timestampMillis;
		this.attempt = Math.max(attempt, 0);
		this.intentional = intentional;
		this.status = Integer.toUnsignedLong(status);
		this.freeRdpError = Integer.toUnsignedLong(freeRdpError);
		this.serverError = Integer.toUnsignedLong(serverError);
		Reason reported = classify(freeRdpError, serverError);
		this.reason = reported == Reason.UNKNOWN ? fallbackReason : reported;
	}

	private static Reason classify(int freeRdpError, int serverError)
	{
		// MS-RDPBCGR Set Error Info is authoritative. FreeRDP can also wrap it in class 1.
		if ((serverError == 0 || serverError == -1) && (freeRdpError >>> 16) == 1)
			serverError = freeRdpError & 0xFFFF;
		switch (serverError)
		{
			case 0:
			case -1: // ERRINFO_NONE
				break;
			case 1:  // ERRINFO_RPC_INITIATED_DISCONNECT
			case 11: // ERRINFO_RPC_INITIATED_DISCONNECT_BY_USER
				return Reason.SERVER_ENDED;
			case 2:  // ERRINFO_RPC_INITIATED_LOGOFF
			case 12: // ERRINFO_LOGOFF_BY_USER
				return Reason.SIGNED_OUT;
			case 3:  // ERRINFO_IDLE_TIMEOUT
				return Reason.IDLE_TIMEOUT;
			case 4:  // ERRINFO_LOGON_TIMEOUT
				return Reason.TIME_LIMIT;
			case 5:  // ERRINFO_DISCONNECTED_BY_OTHER_CONNECTION
				return Reason.REPLACED;
			case 7:  // ERRINFO_SERVER_DENIED_CONNECTION
			case 9:  // ERRINFO_SERVER_INSUFFICIENT_PRIVILEGES
				return Reason.ACCESS_DENIED;
			case 10: // ERRINFO_SERVER_FRESH_CREDENTIALS_REQUIRED
				return Reason.AUTHENTICATION;
			default:
				return Reason.SERVER_ERROR;
		}
		switch (freeRdpError)
		{
			case 0x00020009: // FREERDP_ERROR_AUTHENTICATION_FAILED
			case 0x00020014: // FREERDP_ERROR_CONNECT_LOGON_FAILURE
			case 0x00020015: // FREERDP_ERROR_CONNECT_WRONG_PASSWORD
				return Reason.AUTHENTICATION;
			case 0x0002000D: // FREERDP_ERROR_CONNECT_TRANSPORT_FAILED
				return Reason.TRANSPORT;
			default:
				return Reason.UNKNOWN;
		}
	}

	public String toShareText()
	{
		return String.format(Locale.ROOT,
	    "Really Decent Portal connection diagnostic\nTime (UTC): %s\nResult: %s\nReason: %s\n" +
		    "Reconnect attempts: %d\nLocal disconnect: %s\nWinPR status: 0x%08X\n" +
		    "FreeRDP error: 0x%08X\nServer error info: 0x%08X",
	    Instant.ofEpochMilli(timestampMillis), result, reason, attempt, intentional,
		    status, freeRdpError, serverError);
	}
}
