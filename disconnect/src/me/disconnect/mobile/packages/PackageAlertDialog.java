package me.disconnect.mobile.packages;

import me.disconnect.mobile2.R;
import me.disconnect.mobile.packages.PackageAlertDialog.PackageAlertDialogListener;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class PackageAlertDialog extends DialogFragment {
	
	private static final String PACKAGE_ID = "package_id";
	private static final String ACTIVATED = "activated";
	private static final String PURCHASED = "purchased";
	private static final String ICON_RES_ID = "icon_id";
	private static final String PACKAGE_PRICE = "price";

	public interface PackageAlertDialogListener {
        public void activatePressed(int aPackageId, boolean aActivate);
        public void purchasePressed(final int aPackageId, final int nativeBilling);
	}

	public static PackageAlertDialog newInstance(int aPackageId, boolean isActivated, boolean isPurchased,
								int aIconResId, String aPrice) {
		PackageAlertDialog frag = new PackageAlertDialog();
		
		Bundle args = new Bundle();
	    args.putInt(PACKAGE_ID, aPackageId);
	    args.putBoolean(ACTIVATED, isActivated);
	    args.putBoolean(PURCHASED, isPurchased);
	    args.putInt(ICON_RES_ID, aIconResId );
	    args.putString(PACKAGE_PRICE, aPrice);
	    frag.setArguments(args);

        return frag;
    }

	private PackageAlertDialogListener mListener;
	
	public void setListener(PackageAlertDialogListener aListener){
		mListener = aListener;
	}
	
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		final int packageId = getArguments().getInt(PACKAGE_ID, 0);
		PackageDescription packDesc = PackageDescriptionManager.getInstance().getPackageDescription(packageId);
		
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());

        View view = getActivity().getLayoutInflater().inflate(R.layout.package_alert_dialog, null);
        alertDialogBuilder.setView(view);
        
        setText(view, R.id.title, packDesc.mPackTitle);
        setText(view, R.id.main_text, packDesc.mMainText);
        setText(view, R.id.footer, packDesc.mFooter);
        
        // Set icon
        int iconResId = getArguments().getInt(ICON_RES_ID, 0);
        ImageView icon = (ImageView) view.findViewById(R.id.package_icon);
        icon.setImageResource(iconResId);
        
        final boolean isPurchased = getArguments().getBoolean(PURCHASED, false);
        Button activateButton = (Button)view.findViewById(R.id.activate_button);
        final boolean isActivated = getArguments().getBoolean(ACTIVATED, false);
        if ( isPurchased ) {
        	if ( isActivated ){
        		activateButton.setText(R.string.package_alert_dialog_deactivate);
        	}
        } else {
            // Set purchase price
            String price = getArguments().getString(PACKAGE_PRICE);
        	activateButton.setText(price);
        }
        
        // Set whether or not we should use Google Play or Stripe
        final int nativeBilling = packDesc.mUseNativeBilling;

        activateButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {

				if ( isPurchased ){
					mListener.activatePressed(packageId, !isActivated);
				} else {
					mListener.purchasePressed(packageId, nativeBilling);
				}
				dismiss();
			}
        });
        
        Button cancelButton = (Button)view.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				dismiss();
			}
        });

        return alertDialogBuilder.create();
    }

	private void setText(View view, int resId, String text) {
		TextView textView = (TextView)view.findViewById(resId);
        textView.setText(text);
	}
}
