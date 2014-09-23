package me.disconnect.securefi.openvpnlib;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Vector;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConfigParser.ConfigParseError;
import de.blinkt.openvpn.core.OpenVpnService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.OpenVpnService.LocalBinder;
import de.blinkt.openvpn.core.VpnStatus.ConnectionStatus;
import de.blinkt.openvpn.core.VpnStatus.LogItem;
import de.blinkt.openvpn.core.VpnStatus.LogListener;
import de.blinkt.openvpn.core.VpnStatus.StateListener;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.IBinder;
import android.util.Log;
import me.disconnect.securefi.engine.LoggingConfig;
import me.disconnect.securefi.engine.VPNImplementation;

public class OpenVPNImplementation implements VPNImplementation, StateListener {

	private Vector<ConnectionListener> mConnectionListeners = new Vector<ConnectionListener>();
	private CONNECTION_STATE mLastConnectionState = null;
	private OpenVpnService mService;
	private Context mContext;
	private VpnProfile mVp;
	
	private static LogListener mLogListener;
	
	public OpenVPNImplementation(Context aContext){
		mContext = aContext.getApplicationContext();
		
		if ( LoggingConfig.LOGGING ){
			if ( mLogListener == null ){
				mLogListener = new LogListener(){
					@Override
					public void newLog(LogItem logItem) {
						Log.d("OPEN_VPN", logItem.getString(mContext));
					}
				};
			} else {
				VpnStatus.removeLogListener(mLogListener);
			}
			
			// Add logging to logcat
			VpnStatus.addLogListener(mLogListener);		
		}
	}
	
	@Override
	public void updateState(String state, String logmessage,
			int localizedResId, ConnectionStatus level) {

		// Convert connection status
		switch ( level ){
		case LEVEL_CONNECTED:{
			mLastConnectionState = CONNECTION_STATE.CONNECTED;
			break;
		}
		case LEVEL_VPNPAUSED:{
			// TODO check mapping
			mLastConnectionState = CONNECTION_STATE.DISABLED;
			break;
		}
		case LEVEL_CONNECTING_SERVER_REPLIED:
		case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
		case LEVEL_WAITING_FOR_USER_INPUT:{
			mLastConnectionState = CONNECTION_STATE.CONNECTING;
			break;
		}
		case LEVEL_NONETWORK:
		case LEVEL_NOTCONNECTED:
		case LEVEL_AUTH_FAILED:{
			mLastConnectionState = CONNECTION_STATE.DISABLED;
			break;
		}
		case UNKNOWN_LEVEL:
		default:{
			// TODO check mapping
			mLastConnectionState = CONNECTION_STATE.DISABLED;
		}
		}
		
		fireStateChanged();
	}

	@Override
	public void addStateListener(ConnectionListener sl) {
		VpnStatus.addStateListener(this);
		
		mConnectionListeners.add(sl);
		sl.stateChanged(mLastConnectionState);
	}

	@Override
	public void removeStateListener(ConnectionListener sl) {
		mConnectionListeners.remove(sl);
	}

	@Override
	public void fireStateChanged() {
		
		for(ConnectionListener sl : mConnectionListeners)
            sl.stateChanged(mLastConnectionState);
	}

	@Override
	public CONNECTION_STATE getConnectionState() {
		
		if ( mLastConnectionState == null){
			return null;
		}
		
		return mLastConnectionState;
	}
	
