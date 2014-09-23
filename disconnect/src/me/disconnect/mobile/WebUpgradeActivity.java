package me.disconnect.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import me.disconnect.mobile2.R;

public class WebUpgradeActivity extends Activity {

	private WebView mWebView;
	private ProgressBar mLoadingSpinner;
	private boolean mClosing = false;
	private DisconnectMobilePrefs mPrefs;

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	    protected void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.web_upgrade);
	        getActionBar().setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
	        
	        // Get webview
	        mWebView = (WebView) findViewById(R.id.webview);
	        mLoadingSpinner = (ProgressBar)findViewById(R.id.loading_spinner);
	        
	        // Enable javascript
	        WebSettings webSettings = mWebView.getSettings();
	        webSettings.setJavaScriptEnabled(true);

	        mWebView.setWebViewClient(new LocalWebViewClient());
	        
	        //Load preferences
	        mPrefs = new DisconnectMobilePrefs(getApplicationContext());

	        // Load upgrade page for user
	        String url = "https://your-endpoint.com/upgrade";

	        mWebView.loadUrl(url);
	    }
	
	@Override
	public void onBackPressed() {
	    if (mWebView.canGoBack()) {
	        mWebView.goBack();
	        return;
	    }
	    
	    // Display progress spinner, and fetch updated status
	    handleClose();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    // Inflate the menu items for use in the action bar
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.web_upgrade_menu, menu);
	    return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    // Respond to the action bar's Up/Home button
	    case android.R.id.home:
	    case R.id.action_close:
	    	handleClose();
	        return true;
	    }
	    return super.onOptionsItemSelected(item);
	}
	
	// Display progress spinner, and fetches updated status
	private void handleClose(){
		if ( ! mClosing ){
			mClosing = true;
		}
		setResult(0, new Intent());
		finish();
	}
	
	private class LocalWebViewClient extends WebViewClient {
		 @Override
		    public boolean shouldOverrideUrlLoading(WebView view, String url) {
			 	// TODO Add logic to decide if url should be loaded in the webview or not
			 	return false;
//		        if (Uri.parse(url).getHost().equals("www.example.com")) {
//		            // This the upgrade web site, so do not override; let the WebView load the page
//		            return false;
//		        }
//		        
//		        // Otherwise,  launch another Activity that handles URLs
//		        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//		        startActivity(intent);
//		        return true;
		    }
		 
		 @Override
		    public void onPageFinished(WebView view, String url) {
				 
				 if (url.equals("https://your-endpoint.com/purchased")) {
					mPrefs.setAdvertsPackagePurchased(true);
					mPrefs.setAdvertsPackage(true);
					
					//Payment successful
					setResult(1, new Intent());
					
					// Close webview after we're done
					finish();
					//mAdButton.setChecked(true);
				 }
		        super.onPageFinished(view, url);
		        
		        // Hide the loading spinner when the page has loaded
		        mLoadingSpinner.setVisibility(View.GONE);
		    }
	}
}