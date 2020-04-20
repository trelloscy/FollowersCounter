package com.mediadownloader.folowerscounter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class MainActivity extends AppCompatActivity {

    String appName = "FollowersCounter";
    String instagramUrl = "https://www.instagram.com/";
    String instagramUrlShort = "https://instagram.com/";
    String publicDirectoryPrefix = "p/";
    EditText txtUsername;
    ArrayList<String> picturesList = new ArrayList<>();
    Button btnSearch;
    ImageView imgPreview;
    TextView txtFollowersCounter;

    public void search_Click(View v) {

        // Check for empty username
        String userInput = sanitizeUsername();
        if (TextUtils.isEmpty(userInput)) {
            txtUsername.setError("Please specify a valid Instagram Username/Profile URL");
            return;
        }

        // Clear preview pic
        clearImage();

        //Toast.makeText(getApplicationContext(), "Button clicked!", Toast.LENGTH_SHORT).show();

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);

        // Define instagram url
        String fullUrl = instagramUrl + userInput + "/";

        try {
            new DownloadSourceCodeTask().execute(fullUrl);
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
    }

    public void cancel_Click(View v) {
        txtUsername.setText("");

        // Clear imageView
        clearImage();
    }

    public void clearImage() {

        imgPreview.setImageResource(android.R.color.transparent);
        imgPreview.setBackgroundColor(Color.parseColor("#f6f6f6"));

        // Also clear followers counter
        txtFollowersCounter.setText("");
    }

    public String sanitizeUsername() {
        String result = txtUsername.getText().toString().trim();

        // New - remove blank spaces
        result = result.replace(" ", "");

        // New
        if (result.contains("?")) {
            result = result.substring(0, result.indexOf("?"));
        }

        result = result.replace(instagramUrl, "");
        result = result.replace(instagramUrlShort, "");
        if (result.endsWith("/")) {
            result = result.substring(0, result.lastIndexOf('/'));
        }

        return result;
    }



    /***************************************************************************/

    public class DownloadSourceCodeTask extends AsyncTask<String, Void, String> {

        ProgressDialog asyncDialog = new ProgressDialog(MainActivity.this);

        @Override
        protected void onPreExecute() {
            // Set message of the dialog
            asyncDialog.setMessage("Processing request...");
            // Show dialog
            asyncDialog.show();
        }

        @Override
        protected String doInBackground(String... urls) {

            URL url;
            HttpURLConnection urlConnection = null;

            try {
                url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();

                int responseCode = urlConnection.getResponseCode();

                if(responseCode == HttpURLConnection.HTTP_OK){
                    return readStream(urlConnection.getInputStream()); // HAPPY PATH!
                }
                else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return "Error: user not found";
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
                return "Error: Malformed URL Exception";
            } catch (IOException e) {
                e.printStackTrace();
                return "Error: Network Exception";
            }

            return null;
        }

        private String readStream(InputStream in) {
            BufferedReader reader = null;
            StringBuffer response = new StringBuffer();
            try {
                reader = new BufferedReader(new InputStreamReader(in));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return response.toString();
        }

        @Override
        protected void onPostExecute(String s) {

            // Hide the dialog
            asyncDialog.dismiss();

            if (s == null) {
                Toast.makeText(getApplicationContext(), "Failed to process request", Toast.LENGTH_LONG).show(); // TOAST!
                return;
            }
            else if (s.startsWith(("Error:"))) {
                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show(); // TOAST!
                return;
            }

            //Toast.makeText(getApplicationContext(), "Request processed successfully", Toast.LENGTH_LONG).show(); // TOAST!

            // Source code retrieval successful
            // Proceed with pattern matching

            // New - Clear array here
            picturesList.clear();

            // New - Get followers count
            Pattern followersCountPattern = Pattern.compile("\"edge_followed_by\":\\{\"count\":(\\d*)\\}");
            Matcher followersCountMatcher = followersCountPattern.matcher(s);
            if (followersCountMatcher.find()) {
                String match = followersCountMatcher.group(1);
                String count = "0";
                if (!match.isEmpty()) {
                    count = String.format("%,d", Integer.parseInt(match));
                }
                txtFollowersCounter.setText("Followers count: " + count);
            }

            // New - Handle hd profile pic (320x320) :-(
            Pattern hdProfilePattern = Pattern.compile("\"profile_pic_url_hd\":\"(https:.+?)\"");
            Matcher hdProfileMatcher = hdProfilePattern.matcher(s);

            // New - Check if a profile or post has been specified
            if (hdProfileMatcher.find()) {

                picturesList.add(SanitizeURL(hdProfileMatcher.group(1))); // First capturing group <3

                if (!picturesList.isEmpty()) {
                    // Download and show The image in a ImageView
                    new LoadImageTask(imgPreview).execute(picturesList.get(0));
                }

            } else { // No hdProfile matched

                // New - If link is a post, search for images+videos and combine the results
                Pattern picturesPattern = Pattern.compile("\"display_url\":\"(https:.+?)\"");
                Matcher picturesMatcher = picturesPattern.matcher(s);

                // Add picture urls to array
                while (picturesMatcher.find()) {
                    picturesList.add(SanitizeURL(picturesMatcher.group(1)));
                }
            }

            // New - if array is empty, show toast notification
            if (picturesList.isEmpty()) {
                Toast.makeText(getApplicationContext(), "Pattern matching failed", Toast.LENGTH_LONG).show(); // TOAST!
            }
        }
    }

    public String SanitizeURL(String url) {
        String match = url.replace("\\u0026", "&");
        return match;
    }

    /*************************************************************************/

    public class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView imageView;

        ProgressDialog asyncDialog = new ProgressDialog(MainActivity.this);

        @Override
        protected void onPreExecute() {
            // Set message of the dialog
            asyncDialog.setMessage("Loading image...");
            // Show dialog
            asyncDialog.show();
        }

        public LoadImageTask(ImageView bmImage) {
            this.imageView = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {

            // Hide the dialog
            asyncDialog.dismiss();

            // New - Make bitmap round!
            RoundedBitmapDrawable img = RoundedBitmapDrawableFactory.create(getResources(), result);
            img.setCircular(true);

            // Not sure if this is needed
            imageView.setBackgroundColor(Color.parseColor("#ffffff"));

            // Finally, assign bitmap to imageView
            //imageView.setImageBitmap(result);
            imageView.setImageDrawable(img);
        }
    }

    /***************************************************************************/

    private static final int TIME_INTERVAL = 2000; // # milliseconds, desired time passed between two back presses.
    private long mBackPressed;

    @Override
    public void onBackPressed() {
        if (mBackPressed + TIME_INTERVAL > System.currentTimeMillis())
        {
            super.onBackPressed();
            return;
        }
        else {
            Toast.makeText(getBaseContext(), "Press again to exit", Toast.LENGTH_SHORT).show(); // TOAST!
        }

        mBackPressed = System.currentTimeMillis();
    }

    /***************************************************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSearch = findViewById(R.id.btnSearch);
        txtUsername = findViewById(R.id.txtUsername);
        imgPreview = findViewById(R.id.imgPreview);
        txtFollowersCounter = findViewById(R.id.txtFollowersCounter);
        clearImage();

        // TEMP - set default value
        //txtUsername.setText("themos.k");

        // Load an ad into the AdMob banner view.
        AdView adView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                //.addTestDevice("0109EABF5055E4716546558907BEA085") // REMOVE THIS IN PROD!!!!!!!
                .build();
        adView.loadAd(adRequest);
    }
}
