package jamalian.sina.fitnessassistant;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static android.R.attr.key;
import static jamalian.sina.fitnessassistant.R.id.map;


/**
 * This activity opens a map at the user's current location and shows nearby gyms.
 */
public class GymActivity extends AppCompatActivity
        implements OnMapReadyCallback,
        GoogleMap.OnCameraIdleListener,
        GoogleMap.OnInfoWindowClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // The tag used for logging.
    private static final String TAG = GymActivity.class.getSimpleName();

    // A default zoom.
    private static final int DEFAULT_ZOOM = 13;

    // For requesting location permissions.
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // The Google map and camera objects.
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // The entry point to Google Play services, used by the Places API and Fused Location Provider.
    private GoogleApiClient mGoogleApiClient;

    // The latitude and longitude for the desired location.
    private double lat;
    private double lng;

    // A flag to indicate if location permissions have been granted.
    private boolean mLocationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // Used for selecting the current place.
    private int mMaxEntries;
    private String[] mLikelyPlaceNames;
    private String[] mLikelyPlaceAddresses;
    private String[] mLikelyPlaceAttributions;
    private LatLng[] mLikelyPlaceLatLngs;

    // The API key needed to use Google Places API.
    private String apiKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_gym);

        // Set the initial latitude and longitude to an arbitrary location (Sydney, Australia).
        // This location will be used if the user does not grant location permissions.
        lat = -33.8523341;
        lng = 151.2106085;

        // Used for selecting the current place.
        mMaxEntries = 5;
        mLikelyPlaceNames = new String[mMaxEntries];
        mLikelyPlaceAddresses = new String[mMaxEntries];
        mLikelyPlaceAttributions = new String[mMaxEntries];
        mLikelyPlaceLatLngs = new LatLng[mMaxEntries];

        apiKey = "AIzaSyBF8cNquS3oGV87GI4gIBhywxNBKs86ljk";

        // Build the Play services client for use by the Fused Location Provider and the Places API.
        // Use the addApi() method to request the Google Places API and the Fused Location Provider.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,
                        this /* OnConnectionFailedListener */)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
        mGoogleApiClient.connect();
    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Builds the map when the Google Play services client is successfully connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Handles failure to connect to the Google Play services client.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        // Refer to the reference doc for ConnectionResult to see what error codes might
        // be returned in onConnectionFailed.
        Log.d(TAG, "Play services connection failed: ConnectionResult.getErrorCode() = "
                + result.getErrorCode());
    }

    /**
     * Handles suspension of the connection to the Google Play services client.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "Play services connection suspended");
    }

    /**
     * Sets up the options menu.
     * @param menu The options menu.
     * @return Boolean.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.current_place_menu, menu);
        return true;
    }

    /**
     * Handles a click on the menu option to get a place.
     * @param item The menu item to handle.
     * @return Boolean.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.option_get_place) {
            showCurrentPlace();
        }
        return true;
    }

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout)findViewById(R.id.map), false);

                TextView title = ((TextView) infoWindow.findViewById(R.id.title));
                title.setText(marker.getTitle());

                TextView snippet = ((TextView) infoWindow.findViewById(R.id.snippet));
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Set a camera listener to add new markers if the user moves the map around.
        mMap.setOnCameraIdleListener(this);

        // Set a listener to see if the user has pressed on the marker's info window.
        mMap.setOnInfoWindowClickListener(this);

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        // Retrieve and display nearby gyms.
        getGyms();
    }

    /**
     * Finds nearby gyms and adds markers to the map.
     */
    public void getGyms() {
        // For testing:
        // https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=43.9287854,-79.4567106&rankby=distance&sensor=true&types=gym&key=AIzaSyBF8cNquS3oGV87GI4gIBhywxNBKs86ljk

        String findGymsUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/"+
                "json?location="+mMap.getCameraPosition().target.latitude+","+mMap.getCameraPosition().target.longitude+
                "&rankby=distance&sensor=true"+
                "&types=gym"+
                "&key="+apiKey;

        // Execute a new AsyncTask to find nearby gyms.
        new GetGymsTask().execute(findGymsUrl, "marker");
    }

    /**
     * The camera has stopped moving so add the new nearby gym markers.
     */
    @Override
    public void onCameraIdle() {
        getGyms();
    }

    /**
     * The marker's info window has been pressed by the user, so show a detailed page for the gym.
     * @param marker The gynm's marker pressed by the user.
     */
    @Override
    public void onInfoWindowClick(Marker marker) {
        // Allow the user to go to the gym's website if there's one available.
        if (marker.getTag() != null) {
            String gymUrl = "https://maps.googleapis.com/maps/api/place/details/json?placeid="+marker.getTag()+"&key="+apiKey;

            // Execute a new AsyncTask to get the website for the selected gym.
            new GetGymsTask().execute(gymUrl, "website");
        }
        else {
            Toast.makeText(this, "No website available.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Retrieves nearby gym information in a background thread then adds the markers to the map.
     *
     * Extends AsyncTask<param1, param2, param3> where parameters represent:
     * param1: Params - type of parameters sent to task upon execution
     * param2: Progress - type of progress units published during background computation
     * param3: Result - the type of the result of the background computation
     *
     * doInBackground gets the nearby gyms information where the return type (String) matches Result.
     *
     * onPostExecute takes Result as its String parameter to create the markers on the map or provides
     *               additional details of a specific marker based on the flag given.
     *
     */
    private class GetGymsTask extends AsyncTask<String, Void, String> {

        private String flag;

        @Override
        protected String doInBackground(String... params) {
            // The url being searched to get nearby gyms information.
            String urlString = params[0];

            // marker flag will add the markers to the map.
            // website flag will go to the gym's website if available.
            flag = params[1];

            // Used to establish a connection to the given URL and retrieve the information.
            HttpURLConnection urlConnection = null;
            InputStream in;
            String data;

            try {
                // Establish a connection to Google Maps API.
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection)url.openConnection();
                urlConnection.connect();

                // Reads input stream in bytes and decodes into characters.
                in = new BufferedInputStream(urlConnection.getInputStream());
                data = readStream(in);

                // Result is the returned value, which is onPostExecute's parameter.
                return data;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                // Closes the connection.
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            // Connection was not established successfully.
            if (s == null) {
                return;
            }

            try {
                // Adds the markers to the map.
                if (flag.equals("marker")) {
                    createMarkersFromJson(s);
                }
                // Goes to the gym's website if available.
                else if (flag.equals("website")) {
                    goToWebsite(s);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Reads input stream in bytes and decodes into characters which is returned as a String.
     *
     * @param in The input stream from the URL.
     * @return The String representation of the input stream.
     * @throws IOException
     */
    private String readStream(InputStream in) throws IOException {
        // Converts byte stream into character stream.
        BufferedReader r = new BufferedReader(new InputStreamReader(in));

        StringBuilder sb = new StringBuilder();
        String line;

        // Reads every line and stores them in sb.
        while((line = r.readLine()) != null) {
            sb.append(line);
        }

        // Closes the input stream.
        in.close();

        return sb.toString();
    }

    /**
     * Creates markers on the map given the JSON String containing the nearby gyms information.
     *
     * @param json The JSON String containing the nearby gyms information.
     * @throws JSONException
     */
    private void createMarkersFromJson(String json) throws JSONException {
        // The JSON object containing the received information.
        JSONObject rootJSON = new JSONObject(json);

        // The results array containing gym objects.
        JSONArray resultsJSON = rootJSON.getJSONArray("results");

        // Create a new marker for each gym.
        for (int i = 0; i < resultsJSON.length(); i++) {
            JSONObject jsonObj = resultsJSON.getJSONObject(i);

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .title(jsonObj.getString("name"))
                    .snippet(jsonObj.getString("vicinity"))
                    .position(new LatLng(
                            jsonObj.getJSONObject("geometry").getJSONObject("location").getDouble("lat"),
                            jsonObj.getJSONObject("geometry").getJSONObject("location").getDouble("lng")
                    ))
            );

            // Set the place ID as the tag.
            marker.setTag(jsonObj.getString("place_id"));
        }
    }

    /**
     * Creates an intent to go to the gym's website.
     *
     * @param json The JSON String containing the selected gym details.
     * @throws JSONException
     */
    private void goToWebsite(String json) throws JSONException {
        // The JSON object containing the received information.
        JSONObject rootJSON = new JSONObject(json);

        // The result object containing detail objects for the gym.
        JSONObject resultJSON = rootJSON.getJSONObject("result");

        // Allow the user to go to the gym's website if there's one available.
        if (resultJSON.has("website")) {
            String website = resultJSON.getString("website");

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(website));
            startActivity(intent);
        }
        else {
            Toast.makeText(this, "No website available.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        if (mLocationPermissionGranted) {
            mLastKnownLocation = LocationServices.FusedLocationApi
                    .getLastLocation(mGoogleApiClient);
        }

        lat = mLastKnownLocation.getLatitude();
        lng = mLastKnownLocation.getLongitude();

        // Set the map's camera position to the current location of the device.
        if (mCameraPosition != null) {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(mCameraPosition));
        } else if (mLastKnownLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), DEFAULT_ZOOM));
        } else {
            Log.d(TAG, "Current location is null. Using defaults.");
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), DEFAULT_ZOOM));
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /**
     * Prompts the user to select the current place from a list of likely places, and shows the
     * current place on the map - provided the user has granted location permission.
     */
    private void showCurrentPlace() {
        if (mMap == null) {
            return;
        }

        if (mLocationPermissionGranted) {
            // Get the likely places - that is, the businesses and other points of interest that
            // are the best match for the device's current location.
            @SuppressWarnings("MissingPermission")
            PendingResult<PlaceLikelihoodBuffer> result = Places.PlaceDetectionApi
                    .getCurrentPlace(mGoogleApiClient, null);
            result.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>() {
                @Override
                public void onResult(@NonNull PlaceLikelihoodBuffer likelyPlaces) {
                    int i = 0;
                    mLikelyPlaceNames = new String[mMaxEntries];
                    mLikelyPlaceAddresses = new String[mMaxEntries];
                    mLikelyPlaceAttributions = new String[mMaxEntries];
                    mLikelyPlaceLatLngs = new LatLng[mMaxEntries];
                    for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                        // Build a list of likely places to show the user. Max 5.
                        mLikelyPlaceNames[i] = (String) placeLikelihood.getPlace().getName();
                        mLikelyPlaceAddresses[i] = (String) placeLikelihood.getPlace().getAddress();
                        mLikelyPlaceAttributions[i] = (String) placeLikelihood.getPlace()
                                .getAttributions();
                        mLikelyPlaceLatLngs[i] = placeLikelihood.getPlace().getLatLng();
                        Log.d("MyTag", placeLikelihood.toString());
                        i++;
                        if (i > (mMaxEntries - 1)) {
                            break;
                        }
                    }
                    // Release the place likelihood buffer, to avoid memory leaks.
                    likelyPlaces.release();

                    // Show a dialog offering the user the list of likely places, and add a
                    // marker at the selected place.
                    openPlacesDialog();
                }
            });
        } else {
            // Add a default marker, because the user hasn't selected a place.
            mMap.addMarker(new MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(new LatLng(lat, lng))
                    .snippet(getString(R.string.default_info_snippet)));
        }
    }

    /**
     * Displays a form allowing the user to select a place from a list of likely places.
     */
    private void openPlacesDialog() {
        // Ask the user to choose the place where they are now.
        DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // The "which" argument contains the position of the selected item.
                        LatLng markerLatLng = mLikelyPlaceLatLngs[which];
                        String markerSnippet = mLikelyPlaceAddresses[which];
                        if (mLikelyPlaceAttributions[which] != null) {
                            markerSnippet = markerSnippet + "\n" + mLikelyPlaceAttributions[which];
                        }
                        // Add a marker for the selected place, with an info window
                        // showing information about that place.
                        mMap.addMarker(new MarkerOptions()
                                .title(mLikelyPlaceNames[which])
                                .position(markerLatLng)
                                .snippet(markerSnippet));

                        // Position the map's camera at the location of the marker.
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                                DEFAULT_ZOOM));
                    }
                };

        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pick_place)
                .setItems(mLikelyPlaceNames, listener)
                .show();
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (mLocationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            mMap.setMyLocationEnabled(false);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            mLastKnownLocation = null;
        }
    }
}