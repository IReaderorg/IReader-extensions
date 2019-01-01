package tachiyomix.deeplink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class SourceDeepLinkActivity extends Activity {

  private static final String TAG = "SourceDeepLinkActivity";

  static final String ACTION = "tachiyomi.action.HANDLE_LINK";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();

    Intent forward = new Intent(ACTION);
    forward.setData(intent.getData());
    forward.putExtra(Intent.EXTRA_REFERRER, getPackageName());

    try {
      startActivity(forward);
    } catch (ActivityNotFoundException e) {
      Log.e(TAG, e.toString());
    }

    finish();
    System.exit(0);
  }

}
