package me.disconnect.mobile.vpn;

import java.net.InetAddress;
import java.net.UnknownHostException;

import me.disconnect.mobile.DisconnectMobileConfig;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;

/*
 * Detects if the securefi app is installed, and if it's VPN
 * is currently connected. 
 */
public class SecureFiDetector extends AsyncTask<Void, Void, Boolean> {
	public interface SecureFiObserver {
		void secureFiActive(Boolean aIsActive);
	}
	
	public static void discoverIfActiveAsync(Context aContext, SecureFiObserver aObserver){
		SecureFiDetector detector = new SecureFiDetector(aContext, aObserver);
		detector.execute();
	}
	
	private Context mContext;
	private SecureFiObserver mObserver;
	
	private SecureFiDetector(Context aContext, SecureFiObserver aObserver) {
		mContext = aContext;
		mObserver = aObserver;
	}
	
	@Override
	protected Boolean doInBackground(Void... params) {
		if ( !isInstalled() ){
			return false;
		}
		
		String dnsForHost = getDNSForHostName(DisconnectMobileConfig.HOST_NAME);
		
		return dnsForHost != null && dnsForHost.equals(DisconnectMobileConfig.IP_ADDRESS_IF_BLOCKED);
	}
	
	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);
		mObserver.secureFiActive(result);
	}

	private String getDNSForHostName(String aHostName){
		InetAddress address = null;
		try {
			address = InetAddress.getByName(aHostName);
		} catch (UnknownHostException e) {
			// Couldn't find host name
			address = null;
		}
		
		return address == null ? null : address.getHostAddress();
	}

	private boolean isInstalled() {
		android.content.pm.PackageManager mPm = mContext.getPackageManager();
		PackageInfo info;
		try {
			info = mPm.getPackageInfo(DisconnectMobileConfig.ALTERNATIVE_APP_PACKAGE_NAME, 0);
		} catch (NameNotFoundException e) {
			// if not found
			info = null;
		}
		
		boolean installed = (info != null);
		return installed;
	}
}
