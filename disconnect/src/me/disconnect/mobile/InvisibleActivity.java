package me.disconnect.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import me.disconnect.mobile2.R;
import me.disconnect.mobile.vpn.VpnManager;
import me.disconnect.securefi.engine.LoggingConfig;
import me.disconnect.securefi.engine.VPNImplementation;

/**
 * An invisible activity intended to only live long enough to display toast messages or start an Activity.
 * If the "Message" key is found in the intent, a Toast is shown with that key's value.
 * If the SecureWireless.LAUNCH_THIS key is found, the intent value will be launched via startActivityForResult().
 */
public class InvisibleActivity extends Activity {
    public static final String USER_CANCELLED = "user_cancelled_connection";

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String msg = getIntent().getStringExtra("Message");
        if(msg != null) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            finish();
        }
        else if(getIntent().getParcelableExtra(VPNImplementation.LAUNCH_THIS) != null) {
            if(getSharedPreferences(VPNImplementation.VPN_PREFS, MODE_PRIVATE).getBoolean(VPNImplementation.KEY_PERMISSION_REVOKED, false))
                finish(); // User has opted completely out of Secure Wireless.
            else
                showPrePermissionDialog();
        }
    }

    private void showPrePermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.permission_instructions));
        builder.setPositiveButton("OK", new Dialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The user saw the dialog text and presumably understands that they are about to
                // see the scary system VPN dialog and are ready to approve it.
                Intent permIntent = getIntent().getParcelableExtra(VPNImplementation.LAUNCH_THIS);
                startActivityForResult(permIntent, VPNImplementation.PREPARE_VPN_SERVICE);
                dialog.cancel();
            }
        });
        builder.setNegativeButton("No", new Dialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The user was presented with the system dialog asking them to approve our taking
                // over of the VPN, and they hit cancel. Why would they cancel when they initiated
                // this process by turning on Secure Wireless? It doesn't matter. If we don't turn
                // it off in that case they will be stuck in an endless loop where their only way
                // out would be to approve it. That is the purpose of the pre-permission dialog.
                SharedPreferences.Editor edit = getSharedPreferences(VPNImplementation.VPN_PREFS, MODE_PRIVATE).edit();
                edit.putBoolean(VPNImplementation.KEY_PERMISSION_REVOKED, true);
                edit.putBoolean(DisconnectMobilePrefs.PROTECTION_ON, false); // Forcing master switch. OK because user said "No".
                edit.putBoolean(VPNImplementation.KEY_GETTING_PERMISSION, false);
                edit.commit();
                dialog.cancel();
                
                Intent intent = new Intent(USER_CANCELLED);
                LocalBroadcastManager.getInstance(InvisibleActivity.this).sendBroadcast(intent);
                
                finish();
            }
        });
        builder.show();
    }

    // This receives the results from when the user has been asked to approve this VPN app.
    // Subsequent attempts to connect to the VPN will succeed.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SharedPreferences.Editor editor = getSharedPreferences(VPNImplementation.VPN_PREFS, MODE_PRIVATE).edit();
        
        boolean reLaunchVPN = false;
        if(requestCode == VPNImplementation.PREPARE_VPN_SERVICE) {
            if(resultCode == 0) {
                // Forcing master switch. OK because user hit "Cancel".
                editor.putBoolean(DisconnectMobilePrefs.PROTECTION_ON, false);
                Intent intent = new Intent(USER_CANCELLED);
                LocalBroadcastManager.getInstance(InvisibleActivity.this).sendBroadcast(intent);
                
            } else if(resultCode == -1) {
                // User approved. We're good to go.
                editor.putBoolean(VPNImplementation.KEY_PERMISSION_REVOKED, false);
                
                // Start the vpn
                reLaunchVPN = true;
            }
        }     
        editor.putBoolean(VPNImplementation.KEY_GETTING_PERMISSION, false);
        editor.commit();
        finish();
        
        if ( reLaunchVPN ){
        	VpnManager manager = new VpnManager(InvisibleActivity.this);
			manager.connectVPN();
        }
    }
}
