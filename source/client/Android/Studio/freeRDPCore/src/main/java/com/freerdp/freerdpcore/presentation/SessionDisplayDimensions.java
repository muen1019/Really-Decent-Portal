/*
   Session display dimensions

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.app.Activity;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.freerdp.freerdpcore.domain.BookmarkBase;

/** Automatic uses full display pixels; Fit Screen uses the visible session viewport. */
final class SessionDisplayDimensions
{
	private SessionDisplayDimensions()
	{
	}

	static Point resolve(Activity activity, BookmarkBase.ScreenSettings settings, int viewportWidth,
	                     int viewportHeight)
	{
		if (settings.isAutomatic())
			return fullDisplaySize(activity, viewportWidth, viewportHeight);
		if (settings.isFitScreen())
			return new Point(Math.max(1, viewportWidth), Math.max(1, viewportHeight));
		return new Point(settings.getWidth(), settings.getHeight());
	}

	@SuppressWarnings("deprecation")
	static Point fullDisplaySize(Activity activity, int fallbackWidth, int fallbackHeight)
	{
		WindowManager manager = activity.getWindowManager();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
		{
			// Maximum bounds describe this Activity display's fullscreen area, in current
			// orientation. Insets are deliberately not deducted; density/DIP is not applied.
			Rect bounds = manager.getMaximumWindowMetrics().getBounds();
			if (bounds.width() > 0 && bounds.height() > 0)
				return new Point(bounds.width(), bounds.height());
		}
		return legacyDisplaySize(manager, fallbackWidth, fallbackHeight);
	}

	@SuppressWarnings("deprecation")
	static Point legacyDisplaySize(WindowManager manager, int fallbackWidth, int fallbackHeight)
	{
		// API 29 equivalent: real metrics include system decorations and current rotation.
		DisplayMetrics metrics = new DisplayMetrics();
		manager.getDefaultDisplay().getRealMetrics(metrics);
		if (metrics.widthPixels > 0 && metrics.heightPixels > 0)
			return new Point(metrics.widthPixels, metrics.heightPixels);
		return new Point(Math.max(1, fallbackWidth), Math.max(1, fallbackHeight));
	}
}
