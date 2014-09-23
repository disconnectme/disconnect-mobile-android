package me.disconnect.securefi.engine;

import android.content.Context;

/**
 * Contract for supported VPN implementations.
 */
public interface VPNImplementation {
    public final int PREPARE_VPN_SERVICE = 213;
    
    public static final String LAUNCH_THIS = "launch this";
    
    // Shared Preferences
    public static final String KEY_BANDWIDTH_LIMIT = "bandwidth limit"; // In megabytes
    public static final String KEY_BANDWIDTH_USED = "bandwidth used"; // In megabytes
    public static final String VPN_PREFS = "vpn preferences"; // SharedPreferences file name.
    public static final String KEY_GETTING_PERMISSION = "getting permission"; // semaphore to serialize the approval process.
    public static final String KEY_PERMISSION_REVOKED = "permission revoked"; // If user wants us all the way off.
    public static final String PROTECTED_MESSAGE = "VPN_CONNECTED_MESSAGE"; // Message to show when vpn is connected.
    
    // default values
    public static final float DEFAULT_BANDWIDTH_LIMIT = 10;
    // Identifies our status notification so we can update it. Can be any integer we like.
    public static final int NOTIFICATION_ID = 415;
    
    /**
     * Connection states and support for listeners to state changes.
     */
    public enum CONNECTION_STATE { DISABLED, CONNECTING, CONNECTED, DISCONNECTING }
    public interface ConnectionListener {
        public void stateChanged(CONNECTION_STATE newState);
    }
    public void addStateListener(ConnectionListener sl);
    public void removeStateListener(ConnectionListener sl);
    public void fireStateChanged(); // Normally protected method.
    public CONNECTION_STATE getConnectionState();

    /**
     * Attempts to connect to start the VPN service.
     * This requires the user to approve this action, at least initially.
     * For new connections the user will be presented with a system dialog.
     * When the user agrees, the given Activity's onActivityResult() method is called,
     * and will be passed the above PREPARE_VPN_SERVICE request code.
     * Assuming the result code is RESULT_OK, the onActivityResult() method
     * must call connect() once more to complete the connection.
     */
    public void connect(String username, String password, String aGateway, int[] aPorts, String aCrtCert, String aTaCert, String aClientCert, String aClientKey, String aConfigFile);
    public void disconnect();
    
    /*
     * Called when the connectivity receiver detects that the network has changed. It
     * is the responsibility of the provider to manage the transition to the new
     * network.
     */
    public void networkChanged();
    
    /**
     * Stops VPN provider. Stops any background servicers started by provider
     * and deregisters any observers.
     */
    public void stopProvider();
    
    // TODO Implement proper message handling back to UX
    public void init(Context context);
    public void endit(Context context);
}
