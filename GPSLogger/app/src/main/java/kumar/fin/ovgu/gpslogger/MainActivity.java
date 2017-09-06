package kumar.fin.ovgu.gpslogger;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private FusedLocationProviderClient mFusedLocationClient;
    final Handler ha=new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView t=(TextView)findViewById(R.id.box);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        int MY_PERMISSIONS_REQUEST_LOCATION = 0;
        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);
        }

        // The handler below runs every 3 seconds to update the last known coordinates...
        ha.postDelayed(new Runnable() {

            @Override
            public void run() {
                //call function
                setCoordinates();
                ha.postDelayed(this, 3000);
            }
        }, 3000);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setCoordinates() {
        final TextView t=(TextView)findViewById(R.id.box);
        t.setText("Calculating...");
        try {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        // ...
                        //Toast.makeText(MainActivity.this, "Coordinates = " + location.getLatitude() + ", " + location.getLongitude(), Toast.LENGTH_LONG).show();
                        Log.d("MyApp","Coordinates = " + location.getLatitude() + ", " + location.getLongitude());
                        t.setText("Coordinates = " + location.getLatitude() + ", " + location.getLongitude()
                                + "\nLast Updated at: " + new Date(System.currentTimeMillis())
                                + "\nLocation Provider: " + location.getProvider());
                    }
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void showToast(String message, int length_of_time) {
        Toast.makeText(MainActivity.this, message, length_of_time).show();
    }
}
