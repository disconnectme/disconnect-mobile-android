package me.disconnect.mobile;

import me.disconnect.mobile2.R;
import me.disconnect.mobile.billing.BillingHelper;
import me.disconnect.mobile.billing.BillingObserver;
import me.disconnect.mobile.billing.Inventory;
import me.disconnect.mobile.billing.Purchase;
import me.disconnect.mobile.billing.SkuDetails;
import me.disconnect.mobile.packages.PackageAlertDialog;
import me.disconnect.mobile.packages.PackageDescriptionManager;
import me.disconnect.mobile.packages.PackageAlertDialog.PackageAlertDialogListener;
import me.disconnect.mobile.vpn.SecureFiDetector;
import me.disconnect.mobile.vpn.VPNProvider;
import me.disconnect.mobile.vpn.VpnManager;
import me.disconnect.mobile.vpn.SecureFiDetector.SecureFiObserver;
import me.disconnect.securefi.engine.LoggingConfig;
import me.disconnect.securefi.engine.VPNImplementation;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

public class FrontScreenFragment extends Fragment implements PackageAlertDialogListener, SecureFiObserver {
	private static final int SECURE_FI_ACTIVE_POLL_INTERVAL = 30000;

	private final static String TAG = "FrontScreenFragment";
	
	protected static final int ADS_PACKAGE_ID = 1;
	protected static final int MALWARE_PACKAGE_ID = 2;
	protected static final int BASIC_PACKAGE_ID = 3;
	
	private static final int IGNORE_UNTIL_CONNECTION_COUNT = 2;

	private VpnManager mVpnManager;
	
	private ToggleButton mProtectButton;
	private ToggleButton mBasicButton;
	private ToggleButton mAdButton;
	private ToggleButton mMalwareButton;

	private View mStarField;
	
	private DisconnectMobilePrefs mPrefs;
	private View mOverlay;
	private View mStartingOverlay;
	
	// When attempting to connect to the vpn ignore any
	// disconnect state changes until at least 2 connectioning
	// notifications have been received.
	private int mConnectingIgnoreDisconnectCount;
	
	private BillingHelper mBillingHelper;
	
	private boolean mSecureFiActive;
	
	final Handler mHandler = new Handler();
	
	public FrontScreenFragment() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		mVpnManager = new VpnManager( getActivity() );
		
		// Setup UI components
		View rootView = inflater.inflate(R.layout.front_screen, container,
				false);
		
		mProtectButton = (ToggleButton) rootView.findViewById(R.id.protect_button);
		mProtectButton.setOnClickListener(mProtectButtonCheckListener);
		
		mBasicButton = (ToggleButton) rootView.findViewById(R.id.basic_button);
		mBasicButton.setOnClickListener(mPackageButtonClickListener);
		
	    mAdButton = (ToggleButton) rootView.findViewById(R.id.ad_button);
	    mAdButton.setOnClickListener(mPackageButtonClickListener);
	    
	    mMalwareButton = (ToggleButton) rootView.findViewById(R.id.malware_button);
	    mMalwareButton.setOnClickListener(mPackageButtonClickListener);
	    mMalwareButton.setVisibility(View.GONE);
	    
	    mStarField = rootView.findViewById(R.id.star_field);
	    
	    mOverlay = rootView.findViewById(R.id.overlay_ref);
	    Button overlayCancelButton = (Button) rootView.findViewById(R.id.connecting_cancel);
	    overlayCancelButton.setOnClickListener(mCancelButtonClickListener);
	    
	    mStartingOverlay = rootView.findViewById(R.id.startup_overlay_ref);
	    
	    mPrefs = new DisconnectMobilePrefs(getActivity());
	    
	    // Check billing inventory for any 
	 	// current purchases;
	    checkBillingInventory();
		
