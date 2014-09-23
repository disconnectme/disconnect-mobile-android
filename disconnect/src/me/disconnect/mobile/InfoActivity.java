package me.disconnect.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import me.disconnect.mobile2.R;

public class InfoActivity extends Activity {
	
	private static final String INFO_TEXT = "infoText";
	private static final String TITLE = "title";

	public static void show(Activity aLaunchingActivity, int aTitleResId, int aInfoTextResId) {
		Intent intent = new Intent(aLaunchingActivity, InfoActivity.class);
		intent.putExtra(TITLE, aLaunchingActivity.getString(aTitleResId) );
		intent.putExtra(INFO_TEXT, aLaunchingActivity.getString( aInfoTextResId) );
		aLaunchingActivity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        getActionBar().setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        
        Intent intent = getIntent();
        String text = intent.getStringExtra(INFO_TEXT);
        
        TextView body = (TextView)findViewById(R.id.body);
        body.setText(text);
    }

}
