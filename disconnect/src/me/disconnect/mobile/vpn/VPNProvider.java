package me.disconnect.mobile.vpn;

import me.disconnect.securefi.engine.VPNImplementation;
import me.disconnect.securefi.openvpnlib.OpenVPNImplementation;

import android.content.Context;

/**
 * Singleton source of current VPN provider implementation.
 * Change this code when you want to switch providers for testing or porting.
 * We could also support run-time switching via a SharedPreference provider name, etc.
 */
public class VPNProvider {
	private static VPNImplementation mProvider;
	
    private VPNProvider(){} // Empty private constructor guarantees no instances get created.

    public static VPNImplementation getInstance(Context aContext) {
        // Rather than return mProvider as typical, I'm wrapping it in a proxy object
        // that delegates all interface calls that synchronize on the instance.
    	if ( mProvider == null ){
    		mProvider = new OpenVPNImplementation(aContext);
    	}
    	
        return SynchronizedFactory.makeSynchronized(VPNImplementation.class, mProvider);
    }
}

