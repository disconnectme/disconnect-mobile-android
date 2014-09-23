package me.disconnect.mobile.vpn;

import java.math.BigInteger;
import java.security.SecureRandom;

import me.disconnect.mobile.DisconnectMobileConfig;
import me.disconnect.mobile.DisconnectMobilePrefs;
import me.disconnect.mobile2.R;
import me.disconnect.securefi.engine.VPNImplementation;
import android.content.Context;
import android.util.SparseIntArray;

public class VpnManager {
	private static SecureRandom mSecureRandom = new SecureRandom();
	private Context mContext;
	private VPNImplementation mVPNImplementation;
	private DisconnectMobilePrefs mPrefs;
	
	private final static SparseIntArray mPortMap =  new SparseIntArray() {
		{
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED, 80 );
		append( DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, 1190 );
		append( DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED, 1254 );
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED | DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, 1192 );
		append( DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED | DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, 1191 );
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED | DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED, 1196 );
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED | DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED | DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, 443 );
		}
	};
	
	private final static SparseIntArray mMessageMap =  new SparseIntArray() {
		{
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED, R.string.vpn_connected_basic );
		append( DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, R.string.vpn_connected_ads );
		append( DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED, R.string.vpn_connected_malware );
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED | DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, R.string.vpn_connected_basic_ads );
		append( DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED | DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, R.string.vpn_connected_ads_malware);
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED | DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED, R.string.vpn_connected_basic_malware);
		append( DisconnectMobilePrefs.BASIC_PACKAGE_ACTIVATED | DisconnectMobilePrefs.MALWARE_PACKAGE_ACTIVATED | DisconnectMobilePrefs.ADS_PACKAGE_ACTIVATED, R.string.vpn_connected_basic_ads_malware );
		}
	};
	
	
	public VpnManager(Context aContext){
		mContext = aContext;
		mVPNImplementation = VPNProvider.getInstance(aContext);
		mPrefs = new DisconnectMobilePrefs(aContext);
	}
	
	public void connectVPN(){
		// Set connected message
		setProtectedMessage();
		
		// Use your username logic here - randomized for example		
		String username = mPrefs.generatedUsername();
		if(username == null){
			username = generateRandomString();
			mPrefs.setGeneratedUsername(username);
		}

		// This generates a random password - insert your password here
		String password = generateRandomString();

		String crtCert = mContext.getString(R.string.ca_cert);
		String taCert = mContext.getString(R.string.ta_cert);
		String clientCert = mContext.getString(R.string.client_cert);
		String genericClientKey = mContext.getString(R.string.non_priv_key);
		String configFile = mContext.getString(R.string.config_file);
		String gateway = DisconnectMobileConfig.SERVER_GATEWAY;
		int[] ports = findPortForPackages();

		mVPNImplementation.connect(username, password, gateway, ports, crtCert, taCert, clientCert, genericClientKey, configFile);
	}
	
	private int[] findPortForPackages() {
		int packageBitFlags = mPrefs.getAvailablePackages();
		return new int[]{mPortMap.get(packageBitFlags)};
	}

	private String generateRandomString() {
		return new BigInteger(130, mSecureRandom).toString(32);
	}
	
	private void setProtectedMessage(){
		int packageBitFlags = mPrefs.getAvailablePackages();
		int resourceId = mMessageMap.get(packageBitFlags);
		String connectedMessage = mContext.getString(resourceId);
		mPrefs.setProtectedMessage(connectedMessage);
	}
	

	public void disconnectVPN(){
		mVPNImplementation.disconnect();
	}
}
