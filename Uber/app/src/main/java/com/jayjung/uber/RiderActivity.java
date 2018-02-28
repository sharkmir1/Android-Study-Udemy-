package com.jayjung.uber;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.List;

public class RiderActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    LocationManager locationManager;
    LocationListener locationListener;
    Button uberbutton;
    Boolean requestActive = false;
    TextView infoTextView;
    boolean driverActive = false;

    Handler handler = new Handler();

    public void checkForUpdates() {
        ParseQuery<ParseObject> query = ParseQuery.getQuery("Request");
        query.whereEqualTo("username", ParseUser.getCurrentUser().getUsername());
        query.whereExists("driverUserName");

        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {
                if (e == null && objects.size() > 0) {

                    driverActive = true;

                    ParseQuery<ParseUser> query = ParseUser.getQuery();
                    query.whereEqualTo("username", objects.get(0).getString("driverUserName"));

                    query.findInBackground(new FindCallback<ParseUser>() {
                        @Override
                        public void done(List<ParseUser> objects, ParseException e) {
                            if (e == null && objects.size() > 0) {

                                ParseGeoPoint driverLocation = objects.get(0).getParseGeoPoint("location");
                                if (ContextCompat.checkSelfPermission(RiderActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                                    Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                                    if (lastKnownLocation != null) {
                                        ParseGeoPoint userLocation = new ParseGeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

                                        Double distanceInKilometers = driverLocation.distanceInKilometersTo(userLocation);
                                        Double distanceOneDp = (double) Math.round(distanceInKilometers * 10) / 10;

                                        if (distanceInKilometers < 0.01) {
                                            infoTextView.setText("Your Driver has arrived!");
                                            handler.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    uberbutton.setVisibility(View.VISIBLE);
                                                    uberbutton.setText("Call An Uber");
                                                    requestActive = false;
                                                    driverActive = false;

                                                    ParseQuery<ParseObject> query = ParseQuery.getQuery("Request");
                                                    query.whereEqualTo("username", ParseUser.getCurrentUser().getUsername());

                                                    query.findInBackground(new FindCallback<ParseObject>() {
                                                        @Override
                                                        public void done(List<ParseObject> objects, ParseException e) {
                                                            if (e == null) {
                                                                for (ParseObject object : objects) {
                                                                    object.deleteInBackground();
                                                                }
                                                            }
                                                        }
                                                    });


                                                }
                                            }, 5000);


                                        } else {
                                            infoTextView.setText("Your Driver is " + distanceOneDp + "km on the way!");


                                            LatLng requestLocationLatLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                                            LatLng driverLocationLatLng = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());

                                            final ArrayList<Marker> markers = new ArrayList<Marker>();
                                            markers.add(mMap.addMarker(new MarkerOptions().position(requestLocationLatLng).title("Request Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))));
                                            markers.add(mMap.addMarker(new MarkerOptions().position(driverLocationLatLng).title("Driver's Location")));

                                            ConstraintLayout constraintLayout = (ConstraintLayout) findViewById(R.id.constraintLayout);
                                            constraintLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                                @Override
                                                public void onGlobalLayout() {
                                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                                    for (Marker marker : markers) {
                                                        builder.include(marker.getPosition());
                                                    }
                                                    LatLngBounds bounds = builder.build();

                                                    int padding = 100;
                                                    CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);

                                                    mMap.animateCamera(cu);

                                                    uberbutton.setVisibility(View.INVISIBLE);
                                                    handler.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            checkForUpdates();
                                                        }
                                                    }, 2000);
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                        }
                    });

                }

            }
        });
    }

    public void logOut(View view) {
        ParseUser.logOut();
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
    }


    public void callUber(View view) {

        if (requestActive) {
            ParseQuery<ParseObject> query = ParseQuery.getQuery("Request");
            query.whereEqualTo("username", ParseUser.getCurrentUser().getUsername());

            query.findInBackground(new FindCallback<ParseObject>() {
                @Override
                public void done(List<ParseObject> objects, ParseException e) {
                    if (e == null) {
                        if (objects.size() > 0) {

                            for (ParseObject object : objects) {
                                object.deleteInBackground();

                            }
                            requestActive = false;
                            uberbutton.setText("Call An Uber");
                            checkForUpdates();
                        }
                    }
                }
            });

        } else {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (lastKnownLocation != null) {
                    ParseObject request = new ParseObject("Request");
                    request.put("username", ParseUser.getCurrentUser().getUsername());
                    ParseGeoPoint parseGeoPoint = new ParseGeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                    request.put("location", parseGeoPoint);
                    request.saveInBackground(new SaveCallback() {
                        @Override
                        public void done(ParseException e) {
                            if (e == null) {
                                uberbutton.setText("Cancel Uber");
                                requestActive = true;
                                checkForUpdates();
                            }
                        }
                    });
                } else {
                    Toast.makeText(this, "Could not find location. Try Again Later.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                    Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                    updateMap(lastKnownLocation);
                }
            }
        }
    }

    public void updateMap(Location location) {

        if (!driverActive) {
            LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.clear(); //clears any marker already on map
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
            mMap.addMarker(new MarkerOptions().position(userLocation).title("Your Location"));

        }
        }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        uberbutton = (Button)findViewById(R.id.callUberButton);
        infoTextView = (TextView)findViewById(R.id.infoTextView);

        ParseQuery<ParseObject> query = ParseQuery.getQuery("Request");
        query.whereEqualTo("username", ParseUser.getCurrentUser().getUsername());

        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {
                if (e == null) {
                    if (objects.size() > 0) {
                        requestActive = true;
                        uberbutton.setText("Cancel Uber");
                        checkForUpdates();
                    }
                }
            }
        });
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                updateMap(location);

            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (lastKnownLocation != null) {
                updateMap(lastKnownLocation);
            }
        }


    }
}
