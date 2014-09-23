package me.disconnect.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ToggleButton;

public class ConnectButton extends ToggleButton {

	public ConnectButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setDrawingCacheEnabled(true);
	}

	public ConnectButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setDrawingCacheEnabled(true);
	}

	public ConnectButton(Context context) {
		super(context);
		setDrawingCacheEnabled(true);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Ignore clicks on the transparent parts of the button
		final int action = event.getAction();
		if (action == MotionEvent.ACTION_UP){
		Bitmap bmp = Bitmap.createBitmap(getDrawingCache());
		int color = 0;
		   try {
		    color = bmp.getPixel((int) event.getX(), (int) event.getY());
		   } catch (Exception e) {
		   }
		   if (color == Color.TRANSPARENT){
			   // If transparent ignore up event
			   return false;
		   }
		}
		
		return super.onTouchEvent(event);
	}
}