	@Override
	public void connect(String username, String password, String aGateway, int[] aPorts, String aCrtCert, String aTaCert, String aClientCert, String aClientKey, String aConfigData) {
		ConfigParser cp = new ConfigParser();
    	StringReader configReader = new StringReader(aConfigData);

        try {
			cp.parseConfig(configReader);
			mVp = cp.convertProfile();
			mVp.mCaFilename = VpnProfile.INLINE_TAG + aCrtCert;
			mVp.mTLSAuthFilename = VpnProfile.INLINE_TAG + aTaCert;
			mVp.mTLSAuthDirection = "1";
			mVp.mUseTLSAuth=true;
			mVp.mUsername = username;
			mVp.mPassword = password;
			
			// HARD CODE SERVER AND PORT FOR TESTING
			mVp.mServerName = aGateway;
			mVp.mServerPorts = aPorts;
			
			// Hard-code ciphers.
			mVp.mCipher = "AES-256-CBC";
			mVp.mTLSCipher = "TLS-DHE-RSA-WITH-AES-256-CBC-SHA";
			mVp.mExpectTLSCert = true;
			mVp.mClientCertFilename = VpnProfile.INLINE_TAG + aClientCert;
			mVp.mClientKeyFilename = VpnProfile.INLINE_TAG + aClientKey;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ConfigParseError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
                
		startVpnService();
	}	

	@Override
	public void disconnect() {
		// TODO implement as pause and resume.
		Intent intent = new Intent(mContext, OpenVpnService.class);
		intent.setAction(OpenVpnService.DISCONNECT_VPN);
		mContext.startService(intent);
		
//		if (mService != null && mService.getManagement() != null){
//			mService.getManagement().stopVPN();
//		}      
	}

	@Override
	public void init(Context context) {
		Intent intent = new Intent(context, OpenVpnService.class);
        intent.setAction(OpenVpnService.START_SERVICE);

        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void endit(Context context) {
		context.unbindService(mConnection);   
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }

    };
    
    /**
     * Starts the VpnService. If this succeeds the current VPN profile is started.
     * @param profileInfo a bundle containing the information about the profile to be started
     */
    private boolean startVpnService() {
        Intent permIntent;
        try {
            permIntent = VpnService.prepare(mContext);
        }
        catch (IllegalStateException ex) {
            // TODO: figure out a better way to show errors.
                /* this happens if the always-on VPN feature (Android 4.2+) is activated */
//                VpnNotSupportedError.showWithMessage(this, R.string.vpn_not_supported_during_lockdown);
            return false;
        }
        SharedPreferences prefs = mContext.getSharedPreferences(VPN_PREFS, Context.MODE_PRIVATE);
        boolean getting_permission = prefs.getBoolean(KEY_GETTING_PERMISSION, false);
        if (permIntent == null)  {
            /* User has already granted permission to use VpnService */
        	launchOpenVpn();
        }
        else if( ! getting_permission) {
            // We must request permission by launching the intent given to us above
            // and then start the service once we have it.
            try {
                boolean revoked = prefs.getBoolean(KEY_PERMISSION_REVOKED, true);
                prefs.edit().putBoolean(KEY_GETTING_PERMISSION, true).commit();
                getPermission(permIntent);
            }
            catch (ActivityNotFoundException ex) {
                    /* it seems some devices, even though they come with Android 4,
                     * don't have the VPN components built into the system image.
                     * com.android.vpndialogs/com.android.vpndialogs.ConfirmDialog
                     * will not be found then */
//                    VpnNotSupportedError.showWithMessage(this, R.string.vpn_not_supported);
                return false;
            }
        }
        return true;
    } // end startVpnService()
    
    /**
     * Starts an invisible activity using the system's given permission intent
     * stuffed into the intent as an extra named with the string SecureWireless.LAUNCH_THIS.
     * The activity will launch that intent and if the user revokes permission
     * their "secure_me" shared preference will be turned off.
     */
    private void getPermission(Intent permIntent) {
    	String messageActivity = "me.disconnect.mobile.InvisibleActivity";
    		
        Intent intent = new Intent();
        intent.setClassName(mContext, messageActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LAUNCH_THIS, permIntent);
        mContext.startActivity(intent);
    }
    
    private void launchOpenVpn(){
        try {            
            ProfileManager.setTemporaryProfile(mVp);
            VPNLaunchHelper.startOpenVpn(mVp, mContext);
            
            // TODO make sure error conditions are reported back
            mLastConnectionState = CONNECTION_STATE.CONNECTING;
            fireStateChanged();

        } catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void stopProvider() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void networkChanged() {
		Intent intent = new Intent(mContext, OpenVpnService.class);
		intent.setAction(OpenVpnService.NETWORK_CHANGED);
		mContext.startService(intent);	
	}
}
