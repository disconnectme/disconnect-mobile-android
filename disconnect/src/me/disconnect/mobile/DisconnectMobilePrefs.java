package me.disconnect.mobile;

import java.util.Calendar;

import me.disconnect.securefi.engine.VPNImplementation;
import android.content.Context;
import android.content.SharedPreferences;

/*
 * Wrapper around the apps shared preferences
 */
public class DisconnectMobilePrefs {
	private static final String ADS_PACK_PURCHASED = "ads_pack_purchased";
	private static final String MALWARE_PACK_PURCHASED = "malware_pack_purchased";
	private static final String ADS_PACK_PRICE = "ads_pack_price";
	private static final String MALWARE_PACK_PRICE = "malware_pack_price";
	
	public static final int BASIC_PACKAGE_ACTIVATED = 1;
	public static final int ADS_PACKAGE_ACTIVATED = 2;
	public static final int MALWARE_PACKAGE_ACTIVATED = 4;
	private static final String SHARED_PREFERENCES_NAME = VPNImplementation.VPN_PREFS; // SharedPreferences file name.
	
	//TODO Change access in invisible activity to use methods.
	public static final String PROTECTION_ON = "protection_on";
	private static final String KEY_GETTING_PERMISSION = VPNImplementation.KEY_GETTING_PERMISSION; // semaphore to serialize the approval process.
	private static final String KEY_PERMISSION_REVOKED = VPNImplementation.KEY_PERMISSION_REVOKED; // If user wants us all the way off.
	private static final String PACKAGES_ACTIVATED = "packages_activated";
	
	private static final String GENERATED_USERNAME = "generated_username";
	private static final String DATE_CHECKED = "date_checked";
	private static final String LAST_NOTIFICATION = "last_notification";
	
	private SharedPreferences mPreferences;
	
	public DisconnectMobilePrefs( Context aContext ){
		//mContext = aContext;
		mPreferences = aContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE );
		if (!mPreferences.contains(PACKAGES_ACTIVATED) ){
			// On first start, set the basic package as being available
			setBasicPackage(true);
		}
	}
	
	public boolean isProtected(){
		return mPreferences.getBoolean(PROTECTION_ON, false);
	}
	
	public void setProtection(boolean aOn){
		mPreferences.edit().putBoolean(PROTECTION_ON, aOn).commit();
		if ( aOn ){

             if(aOn){ // This may fix any error that might have left this flag in the wrong state.
            	 mPreferences.edit()
                     .putBoolean(KEY_PERMISSION_REVOKED, false)
                     .putBoolean(KEY_GETTING_PERMISSION, false)
                     .commit();
             }
		}
	}

	public void setMalwarePackage(boolean aActivated){
		int packages = mPreferences.getInt(PACKAGES_ACTIVATED, 0);
		if ( aActivated ){
			packages |= MALWARE_PACKAGE_ACTIVATED;
		} else {
			packages &= ~ MALWARE_PACKAGE_ACTIVATED;
		}
		
		mPreferences.edit().putInt(PACKAGES_ACTIVATED, packages).commit();
	}
	
	public void setAdvertsPackage(boolean aActivated){
		int packages = mPreferences.getInt(PACKAGES_ACTIVATED, 0);
		if ( aActivated ){
			packages |= ADS_PACKAGE_ACTIVATED;
		} else {
			packages &= ~ ADS_PACKAGE_ACTIVATED;
		}
		
		mPreferences.edit().putInt(PACKAGES_ACTIVATED, packages).commit();
	}
	
	public void setBasicPackage(boolean aActivated){
		int packages = mPreferences.getInt(PACKAGES_ACTIVATED, 0);
		if ( aActivated ){
			packages |= BASIC_PACKAGE_ACTIVATED;
		} else {
			packages &= ~ BASIC_PACKAGE_ACTIVATED;
		}
		
		mPreferences.edit().putInt(PACKAGES_ACTIVATED, packages).commit();
	}
	
	public boolean malwarePackageAvailable(){
		return (mPreferences.getInt(PACKAGES_ACTIVATED, 0) & MALWARE_PACKAGE_ACTIVATED) == MALWARE_PACKAGE_ACTIVATED;
	}
	
	public boolean adsPackageAvailable(){
		return (mPreferences.getInt(PACKAGES_ACTIVATED, 0) & ADS_PACKAGE_ACTIVATED) == ADS_PACKAGE_ACTIVATED;
	}
	
	public boolean basicPackageAvailable(){
		return (mPreferences.getInt(PACKAGES_ACTIVATED, 0) & BASIC_PACKAGE_ACTIVATED) == BASIC_PACKAGE_ACTIVATED;
	}
	
	public int getAvailablePackages(){
		return mPreferences.getInt(PACKAGES_ACTIVATED, 0);
	}
	
	public void setMalwarePackagePurchased(boolean aPurchased){
		mPreferences.edit().putBoolean(MALWARE_PACK_PURCHASED, aPurchased).commit();
	}
	
	public void setAdvertsPackagePurchased(boolean aPurchased){
		mPreferences.edit().putBoolean(ADS_PACK_PURCHASED, aPurchased).commit();
	}
	
	public boolean malwarePackagePurchased(){
		return mPreferences.getBoolean(MALWARE_PACK_PURCHASED, false);
	}
	
	public boolean adsPackagePurchased(){
		return true;
		// return mPreferences.getBoolean(ADS_PACK_PURCHASED, false);
	}
	
	public boolean notificationShown(String identifier) {
		return mPreferences.getBoolean(identifier, false);
	}

	public void setNotificationsShown(String identifier) {
		mPreferences.edit().putBoolean(identifier, true).commit();
	}

	public void setLastNotificationShown(String identifier) {
		mPreferences.edit().putString(LAST_NOTIFICATION, identifier).commit();
	}

	public String getLastNotificationShown() {
		return mPreferences.getString(LAST_NOTIFICATION, "none");
	}
	
	public void setProtectedMessage(String aMessage){
		mPreferences.edit().putString(VPNImplementation.PROTECTED_MESSAGE, aMessage).commit();
	}
	
	public void setMalwarePackagePrice(String aPrice){
		mPreferences.edit().putString(MALWARE_PACK_PRICE, aPrice).commit();
	}
	
	public void setAdvertsPackagePrice(String aPrice){
		mPreferences.edit().putString(ADS_PACK_PRICE, aPrice).commit();
	}
	
	public String malwarePackagePrice(){
		return mPreferences.getString(MALWARE_PACK_PRICE, null);
	}
	
	public String adsPackagePrice(){
		return mPreferences.getString(ADS_PACK_PRICE, null);
	}
	
	public String generatedUsername(){
		return mPreferences.getString(GENERATED_USERNAME, null);
	}
	
	public void setGeneratedUsername(String username){
		mPreferences.edit().putString(GENERATED_USERNAME, username).commit();
	}
	
	public void setDateNotificationChecked(Calendar lastTime){
		mPreferences.edit().putLong(DATE_CHECKED, lastTime.getTime().getTime()).commit();
	}
	
	public Long getDateNotificationChecked(){
		return mPreferences.getLong(DATE_CHECKED, 0);
	}
}