		return rootView;
	}
	
	private OnClickListener mProtectButtonCheckListener = new OnClickListener(){

		@Override
		public void onClick(View aButtonView) {
			if ( mSecureFiActive ){
				// If the device is protected by Securefi, display message.
				Toast.makeText(getActivity(), R.string.securefi_active_message, Toast.LENGTH_LONG).show();
				return;
			}
			
			if ( mPrefs.getAvailablePackages() == 0 ){
				// Display message saying no packages activated.
				Toast toast = Toast.makeText(getActivity(), getString(R.string.activate_package) , Toast.LENGTH_LONG);
				toast.show();
				
				mProtectButton.setChecked(false);
				return;
			}
			
			if ( mProtectButton.isChecked() ){
				mConnectingIgnoreDisconnectCount = IGNORE_UNTIL_CONNECTION_COUNT;
				mPrefs.setProtection(true);
				mVpnManager.connectVPN();
				disableNotification();
			} else {
				mPrefs.setProtection(false);
				mVpnManager.disconnectVPN();
			}
		}
	};
	
	private OnClickListener mPackageButtonClickListener = new OnClickListener(){

		@Override
		public void onClick(View aButtonView) {
			if ( mSecureFiActive ){
				// If the device is protected by Securefi, display message.
				Toast.makeText(getActivity(), R.string.securefi_active_message, Toast.LENGTH_LONG).show();
				return;
			}
			
			if ( !PackageDescriptionManager.getInstance().downloadComplete() ){
				// Display error message if package descriptions have not been downloaded.
				Toast.makeText(getActivity(), R.string.package_description_error, Toast.LENGTH_LONG).show();
				return;
			}
			
			int packageId = 0;
			boolean isActivated = false;
			boolean isPurchased = false;
			int iconResId = 0;
			String price = null;
			switch ( aButtonView.getId() ){
			case R.id.ad_button:{
				packageId = ADS_PACKAGE_ID;
				isActivated = mPrefs.adsPackageAvailable();
				isPurchased = mPrefs.adsPackagePurchased();
				iconResId = R.drawable.ic_ad_on;
				price = mPrefs.adsPackagePrice();
			}
			break;
			case R.id.basic_button:{
				packageId = BASIC_PACKAGE_ID;
				isActivated = mPrefs.basicPackageAvailable();
				isPurchased = true; // basic package available by default
				iconResId = R.drawable.ic_basic_on;
			}
			break;
			case R.id.malware_button:{
				packageId = MALWARE_PACKAGE_ID;
				isActivated = mPrefs.malwarePackageAvailable();
				isPurchased = mPrefs.malwarePackagePurchased();
				iconResId = R.drawable.ic_malware_on;
				price = mPrefs.malwarePackagePrice();
			}
			break;
			default:{
				
			}
			}
			
			if ( price == null ){
				// If the store package price is null, use the default
				// text.
				price = getString(R.string.package_alert_dialog_purchase);
			}
			
			// show dialog
        	PackageAlertDialog packageDialog = PackageAlertDialog.newInstance(packageId, isActivated, isPurchased, iconResId, price);
        	packageDialog.setListener(FrontScreenFragment.this);
        	packageDialog.show(getFragmentManager(), "upgrade_tag");
		}
	};
	
	private OnClickListener mCancelButtonClickListener = new OnClickListener(){

		@Override
		public void onClick(View aButtonView) {
				mPrefs.setProtection(false);
				mVpnManager.disconnectVPN();
		}
	};

	private BroadcastReceiver mUserCancelReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent) {
			// The user has cancelled the connection after reading
			// the warnings regarding connecting. Update the UX to
			// reflect the cancellation.
			setUiDisconnected();
			mOverlay.setVisibility(View.GONE);
		}
	};
	
	public void onStart() {
		super.onStart();
		
		// Connect to new provider
    	VPNImplementation vpnImpl = VPNProvider.getInstance(getActivity());
        vpnImpl.init(getActivity());
        vpnImpl.addStateListener(mConnectionListener);
        
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mUserCancelReceiver,
        	      new IntentFilter(InvisibleActivity.USER_CANCELLED));
	}
	
	public void onStop() {
		super.onStop();
		
		VPNImplementation vpnImpl = VPNProvider.getInstance(getActivity());
        vpnImpl.removeStateListener(mConnectionListener);
        vpnImpl.endit(getActivity());
        
        // Remove any connection state updates added.
        mHandler.removeCallbacks(mUpdateConnectionState);
        
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mUserCancelReceiver);
	}
	
	// Listen for changes to the connected state
	 private VPNImplementation.ConnectionListener mConnectionListener = new VPNImplementation.ConnectionListener() {
	        @Override
	        public void stateChanged(VPNImplementation.CONNECTION_STATE newState) {
	        	// TODO Check if an attempt to re-connect/disconnect the VPN should be made.
	        	Log.d("CONNECTION_SA", "state = " + newState.name());
	        	
	        	// If the state is not disabled, then the connection can't be protected by SecureFi
	        	if ( newState != VPNImplementation.CONNECTION_STATE.DISABLED){
	        		mSecureFiActive = false;
	        	}
	            
	            getActivity().runOnUiThread(mUpdateConnectionState);
	        }
	    };
	    
	    private Runnable mUpdateConnectionState = new Runnable(){

			public void run() {
            	// Show overlay if the state is connecting
            	// Update connection status
                VPNImplementation.CONNECTION_STATE vpn_state = VPNProvider.getInstance(getActivity()).getConnectionState();
                boolean vpn_connecting = vpn_state == VPNImplementation.CONNECTION_STATE.CONNECTING;
                boolean vpn_disconnecting = vpn_state == VPNImplementation.CONNECTION_STATE.DISCONNECTING;
                boolean disconnected = (vpn_state == VPNImplementation.CONNECTION_STATE.DISABLED) || ( vpn_state == null ); 
                
                if ( vpn_connecting ){
                	// decrement ignore count
                	mConnectingIgnoreDisconnectCount--;
                }
                
                if ( (vpn_disconnecting || disconnected)
                		&& mConnectingIgnoreDisconnectCount > 0){
                	// Ignore the disconnect state while re-connecting
                	// until 2 connecting state changes have been received.
                	return;
                }
                
                // Prevent users from crazy-clicking while VPN is connecting.
                mOverlay.setVisibility(vpn_connecting ? View.VISIBLE : View.INVISIBLE);  
                
                boolean connected = vpn_state == VPNImplementation.CONNECTION_STATE.CONNECTED;
                if ( connected ){
                	mConnectingIgnoreDisconnectCount = 0;
                	setUiConnected();
                } else if (disconnected){ 
                	// If the VPN is disconnected, check if the device is
                	// protected by the SecureFi app.
                	
                	// Check if SecureFi is active
                	mStartingOverlay.setVisibility(View.VISIBLE);
                    SecureFiDetector.discoverIfActiveAsync(getActivity(), FrontScreenFragment.this);
                }
            }
        };
	    
	@Override
	public void activatePressed(int aPackageId, boolean aActivate) {
		switch ( aPackageId ){
		case ADS_PACKAGE_ID:{
			mPrefs.setAdvertsPackage(aActivate);
			mAdButton.setChecked(aActivate);
		}
		break;
		case BASIC_PACKAGE_ID:{
			mPrefs.setBasicPackage(aActivate);
			mBasicButton.setChecked(aActivate);
		}
		break;
		case MALWARE_PACKAGE_ID:{
			mPrefs.setMalwarePackage(aActivate);
			mMalwareButton.setChecked(aActivate);
		}
		break;
		default:{
			
		}
		}
		
		// Handle package activation changed
		if ( !aActivate ){
			VPNImplementation.CONNECTION_STATE vpn_state = VPNProvider.getInstance(getActivity()).getConnectionState();
			if ( vpn_state != VPNImplementation.CONNECTION_STATE.CONNECTED ){
				// If the vpn is not connected, ignore the deactivation of a package.
				return;
			}
		}
		
		handlePackageActivation();	
	}

	@Override
	public void purchasePressed(final int aPackageId, final int nativeBilling) {
		// Purchase package
		String packageSku = null;
		if ( aPackageId == ADS_PACKAGE_ID){
			packageSku = BillingHelper.ADS_PACK_SKU;;
		} else if ( aPackageId == MALWARE_PACKAGE_ID ){
			packageSku = BillingHelper.MALWARE_PACK_SKU;
		}

		if ( packageSku != null && mBillingHelper == null ){
				final String sku = packageSku;

				if (nativeBilling == 0 || DisconnectMobileConfig.FORCE_NON_NATIVE_BILLING) {
					startActivityForResult(new Intent(getActivity(), WebUpgradeActivity.class), 1337);
				}
				else {
					mBillingHelper = new BillingHelper(getActivity(), new BillingObserver(){
						
						@Override
						public void billingAvailable(boolean aAvailable) {
							if (aAvailable ){
								mBillingHelper.purchasePack(getActivity(), sku);
							} else {
								mBillingHelper.dispose();
								mBillingHelper = null;
							}
						}

						@Override
						public void inventoryCompleted(Inventory inventory) {
						}

						@Override
						public void purchaseComplete(boolean aSuccess) {
							if ( aSuccess ){
								// If purchase successful, activate package
								if ( aPackageId == ADS_PACKAGE_ID){
									mPrefs.setAdvertsPackagePurchased(true);
									mPrefs.setAdvertsPackage(true);
									mAdButton.setChecked(true);
								} else if ( aPackageId == MALWARE_PACKAGE_ID ){
									mPrefs.setMalwarePackagePurchased(true);
									mPrefs.setMalwarePackage(true);
									mMalwareButton.setChecked(true);
								}
								
								handlePackageActivation();
							}
							
							mBillingHelper.dispose();
							mBillingHelper = null;
						}
					});
				}
		}
	}
	
	private void handlePackageActivation() {
		if ( mPrefs.getAvailablePackages() > 0 ){
			// Show connecting screen
			mOverlay.setVisibility(View.VISIBLE);
			
			// reconnect VPN
			mConnectingIgnoreDisconnectCount = IGNORE_UNTIL_CONNECTION_COUNT;
			mVpnManager.connectVPN();
			mPrefs.setProtection(true);
			disableNotification();
		} else {
			// Make sure any VPN connection is disconnected.
			mPrefs.setProtection(false);
			mVpnManager.disconnectVPN();
		}
	}

	// TODO: Option to disable the icon
	public void disableNotification() {
		NotificationManager notificationManager =
                (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(VPNImplementation.NOTIFICATION_ID);
	}

	private void setUiConnected() {
		mProtectButton.setChecked(true);
		mAdButton.setChecked(mPrefs.adsPackageAvailable());
		mBasicButton.setChecked(mPrefs.basicPackageAvailable());
		mMalwareButton.setChecked(mPrefs.malwarePackageAvailable());
		mStarField.setBackgroundResource(R.drawable.background_collusion_on);
		// TODO: Option to disable the icon
		disableNotification();
	}

	private void setUiDisconnected() {
		mProtectButton.setChecked(false);
		mAdButton.setChecked(false);
		mBasicButton.setChecked(false);
		mMalwareButton.setChecked(false);
		
		mStarField.setBackgroundResource(R.drawable.background_collusion_off);
	}
	
	private void checkBillingInventory(){
		// Create billing helper
		if ( mBillingHelper == null ){

			mBillingHelper = new BillingHelper(getActivity(), new BillingObserver(){

				@Override
				public void billingAvailable(boolean aAvailable) {
					// If billing isn't available hide upgrade button.
					if ( !aAvailable ){
						// TODO what to do if billing isn't available?

						mBillingHelper.dispose();
						mBillingHelper = null;
					} else {
						// Fetch inventory to discover what subscriptions (if any) the user has made.
						mBillingHelper.asyncGetInventory();
					}
				}

				@Override
				public void inventoryCompleted(Inventory inventory) {
					if ( inventory != null ){
						if ( LoggingConfig.LOGGING ){
							Log.d(TAG, inventory.toString());
						}

						// Purchases discovered, therefore find the current purchased
						// subscriptions.
						Purchase adsPurchase = inventory.getPurchase(BillingHelper.ADS_PACK_SKU);
						if ( adsPurchase != null && adsPurchase.getPurchaseState() == Purchase.PURCHASE_STATE_PURCHASED ){
							mPrefs.setAdvertsPackagePurchased(true);
						} else if (mPrefs.adsPackagePurchased() != true) {
							mPrefs.setAdvertsPackagePurchased(false);
						}
						
						Purchase malwarePurchase = inventory.getPurchase(BillingHelper.MALWARE_PACK_SKU);
						if ( malwarePurchase != null && malwarePurchase.getPurchaseState() == Purchase.PURCHASE_STATE_PURCHASED ){
							mPrefs.setMalwarePackagePurchased(true);
						} else {
							mPrefs.setMalwarePackagePurchased(false);
						}

						// Store pack prices
						SkuDetails adsDetails = inventory.getSkuDetails(BillingHelper.ADS_PACK_SKU);
						if ( adsDetails != null ){
							mPrefs.setAdvertsPackagePrice(adsDetails.getPrice());
						}
						
						SkuDetails malwareDetails = inventory.getSkuDetails(BillingHelper.MALWARE_PACK_SKU);
						if ( adsDetails != null ){
							mPrefs.setMalwarePackagePrice(malwareDetails.getPrice());
						}		
					}

					mBillingHelper.dispose();
					mBillingHelper = null;				
				}

				@Override
				public void purchaseComplete(boolean aSuccess) {
					// TODO Auto-generated method stub

				}
			}
					);
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1337) {
			// Handle result from stripe payment
			if (resultCode == 1) {
				mAdButton.setChecked(true);
				handlePackageActivation();	
			}
		}
		else {
			// Otherwise pass it to the native billing handler
			mBillingHelper.handleActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public void secureFiActive(Boolean aIsActive) {
		// Hide spinner
		mStartingOverlay.setVisibility(View.GONE);
		
		if ( !aIsActive ){
			// The device is not protected by the SecureFi app
			mSecureFiActive = false;
			setUiDisconnected();
			
			// Display notification, tap to protect
			updateNotification(getActivity().getString(me.disconnect.securefi.openvpnlib.R.string.vpn_notification_disconnected));
		} else {
			mAdButton.setChecked( true );
			mBasicButton.setChecked( true );
			mMalwareButton.setChecked( true );
			mProtectButton.setChecked(true);
			mStarField.setBackgroundResource(R.drawable.background_collusion_on);
			mSecureFiActive = true;
			
			// TODO Unfortunately there isn't a way of updating the notification in
			// the background if the SecureFi app stops protecting the device. Therefore
			// remove the disconnect notification
			disableNotification();
			
			//// Update notification to say protected by SecureFi
			//updateNotification("Currently blocking tracking, ads, and malware");
			
			// If the connection is protected by the SecureFi app, re-check 
			// every 30 seconds.
			if ( isVisible() ){
				mHandler.postDelayed(mUpdateConnectionState, SECURE_FI_ACTIVE_POLL_INTERVAL); 
			} else {
				mProtectButton.setChecked(true);
			}
		}	
	}
	
    private void updateNotification(String msg) {
        String mainActivity = "me.disconnect.mobile.MainActivity";
        
        Notification.Builder builder = new Notification.Builder(getActivity())
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(getString(me.disconnect.securefi.openvpnlib.R.string.vpn_notification_title))
            .setContentText(msg);
        
        // Create an explicit intent for our main activity.
        Intent resultIntent = new Intent();
        resultIntent.setClassName(getActivity(), mainActivity);
        
        // The stack builder object will contain an artificial back stack for the started Activity.
        // This ensures that navigating backward from the Activity leads from app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getActivity());
        // Add the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(new ComponentName(getActivity(), mainActivity));
        // Add the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        builder.setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
//        mBuilder.setLights(Color.GREEN, 500, 500);
         
        @SuppressWarnings("deprecation") // Notification.Builder build() is > API 16
		Notification notification = builder.getNotification();
        NotificationManager notificationManager =
                (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        notificationManager.notify(VPNImplementation.NOTIFICATION_ID, notification);
    }
}
