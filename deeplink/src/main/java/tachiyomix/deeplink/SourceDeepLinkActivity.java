package tachiyomix.deeplink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

public class SourceDeepLinkActivity extends Activity {

  private static final String TAG = "SourceDeepLinkActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent forward = new Intent(Intent.ACTION_VIEW);
    forward.setData(Uri.parse(String.format(
            "tachiyomi://deeplink/%s?url=%s",
            getPackageName(),
            getIntent().getData().toString()
    )));

    try {
      startActivity(forward);
    } catch (ActivityNotFoundException e) {
      Log.e(TAG, e.toString());
    }

    finish();
    System.exit(0);
  }

}
