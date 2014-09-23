package me.disconnect.mobile.billing;

import java.util.Arrays;
import java.util.List;

import me.disconnect.securefi.engine.LoggingConfig;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

public class BillingHelper extends IabHelper {	
	private final static String TAG = "Billing Helper";
	
	// Request code for purchase flow
	public static final int REQUEST_CODE = 221163;

	// SKUs for purchases
	public static final String ADS_PACK_SKU =  "malv_pack_180814";
	public static final String MALWARE_PACK_SKU = "malware_pack_180814";

	public static final List<String> SKU_LIST = Arrays.asList(ADS_PACK_SKU, MALWARE_PACK_SKU );

	private Context mContext;
//	private IabHelper mHelper;
	private BillingObserver mBillingObserver;

	// License key for this app from the Services & APIs section of the Play listing,
	// broken into chunks and reassembled as suggested. Only reconstructed when needed.
	private static final String[] bits = {
	"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAp1rlEwbLcYPJRSM9fiN9JWl8FOtZlBfwaiMwUEvFfwC5rZc07J",
	"7xC422ApoI9/ByX4vYEjKWg6YkyqM75mLLbhIwPfQcDNrbHAb/aC4bUh2HEKUwoCUTOFFTWPS/OXkypvreDnUplq40scs6Y",
	"uPHUriCqKntx5hgz66JtcU+LOswIc8VPx+LdRcvjlxyJ0Wcp+qPdO1XOA0M7xZAyVko3R3pJ8a2USfh0pEv/DrsKk1kuMnVbL2N",
	"vpDs6S53El8WoSv7wISD9h6QW5RarFErFtBiEa5fUAkS6Ox+I8ty4htRkjdpshcJ81NMa25MF074J+wzneNXCW0WGLOfN5FvFQIDAQAB"
	};
	

	public BillingHelper(Context aContext, BillingObserver aBillingObserver) {
		super(aContext, "");

		mContext = aContext;
		mBillingObserver = aBillingObserver;

		// Construct base 64 Encoded public key with obscure variable name to help hide it.
		String BACKGROUND_IMAGE = "";
		for(String s : bits){
			BACKGROUND_IMAGE += s;
		}
		
		mSignatureBase64 = BACKGROUND_IMAGE;

		// enable debug logging (for a production application, you should set this to false).
		enableDebugLogging( LoggingConfig.LOGGING );

		// Start setup. This is asynchronous and the specified listener
		// will be called once setup completes.
		if ( LoggingConfig.LOGGING ){
			Log.d(TAG, "Starting setup.");
		}
		startSetup(mSetupListener);
	}

	private OnIabSetupFinishedListener mSetupListener = new IabHelper.OnIabSetupFinishedListener() {
		public void onIabSetupFinished(IabResult result) {
			Log.d(TAG, "Setup finished.");

			// Have we been disposed of in the meantime? If so, quit.
			if (mBillingObserver == null) {
				return;
			}

			// Report if subscription billing is available
			if ( result.isSuccess() ){
				mBillingObserver.billingAvailable(subscriptionsSupported());
			} else {
				mBillingObserver.billingAvailable(false);
			}
		}
	};

	// Release resources
	public void dispose(){
		super.dispose();
		mBillingObserver = null;
	}

	public void asyncGetInventory() {
		queryInventoryAsync(true, SKU_LIST, mGotInventoryListener);	
	}

	private IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
			if ( LoggingConfig.LOGGING ){
				Log.d(TAG, "Query inventory finished.");
			}

			// Have we been disposed of in the meantime? If so, quit.
			if (mBillingObserver == null) {
				return;
			}

			// Report if subscription billing is available
			if ( result.isSuccess() ){
				mBillingObserver.inventoryCompleted(inventory);
			} else {
				mBillingObserver.inventoryCompleted(null);
			}


		}
	};

	public void purchasePack(Activity aActivity, String sku) {
		// TODO what should the extra data be set to.
		String extraData = "";
		launchPurchaseFlow(aActivity, sku, REQUEST_CODE, mOnIabPurchaseFinishedListener, extraData);
	}

	private OnIabPurchaseFinishedListener mOnIabPurchaseFinishedListener = new OnIabPurchaseFinishedListener(){

		@Override
		public void onIabPurchaseFinished(IabResult result, Purchase aPurchaseInfo) {
			// Have we been disposed of in the meantime? If so, quit.
			if (mBillingObserver == null) {
					return;
			}
			
			if (result.isFailure()) {
				mBillingObserver.purchaseComplete(false);
				return;
	        } 
			
			
	        mBillingObserver.purchaseComplete(true);
			
			// Purchase complete
	        if ( LoggingConfig.LOGGING ){
	        	Log.d(TAG, "Purchase complete");
	        }
	        
	        // TODO handle successful purchase
			
		}
	};
}
