package de.blinkt.openvpn.core;

import android.Manifest.permission;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;

import de.blinkt.openvpn.EclipseBuildConfig;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.VpnStatus.ConnectionStatus;
import de.blinkt.openvpn.core.VpnStatus.StateListener;

import java.lang.reflect.InvocationTargetException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;

import me.disconnect.securefi.engine.VPNImplementation;
import me.disconnect.securefi.openvpnlib.R;

import static de.blinkt.openvpn.core.NetworkSpace.*;
import static de.blinkt.openvpn.core.VpnStatus.ConnectionStatus.*;

public class OpenVpnService extends VpnService implements StateListener, Callback {
    public static final String START_SERVICE = "de.blinkt.openvpn.START_SERVICE";
    public static final String START_SERVICE_STICKY = "de.blinkt.openvpn.START_SERVICE_STICKY";
    public static final String ALWAYS_SHOW_NOTIFICATION = "de.blinkt.openvpn.NOTIFICATION_ALWAYS_VISIBLE";
    public static final String DISCONNECT_VPN = "de.blinkt.openvpn.DISCONNECT_VPN";
    public static final String NETWORK_CHANGED = "de.blinkt.openvpn.NETWORK_CHANGED";

    private static boolean mNotificationAlwaysVisible = true;
    private final Vector<String> mDnslist = new Vector<String>();
    private final NetworkSpace mRoutes = new NetworkSpace();
    private final NetworkSpace mRoutesv6 = new NetworkSpace();
    private final IBinder mBinder = new LocalBinder();
    private Thread mProcessThread = null;
    private VpnProfile mProfile;
    private String mDomain = null;
    private CIDRIP mLocalIP = null;
    private int mMtu;
    private String mLocalIPv6 = null;
    private boolean mStarting = false;
    private boolean mOvpn3 = false;
    private OpenVPNManagement mManagement;
    private String mLastTunCfg;
    private String mRemoteGW;
    
