package me.disconnect.mobile.vpn;

import java.sql.Date;
import java.util.Calendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.disconnect.mobile.packages.Provisioner;
import me.disconnect.mobile2.R;
import me.disconnect.mobile.DisconnectMobilePrefs;
import me.disconnect.securefi.engine.VPNImplementation;

public class ConnectivityReceiver extends BroadcastReceiver {
	
	// Notification ID for update/Disconnect notifications
	private Integer UPDATE_NOTIFICATION_ID = 11297116;
	
	private Boolean performingAsyncTask = false;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		handleOnReceive(context);
	}

	private void handleOnReceive(Context context){
		ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();
		VPNImplementation.CONNECTION_STATE connection_state = VPNProvider.getInstance(context).getConnectionState();
		if (activeInfo != null && activeInfo.isConnected()) {
			DisconnectMobilePrefs prefs = new DisconnectMobilePrefs(context);
			// User needs to reauthorize us to take over VPN after reboots and we need to bring
			// up the app to allow that. We just need to not do that when "secure me" is off.
			// See also http://devmaze.wordpress.com/2011/12/05/activating-applications/
			boolean secure_me = prefs.isProtected();
			if ( secure_me ){
				if(connection_state == null ) {
					showNotification(context.getResources().getString(R.string.vpn_notification_disconnected), "Disconnect", "me.disconnect.mobile.MainActivity", context, VPNImplementation.NOTIFICATION_ID, false);
					
					// We're not connected, try to connect
					VPNProvider.getInstance(context).disconnect();
					VpnManager vpnManager = new VpnManager(context);
					vpnManager.connectVPN();
					
					return;
				}

				if(connection_state == VPNImplementation.CONNECTION_STATE.DISABLED ) {
					VpnManager vpnManager = new VpnManager(context);
					vpnManager.connectVPN();
						//TODO - provide option to disable notification
						NotificationManager notificationManager =
				                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
						notificationManager.cancel(VPNImplementation.NOTIFICATION_ID);
				} else {
					// Notify provider that the network has changed.
					//VPNProvider.getInstance(context).networkChanged();
					// Restarts the VPN connection if network has changed
						VPNProvider.getInstance(context).disconnect();
						VpnManager vpnManager = new VpnManager(context);
						vpnManager.connectVPN();

						//TODO - provide option to disable notification
						NotificationManager notificationManager =
				                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
						notificationManager.cancel(VPNImplementation.NOTIFICATION_ID);
				}
			}
		} else {
			// Used to disconnect the VPN if the user has no connectivity
			if(connection_state == VPNImplementation.CONNECTION_STATE.CONNECTED
				|| connection_state == VPNImplementation.CONNECTION_STATE.CONNECTING)
			VPNProvider.getInstance(context).disconnect();

			// Try to reconnect afterward
			VpnManager vpnManager = new VpnManager(context);
			vpnManager.connectVPN();
		}

		VPNProvider.getInstance(context).fireStateChanged(); // TODO: Fire method should be protected.
		checkNotification(context);
	}
	
	private void checkNotification(final Context context) {
		DisconnectMobilePrefs prefs = new DisconnectMobilePrefs(context);
		String url = "https://your-endpoint.com/notification?version=" + getVersion(context);
		// Only check for a notification once per day
		Boolean showNotification = timeToCheckNotification(prefs.getDateNotificationChecked());
		
		if (performingAsyncTask == false && showNotification) {
			prefs.setDateNotificationChecked(Calendar.getInstance());
			performingAsyncTask = true;
	        new AsyncTask<String, Void, JSONArray>() {
	            @Override
	            protected JSONArray doInBackground(String... params) {
	            	try {
	            		return Provisioner.refresh(params);
	            	}
	            	catch (Exception e) {
	            		return null;
	            	}
	            }
	            @Override
	            protected void onPostExecute(JSONArray result) {
	                if(result != null){
	                    handleNotificationJSON(result, context);
	                } else {
	                	//TODO
	                }
	                performingAsyncTask = false;
	            }
	        }.execute(url);
		}
	}

	private void handleNotificationJSON(JSONArray result, Context context) {
		DisconnectMobilePrefs prefs = new DisconnectMobilePrefs(context);
		JSONObject jsonObject;

		// TODO Add a 24-hour keepalive
		// TODO Remember what the last notification was
		try {
			jsonObject = result.getJSONObject(0);
			String identifier = jsonObject.getString("identifier");
			if (!(prefs.notificationShown(identifier))) {

				// Set notification as shown and set as last shown notification
				prefs.setNotificationsShown(identifier);
				prefs.setLastNotificationShown(identifier);

				String title = jsonObject.getString("title");
				String text = jsonObject.getString("text");
				String url = jsonObject.getString("url");

				// Show the notification
				showNotification(text, title, url, context, UPDATE_NOTIFICATION_ID, true);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

    private void showNotification(String text, String title, String target, Context context, int notificationID, Boolean isURL) {
	    NotificationCompat.Builder notificationBuilder =
	        new NotificationCompat.Builder(context)
	    .setAutoCancel(true)
	    .setSmallIcon(R.drawable.notification_icon)
	    .setContentTitle(title)
	    .setContentText(text);

	    NotificationManager mNotificationManager =
	        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

	    Intent intent;

	    if (isURL) {
		    intent = new Intent(Intent.ACTION_VIEW);
		    intent.setData(Uri.parse(target));
	    }
	    else {
            intent = new Intent();
            intent.setClassName(context, target);
	    }

	    PendingIntent pending = PendingIntent.getActivity(context, 0, intent, Intent.FLAG_ACTIVITY_NEW_TASK);
	    notificationBuilder.setContentIntent(pending);

	    mNotificationManager.notify(notificationID, notificationBuilder.build());
    }

    private String getVersion(Context context) {
		PackageManager manager = context.getPackageManager();
		PackageInfo info;
		try {
			info = manager.getPackageInfo(
			    context.getPackageName(), 0);
			String version = info.versionName;
			return version;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
			return "VersionError";
		}
    }
    private Boolean timeToCheckNotification(Long lastChecked) {
    	Calendar today = Calendar.getInstance();
    	Calendar lastTime = Calendar.getInstance();
    	
    	lastTime.setTimeInMillis(lastChecked);
    	lastTime.add(Calendar.DATE, 1);
    	
    	return lastTime.before(today); 
    }
    
    private static boolean isNetworkAvailable(Context context) 
    {
        return ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null;
    }

//    static void recheckConnection(Context context) {
//    	SharedPreferences prefs = context.getSharedPreferences(SecureWireless.VPN_PREFS, Context.MODE_PRIVATE);
//
//        boolean secure_me = prefs.getBoolean(SecureWireless.KEY_SECURE_ME, SecureWireless.DEFAULT_SECURE_ME);
//        
//        boolean permission_revoked = prefs.getBoolean(SecureWireless.KEY_PERMISSION_REVOKED, false);
//        boolean security_needed = secure_me && !permission_revoked;
//        VPNImplementation.CONNECTION_STATE state = VPNProvider.getInstance(context).getConnectionState();
//        if(security_needed) {
//            if(VPNImplementation.CONNECTION_STATE.DISABLED.equals(state)) {
//                connectToVPN(context, prefs);
//            }
//        }
//        else { // Security not needed.
//            if(VPNImplementation.CONNECTION_STATE.CONNECTED.equals(state))
//                VPNProvider.getInstance(context).disconnect();
//        }
//    }
}
