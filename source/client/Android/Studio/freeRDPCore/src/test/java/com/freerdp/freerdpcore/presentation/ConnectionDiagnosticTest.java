/*
   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   https://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectionDiagnosticTest
{
	@Test public void serverTakeoverWinsOverTransportFailure()
	{
		assertEquals(ConnectionDiagnostic.Reason.REPLACED, diagnostic(0x2000D, 5).reason);
		assertEquals(ConnectionDiagnostic.Reason.REPLACED, diagnostic(0x10005, 0).reason);
		assertEquals(ConnectionDiagnostic.Reason.REPLACED, diagnostic(0x10005, -1).reason);
	}

	@Test public void separatesTransportFromUnspecifiedAndServerErrors()
	{
		assertEquals(ConnectionDiagnostic.Reason.TRANSPORT, diagnostic(0x2000D, 0).reason);
		assertEquals(ConnectionDiagnostic.Reason.UNKNOWN, diagnostic(0, 0).reason);
		assertEquals(ConnectionDiagnostic.Reason.UNKNOWN, diagnostic(0x20006, -1).reason);
		assertEquals(ConnectionDiagnostic.Reason.SERVER_ERROR, diagnostic(0x2000D, 0x112F).reason);
	}

	@Test public void genericRetryFailureKeepsEarlierCauseAndFinalCode()
	{
		ConnectionDiagnostic ended = new ConnectionDiagnostic(ConnectionDiagnostic.Result.FAILED,
		    0, 2, false, 0, 0x20006, 0, ConnectionDiagnostic.Reason.TRANSPORT);
		assertEquals(ConnectionDiagnostic.Reason.TRANSPORT, ended.reason);
		assertEquals(0x20006, ended.freeRdpError);
		assertTrue(ended.toShareText().contains("Reason: TRANSPORT"));
		assertTrue(ended.toShareText().contains("FreeRDP error: 0x00020006"));
	}

	@Test public void distinguishesServerTimeoutsAndSignOut()
	{
		assertEquals(ConnectionDiagnostic.Reason.IDLE_TIMEOUT, diagnostic(0, 3).reason);
		assertEquals(ConnectionDiagnostic.Reason.TIME_LIMIT, diagnostic(0, 4).reason);
		assertEquals(ConnectionDiagnostic.Reason.SIGNED_OUT, diagnostic(0, 12).reason);
	}

	@Test public void copiedDetailsIncludeAllThreeFullCodes()
	{
		ConnectionDiagnostic ended = new ConnectionDiagnostic(ConnectionDiagnostic.Result.DISCONNECTED,
		    0, 2, false, 0xC000020D, 0x0002000D, 0xFFFFFFFF);
		String details = ended.toShareText();
		assertTrue(details.contains("WinPR status: 0xC000020D"));
		assertTrue(details.contains("FreeRDP error: 0x0002000D"));
		assertTrue(details.contains("Server error info: 0xFFFFFFFF"));
	}

	private static ConnectionDiagnostic diagnostic(int freeRdpError, int serverError)
	{
		return new ConnectionDiagnostic(ConnectionDiagnostic.Result.FAILED, 0, 0, false, 0,
		                                freeRdpError, serverError);
	}
}