    private NetworkStateManager mNetworkStateManager;
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private ConnectionStatus mLevel;

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(START_SERVICE))
            return mBinder;
        else
            return super.onBind(intent);
    }

    @Override
    public void onRevoke() {
        mManagement.stopVPN();
        endVpnService();
        
        /* the system revoked the rights grated with the initial prepare() call.
		 * called when the user clicks disconnect in the system's VPN dialog */
        // Please forgive this miserable sinner for creating code links up the stack.
        // I'll not import Secure Wireless which would make that a compiler requirement, so I'll just
        // copy those string values here. Yet another sin.
        // The need is to know when the user had revoked VPN permission via the white "key"
        // system notification, which is why we're here. Knowing that lets us not try to
        // reconnect as soon the user disconnects.
        // TODO: Refactor this into a sin-free state via a callback or something.
        SharedPreferences.Editor editor  = getSharedPreferences(VPNImplementation.VPN_PREFS, Context.MODE_PRIVATE).edit();
        editor.putBoolean("permission revoked", true);
        editor.putBoolean("secure me", false);
        editor.commit();
    }

    // Similar to revoke but do not try to stop process
    public void processDied() {
        endVpnService();
    }

    private void endVpnService() {
        mProcessThread = null;

        ProfileManager.setConntectedVpnProfileDisconnected(this);
        if (!mStarting) {
            stopForeground(!mNotificationAlwaysVisible);

            if (!mNotificationAlwaysVisible) {
                stopSelf();
                VpnStatus.removeStateListener(this);
            }
        }
    }
    
    private void showNotification(String msg) {
        if (msg.equals("cancel")) {
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(VPNImplementation.NOTIFICATION_ID);
        }
        else {
            String mainActivity = "me.disconnect.mobile.MainActivity";
            
            Notification.Builder mBuilder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getString(R.string.vpn_notification_title))
                .setContentText(msg);
            
            // Create an explicit intent for our main activity.
            Intent resultIntent = new Intent();
            resultIntent.setClassName(this, mainActivity);
            
            // The stack builder object will contain an artificial back stack for the started Activity.
            // This ensures that navigating backward from the Activity leads from app to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            // Add the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(new ComponentName(this, mainActivity));
            // Add the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            mBuilder.setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
    //        mBuilder.setLights(Color.GREEN, 500, 500);
             
            @SuppressWarnings("deprecation") // Notification.Builder build() is > API 16
            Notification notification = mBuilder.getNotification();
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // mId allows you to update the notification later on.
            mNotificationManager.notify(VPNImplementation.NOTIFICATION_ID, notification);
        }
    }
    
    private String stateToString(ConnectionStatus state) {
    	// Convert connection status
    	switch ( state ){
    	case LEVEL_CONNECTED:{
            return "cancel";
    	}
    	case LEVEL_VPNPAUSED:{
    		return getString(R.string.vpn_notification_disconnected);
    	}
    	case LEVEL_CONNECTING_SERVER_REPLIED:
    	case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
    	case LEVEL_WAITING_FOR_USER_INPUT:{
    		return getString(R.string.vpn_notification_connecting);
    	}
    	case LEVEL_NONETWORK:
    	case LEVEL_NOTCONNECTED:
    	case LEVEL_AUTH_FAILED:
    	case UNKNOWN_LEVEL:
    	default:{
    		return getString(R.string.vpn_notification_disconnected);
    	}
    	}
    }

    // TODO Change to show main activity
    PendingIntent getLogPendingIntent() {
        // Let the configure Button show the Log
    	String mainActivity = "me.disconnect.mobile.MainActivity";
		
        Intent intent = new Intent();
        intent.setClassName(this, mainActivity);

        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent startLW = PendingIntent.getActivity(this, 0, intent, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;

    }

    public void userPause(boolean shouldBePaused) {
    }
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	
    	// Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
        	 	android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_NOT_STICKY;

        if (intent.getBooleanExtra(ALWAYS_SHOW_NOTIFICATION, false))
            mNotificationAlwaysVisible = true;

        VpnStatus.addStateListener(this);
        
        if (START_SERVICE.equals(intent.getAction()))
            return START_NOT_STICKY;
        
        if (START_SERVICE_STICKY.equals(intent.getAction())) {
            return START_REDELIVER_INTENT;
        }
        
        // TODO should network change be handled differently
        // Remove any existing messages in queue
        mServiceHandler.removeCallbacksAndMessages(null);

        // Queue message
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.arg2 = flags;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        
        return START_NOT_STICKY;
    }
        
        
        private final class ServiceHandler extends Handler {
            public ServiceHandler(Looper looper) {
                super(looper);
            }
            
            @Override
            public void handleMessage(Message msg) {
            	Intent intent = (Intent)msg.obj;



            	// Disconnect VPN
            	if (DISCONNECT_VPN.equals(intent.getAction())){
            		ProfileManager.setConntectedVpnProfileDisconnected(OpenVpnService.this);
            		
            		stopRunningVPN();
            		return;
            	}

            	if ( NETWORK_CHANGED.equals(intent.getAction())){
            		// Notification network has changed.
            		if ( mNetworkStateManager != null ){
            			mNetworkStateManager.networkStateChange(getBaseContext());
            		}

            		return;
            	}

            	// Extract information from the intent.
            	String prefix = getPackageName();
            	String[] argv = intent.getStringArrayExtra(prefix + ".ARGV");
            	String nativelibdir = intent.getStringExtra(prefix + ".nativelib");
            	String profileUUID = intent.getStringExtra(prefix + ".profileUUID");

            	mProfile = ProfileManager.get(OpenVpnService.this, profileUUID);

            	showNotification(stateToString(LEVEL_CONNECTING_NO_SERVER_REPLY_YET));

            	stopRunningVPN();

            	// Start a new session by creating a new thread.
            	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(OpenVpnService.this);

            	mOvpn3 = prefs.getBoolean("ovpn3", false);
            	if (!"ovpn3".equals(EclipseBuildConfig.FLAVOR))
            		mOvpn3 = false;


            	// Open the Management Interface
            	if (!mOvpn3) {

            		// start a Thread that handles incoming messages of the managment socket
            		OpenVpnManagementThread ovpnManagementThread = new OpenVpnManagementThread(mProfile, OpenVpnService.this);
            		if (ovpnManagementThread.openManagementInterface(OpenVpnService.this)) {

            			Thread mSocketManagerThread = new Thread(ovpnManagementThread, "OpenVPNManagementThread");
            			mSocketManagerThread.start();
            			mManagement = ovpnManagementThread;
            			VpnStatus.logInfo("started Socket Thread");
            		} else {
            			return;
            		}
            	}


            	Runnable processThread;
            	if (mOvpn3) {

            		OpenVPNManagement mOpenVPN3 = instantiateOpenVPN3Core();
            		processThread = (Runnable) mOpenVPN3;
            		mManagement = mOpenVPN3;


            	} else {
            		HashMap<String, String> env = new HashMap<String, String>();
            		processThread = new OpenVPNThread(OpenVpnService.this, argv, env, nativelibdir);
            	}

            	mProcessThread = new Thread(processThread, "OpenVPNProcessThread");
            	mProcessThread.start();

            	mManagement.resume();

            	mNetworkStateManager = new NetworkStateManager(mManagement);

            	ProfileManager.setConnectedVpnProfile(OpenVpnService.this, mProfile);



            }

			private void stopRunningVPN() {
				// Set a flag that we are starting a new VPN
            	mStarting = true;
            	// Stop the previous session by interrupting the thread.
            	if (mManagement != null && mManagement.stopVPN())
            		// an old was asked to exit, wait 1s
            		try {
            			Thread.sleep(1000);
            		} catch (InterruptedException e) {
            		}

            	if (mProcessThread != null) {
            		mProcessThread.interrupt();
            		try {
            			Thread.sleep(1000);
            		} catch (InterruptedException e) {
            		}
            	}            	
            	
            	mStarting = false;
			}

        };

    
    

    private OpenVPNManagement instantiateOpenVPN3Core() {
        try {
            Class<?> cl = Class.forName("de.blinkt.openvpn.core.OpenVPNThreadv3");
            return (OpenVPNManagement) cl.getConstructor(OpenVpnService.class,VpnProfile.class).newInstance(this,mProfile);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onDestroy() {
        if (mProcessThread != null) {
            mManagement.stopVPN();

            mProcessThread.interrupt();
        }
       
        // Just in case unregister for state
        VpnStatus.removeStateListener(this);
        
        // Quit the service looper
        mServiceLooper.quit();
    }

    private String getTunConfigString() {
        // The format of the string is not important, only that
        // two identical configurations produce the same result
        String cfg = "TUNCFG UNQIUE STRING ips:";

        if (mLocalIP != null)
            cfg += mLocalIP.toString();
        if (mLocalIPv6 != null)
            cfg += mLocalIPv6.toString();

        cfg += "routes: " + TextUtils.join("|", mRoutes.getNetworks(true)) + TextUtils.join("|", mRoutesv6.getNetworks(true));
        cfg += "excl. routes:" + TextUtils.join("|", mRoutes.getNetworks(false)) + TextUtils.join("|", mRoutesv6.getNetworks(false));
        cfg += "dns: " + TextUtils.join("|", mDnslist);
        cfg += "domain: " + mDomain;
        cfg += "mtu: " + mMtu;
        return cfg;
    }

    public ParcelFileDescriptor openTun() {

        //Debug.startMethodTracing(getExternalFilesDir(null).toString() + "/opentun.trace", 40* 1024 * 1024);

        Builder builder = new Builder();

        VpnStatus.logInfo(R.string.last_openvpn_tun_config);


        if (mLocalIP == null && mLocalIPv6 == null) {
            VpnStatus.logError(getString(R.string.opentun_no_ipaddr));
            return null;
        }

        if (mLocalIP != null) {
            try {
                builder.addAddress(mLocalIP.mIp, mLocalIP.len);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, mLocalIP, iae.getLocalizedMessage());
                return null;
            }
        }

        if (mLocalIPv6 != null) {
            String[] ipv6parts = mLocalIPv6.split("/");
            try {
                builder.addAddress(ipv6parts[0], Integer.parseInt(ipv6parts[1]));
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.ip_add_error, mLocalIPv6, iae.getLocalizedMessage());
                return null;
            }

        }


        for (String dns : mDnslist) {
            try {
                builder.addDnsServer(dns);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, dns, iae.getLocalizedMessage());
            }
        }


        builder.setMtu(mMtu);

        Collection<ipAddress> positiveIPv4Routes = mRoutes.getPositiveIPList();
        Collection<ipAddress> positiveIPv6Routes = mRoutesv6.getPositiveIPList();

        for (NetworkSpace.ipAddress route : positiveIPv4Routes) {
            try {
                builder.addRoute(route.getIPv4Address(), route.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route + " " + ia.getLocalizedMessage());
            }
        }

        for (NetworkSpace.ipAddress route6 : positiveIPv6Routes) {
            try {
                builder.addRoute(route6.getIPv6Address(), route6.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route6 + " " + ia.getLocalizedMessage());
            }
        }

        if (mDomain != null)
            builder.addSearchDomain(mDomain);

        VpnStatus.logInfo(R.string.local_ip_info, mLocalIP.mIp, mLocalIP.len, mLocalIPv6, mMtu);
        VpnStatus.logInfo(R.string.dns_server_info, TextUtils.join(", ", mDnslist), mDomain);
        VpnStatus.logInfo(R.string.routes_info_incl, TextUtils.join(", ", mRoutes.getNetworks(true)), TextUtils.join(", ", mRoutesv6.getNetworks(true)));
        VpnStatus.logInfo(R.string.routes_info_excl, TextUtils.join(", ", mRoutes.getNetworks(false)),TextUtils.join(", ", mRoutesv6.getNetworks(false)));
        VpnStatus.logDebug(R.string.routes_debug, TextUtils.join(", ", positiveIPv4Routes), TextUtils.join(", ", positiveIPv6Routes));

        String session;
        if ( !ConfigParser.CONVERTED_PROFILE.equals(mProfile.mName)){
        	session = mProfile.mName;
        } else {
        	session = mProfile.mServerName;
        }

        builder.setSession(session);

        // No DNS Server, log a warning
        if (mDnslist.size() == 0)
            VpnStatus.logInfo(R.string.warn_no_dns);

        mLastTunCfg = getTunConfigString();

        // Reset information
        mDnslist.clear();
        mRoutes.clear();
        mRoutesv6.clear();
        mLocalIP = null;
        mLocalIPv6 = null;
        mDomain = null;

        builder.setConfigureIntent(getLogPendingIntent());

        try {
            ParcelFileDescriptor tun = builder.establish();
            //Debug.stopMethodTracing();
            return tun;
        } catch (Exception e) {
            VpnStatus.logError(R.string.tun_open_error);
            VpnStatus.logError(getString(R.string.error) + e.getLocalizedMessage());
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                VpnStatus.logError(R.string.tun_error_helpful);
            }
            return null;
        }

    }

    public void addDNS(String dns) {
        mDnslist.add(dns);
    }

    public void setDomain(String domain) {
        if (mDomain == null) {
            mDomain = domain;
        }
    }

    /** Route that is always included, used by the v3 core */
    public void addRoute (CIDRIP route) {
        mRoutes.addIP(route, true);
    }

    public void addRoute (String dest, String mask, String gateway, String device) {
        CIDRIP route = new CIDRIP(dest, mask);
        boolean include = isAndroidTunDevice(device);

        NetworkSpace.ipAddress gatewayIP = new NetworkSpace.ipAddress(new CIDRIP(gateway, 32),false);

        if (mLocalIP==null) {
            VpnStatus.logError("Local IP address unset but adding route?! This is broken! Please contact author with log");
            return;
        }
        NetworkSpace.ipAddress localNet = new NetworkSpace.ipAddress(mLocalIP,true);
        if (localNet.containsNet(gatewayIP))
            include=true;

        if (gateway!= null &&
                (gateway.equals("255.255.255.255") || gateway.equals(mRemoteGW)))
            include=true;


        if (route.len == 32 && !mask.equals("255.255.255.255")) {
            VpnStatus.logWarning(R.string.route_not_cidr, dest, mask);
        }

        if (route.normalise())
            VpnStatus.logWarning(R.string.route_not_netip, dest, route.len, route.mIp);

        mRoutes.addIP(route, include);
    }

    public void addRoutev6(String network, String device) {
        String[] v6parts = network.split("/");
        boolean included = isAndroidTunDevice(device);

        // Tun is opened after ROUTE6, no device name may be present

        try {
            Inet6Address ip = (Inet6Address) InetAddress.getAllByName(v6parts[0])[0];
            int mask = Integer.parseInt(v6parts[1]);
            mRoutesv6.addIPv6(ip, mask, included);

        } catch (UnknownHostException e) {
            VpnStatus.logException(e);
        }


    }

    private boolean isAndroidTunDevice(String device) {
        return device!=null &&
                (device.startsWith("tun") || "(null)".equals(device) || "vpnservice-tun".equals(device));
    }

    public void setMtu(int mtu) {
        mMtu = mtu;
    }

    public void setLocalIP(CIDRIP cdrip) {
        mLocalIP = cdrip;
    }

    public void setLocalIP(String local, String netmask, int mtu, String mode) {
        mLocalIP = new CIDRIP(local, netmask);
        mMtu = mtu;
        mRemoteGW=null;


        if (mLocalIP.len == 32 && !netmask.equals("255.255.255.255")) {
            // get the netmask as IP
            long netMaskAsInt = CIDRIP.getInt(netmask);

            // Netmask is Ip address +/-1, assume net30/p2p with small net
            if (Math.abs(netMaskAsInt - mLocalIP.getInt()) == 1) {
                if ("net30".equals(mode))
                    mLocalIP.len = 30;
                else
                    mLocalIP.len = 31;
            } else {
                if (!"p2p".equals(mode))
                    VpnStatus.logWarning(R.string.ip_not_cidr, local, netmask, mode);
                mRemoteGW=netmask;
            }
        }
    }

    public void setLocalIPv6(String ipv6addr) {
        mLocalIPv6 = ipv6addr;
    }

    @Override
    public void updateState(String state, String logmessage, int resid, ConnectionStatus level) {
        // If the process is not running, ignore any state,
        // Notification should be invisible in this state
        doSendBroadcast(state, level);
        if (mProcessThread == null && !mNotificationAlwaysVisible)
            return;

            if (level == LEVEL_WAITING_FOR_USER_INPUT) {
                // The user is presented a dialog of some kind, no need to inform the user
                // with a notification
                return;
            } 

            // Other notifications are shown,
            // This also mean we are no longer connected, ignore bytecount messages until next
            // CONNECTED
            // Does not work :(
            showNotification(stateToString(level));
            
            if ( level == LEVEL_CONNECTED){
            	mLevel = level;
            	// Register shared preference observer to update data usage
            	getSharedPreferences(VPNImplementation.VPN_PREFS, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(mPrefListener);
            } else {
            	// deregister shared preference observer 
            	getSharedPreferences(VPNImplementation.VPN_PREFS, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(mPrefListener);
            }
    }
    
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if((key.equals(VPNImplementation.KEY_BANDWIDTH_USED) || key.equals(VPNImplementation.KEY_BANDWIDTH_LIMIT))) {
                showNotification(stateToString(mLevel));
            }
        }
    };

    private void doSendBroadcast(String state, ConnectionStatus level) {
    	// TODO decide in future if the connection state needs 
    	// to be broadcast.
//        Intent vpnstatus = new Intent();
//        vpnstatus.setAction("de.blinkt.openvpn.VPN_STATUS");
//        vpnstatus.putExtra("status", level.toString());
//        vpnstatus.putExtra("detailstatus", state);
//        sendBroadcast(vpnstatus, permission.ACCESS_NETWORK_STATE);
    }

 

    @Override
    public boolean handleMessage(Message msg) {
        Runnable r = msg.getCallback();
        if (r != null) {
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public OpenVPNManagement getManagement() {
        return mManagement;
    }

    public String getTunReopenStatus() {
        String currentConfiguration = getTunConfigString();
        if (currentConfiguration.equals(mLastTunCfg))
            return "NOACTION";
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            return "OPEN_AFTER_CLOSE";
        else
            return "OPEN_BEFORE_CLOSE";
    }

    public class LocalBinder extends Binder {
        public OpenVpnService getService() {
            // Return this instance of LocalService so clients can call public methods
            return OpenVpnService.this;
        }
    }
    
    
}
