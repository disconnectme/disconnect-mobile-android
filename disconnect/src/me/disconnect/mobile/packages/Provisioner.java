package me.disconnect.mobile.packages;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for getting package information from back end.
 */
public class Provisioner{

    public interface PackagesResultProcessor {
        /**
         * @param result  a JSON array 
         */
        public void processResult(JSONArray result);
        public void onError();
    }

    /**
     * High-level utility to refresh packages back-end status.
     * Hits the with a given url 
     * Call from the UI thread. Runs asynchronously. Results returned on UI thread.
     * @param processor Callback object to read from the JSON results.
     * @param urlAndNameValuePairs Provisioning or status URL
     */
    public static void refreshPackages(final PackagesResultProcessor processor, String url) {
        new AsyncTask<String, Void, JSONArray>() {
            @Override
            protected JSONArray doInBackground(String... params) {
                return refresh(params);
            }
            @Override
            protected void onPostExecute(JSONArray result) {
                if(result != null){
                    processor.processResult(result);
                } else {
                	processor.onError();
                }
            }
        }.execute(url);
    }

    /**
     * Low-level workhorse method. Runs synchronously.
     */
    public static JSONArray refresh(String... params) {
        String url = params[0];
        HttpClient httpclient = new DefaultHttpClient();
        // Post arguments include "amount" - integer number of megabytes desired for the account for debugging.
        HttpGet httpget = new HttpGet(url);
        try {
            // Execute HTTP Post Request
            HttpResponse response = httpclient.execute(httpget);
            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if (statusCode != 200) {
         //       Log.e(SecureWireless.LOG_TAG, "Status refresh failed with status code " + statusCode);
                return null;
            }
            HttpEntity entity = response.getEntity();
            InputStream inputStream = entity.getContent();
            // json is UTF-8 by default
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null)
                sb.append(line + "\n");
            try {
                return new JSONArray(sb.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
