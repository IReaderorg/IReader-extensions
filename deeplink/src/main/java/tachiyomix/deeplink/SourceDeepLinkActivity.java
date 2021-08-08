package tachiyomix.deeplink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class SourceDeepLinkActivity extends Activity {

  private static final String TAG = "SourceDeepLinkActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent forward = new Intent(Intent.ACTION_VIEW);
    forward.setData(Uri.parse(String.format(
            "tachiyomi://deeplink/%s?data=%s",
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
