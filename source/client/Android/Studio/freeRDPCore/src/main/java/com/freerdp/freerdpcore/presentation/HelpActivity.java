/*
   Activity that displays the help pages

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.R;

public class HelpActivity extends AppCompatActivity
{
	private WebView mWebView;

	@Override public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_help);

		if (getSupportActionBar() != null)
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		mWebView = findViewById(R.id.activity_help_webview);

		WebSettings settings = mWebView.getSettings();
		settings.setDomStorageEnabled(true);
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);
		settings.setSupportZoom(true);
		settings.setJavaScriptEnabled(true);
		settings.setAllowContentAccess(true);
		settings.setAllowFileAccess(true);

		mWebView.setWebViewClient(new WebViewClient() {
			@Override public boolean shouldOverrideUrlLoading(WebView view, String url)
			{
				if (url.startsWith("file:///android_asset/"))
				{
					view.loadUrl(url);
					return true;
				}
				return false;
			}
		});

		mWebView.loadUrl("file:///android_asset/" + getString(R.string.help_page_asset));
	}

	@Override public boolean onSupportNavigateUp()
	{
		finish();
		return true;
	}
}
