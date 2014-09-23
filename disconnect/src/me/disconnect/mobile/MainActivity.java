package me.disconnect.mobile;

import me.disconnect.mobile2.R;
import me.disconnect.mobile.billing.BillingHelper;
import me.disconnect.mobile.packages.PackageDescriptionManager;
import me.disconnect.securefi.engine.VPNImplementation;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new FrontScreenFragment()).commit();
		}
		
		// Get package information
		PackageDescriptionManager.getInstance().getDescriptionsAsync();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		
		switch ( id ){
		case R.id.about:{
			InfoActivity.show(this, R.string.about, R.string.about_page);
			break;
		}
		case R.id.privacy_policy:{
			InfoActivity.show(this, R.string.privacy_policy, R.string.privacy_page_text);
			break;
		}
		case R.id.basic_privacy_pack:{
			InfoActivity.show(this, R.string.basic_pack, R.string.basic_privacy_pack_text);
			break;
		}
		case R.id.advertising_filter_pack:{
			InfoActivity.show(this, R.string.ad_pack, R.string.advertising_pack_text);
			break;
		}
		case R.id.malware_protection_pack:{
			InfoActivity.show(this, R.string.malware_pack, R.string.malware_pack_text);
			break;
		}
		case R.id.send_feedback:{
			PackageInfo pinfo;
			String version = "0.0.0";
			try {
				pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				version = pinfo.versionName;
			} catch (Exception e) {
				e.printStackTrace();
			}
			final Intent sendIntent = new Intent(Intent.ACTION_SEND);
			sendIntent.setType("plain/text");
			sendIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{DisconnectMobileConfig.FEEDBACK_EMAIL_ADDRESS});
			sendIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.feed_back_email_subject) + " - " + version);
			startActivity(Intent.createChooser(sendIntent, "Send mail..."));
		}
		}

		return super.onOptionsItemSelected(item);
	}
	
	// This is here only for when the user has been asked to approve this VPN app.
    // Calling connect again will succeed. It seems really wonky to require an Activity
    // simply to get the result of the system dialog, but Chiu-Ki says this is the right pattern.
    // Note: currently using InvisibleActivity for this so this version will not be called.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == VPNImplementation.PREPARE_VPN_SERVICE) {
       //     if(resultCode == RESULT_OK)
       //         ConnectivityReceiver.recheckConnection(this);
       //     else // Forcing master switch. OK because user hit "Cancel".
        //        getSharedPrefs().edit().putBoolean(SecureWireless.KEY_SECURE_ME, false).commit();
        } else if ( requestCode == BillingHelper.REQUEST_CODE  || requestCode == 1337){
        	Fragment fragment = (Fragment) getFragmentManager().findFragmentById(R.id.container);
            if(fragment != null){
                  fragment.onActivityResult(requestCode, resultCode, data);
            }
        }
    }
}
