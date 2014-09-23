package me.disconnect.mobile.packages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.SparseArray;

import me.disconnect.mobile.DisconnectMobileConfig;
import me.disconnect.mobile.packages.Provisioner.PackagesResultProcessor;

public class PackageDescriptionManager implements PackagesResultProcessor{
	private static PackageDescriptionManager mInstance;
	private SparseArray<PackageDescription> mPackages = new SparseArray<PackageDescription>();
	
	public static PackageDescriptionManager getInstance(){
		if ( mInstance == null ){
			mInstance = new PackageDescriptionManager();
		}
		
		return mInstance;
	}
	
	public void getDescriptionsAsync(){
		Provisioner.refreshPackages(
				this
		       , DisconnectMobileConfig.PACKAGE_PROVISIONING_URL
		    );
	}

	@Override
	public void processResult(JSONArray result) {
		int numPackages = result.length();
		for (int index = 0; index < numPackages; index++ ){
			try {
				PackageDescription packDesc = new PackageDescription();
				JSONObject jsonObject = result.getJSONObject(index);
				packDesc.mBlockTestDomain = jsonObject.getString("block_test_domain");
				packDesc.mFooter = jsonObject.getString("footer");
				packDesc.mHomeScreenTitle = jsonObject.getString("home_screen_title");
				packDesc.mId = jsonObject.getInt("id");
				packDesc.mLastUpdate = jsonObject.optLong("last_updated");
				packDesc.mMainText = jsonObject.getString("main_text");
				packDesc.mName = jsonObject.getString("name");
				packDesc.mPackTitle = jsonObject.getString("pack_title");
				packDesc.mSubTitle = jsonObject.getString("subtitle");
				packDesc.mUseNativeBilling = jsonObject.getInt("native_billing");
				mPackages.put(packDesc.mId, packDesc);
			} catch (JSONException exception){
				exception.printStackTrace();
			}
		}
	}

	@Override
	public void onError() {
		// TODO handle error
	}
	
	public PackageDescription getPackageDescription(int aPackageId){
		return mPackages.get(aPackageId);
	}
	
	public boolean downloadComplete(){
		return mPackages.size() > 0;
	}
}
