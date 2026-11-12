/*
   Activity that displays the about page

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.R;
import com.freerdp.freerdpcore.services.LibFreeRDP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class AboutActivity extends AppCompatActivity
{
	private static final String TAG = AboutActivity.class.toString();
	private static final String ASSET_BASE = "file:///android_asset/";
	private WebView mWebView;

	@Override protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		if (getSupportActionBar() != null)
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		mWebView = findViewById(R.id.activity_about_webview);

		WebSettings settings = mWebView.getSettings();
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);
		settings.setSupportZoom(true);

		mWebView.setWebViewClient(new WebViewClient() {
			@Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
			{
				if (request.getUrl().toString().startsWith(ASSET_BASE))
					return false;
				if (request.isForMainFrame() && "https".equals(request.getUrl().getScheme()))
				{
					try
					{
						startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
					}
					catch (ActivityNotFoundException e)
					{
						Log.w(TAG, "Could not open link", e);
					}
				}
				return true;
			}
		});

		populate();
	}

	@Override public boolean onSupportNavigateUp()
	{
		finish();
		return true;
	}

	private void populate()
	{
		String file = getString(R.string.about_page_asset);

		StringBuilder total = new StringBuilder();
		try (BufferedReader r = new BufferedReader(
		         new InputStreamReader(getAssets().open(file), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				total.append(line);
				total.append("\n");
			}
		}
		catch (IOException e)
		{
			Log.e(TAG, "Could not read about page " + file, e);
		}

		String version;
		try
		{
			String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
			version = name != null ? name : "—";
		}
		catch (PackageManager.NameNotFoundException e)
		{
			version = "—";
		}

		final String html = total.toString()
		                        .replace("%APP_VERSION%", TextUtils.htmlEncode(version))
		                        .replace("%FREERDP_VERSION%", TextUtils.htmlEncode(LibFreeRDP.getVersion()));

		mWebView.loadDataWithBaseURL(ASSET_BASE + file, html, "text/html", "UTF-8", null);
	}
}
