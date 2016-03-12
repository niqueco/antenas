package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationRequest;

import org.gavaghan.geodesy.GlobalCoordinates;

public class TVActivity extends FragmentActivity implements LocationClientCompat.Callback
{
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	private static final int PEDIDO_DE_PERMISO_FINE_LOCATION = 131;

	private LocationClientCompat locationClient;
	private AntenasAdapter antenasAdapter;
	private LocationManager locationManager;

	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) { }
		@Override
		public void onProviderEnabled(String provider) { }
		@Override
		public void onProviderDisabled(String provider) { }

		@Override
		public void onLocationChanged(Location location)
		{
			if(location.getAccuracy() > 300)
				return;
			nuevaUbicación(location);
		}
	};

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.tv_activity);
		final RecyclerView rv = (RecyclerView)findViewById(R.id.antenas);

		if(rv != null)
		{
			antenasAdapter = new AntenasAdapter(this, null, /* onAntenaClickedListener */ null, R.layout.antena_tv);
			rv.setAdapter(antenasAdapter);
		}

		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_FINE_LOCATION);
		} else
		{
			locationClient = new LocationClientCompat(this, LocationRequest.create()
					.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
					.setInterval(10000)
					.setFastestInterval(2000)
					.setSmallestDisplacement(10), this);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if(locationManager != null)
		{
			Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_COARSE);
			criteria.setCostAllowed(true);
			try
			{
				//noinspection MissingPermission
				Compat.requestLocationUpdates(locationManager, 1000 * 60, 0, criteria, locationListener);
			} catch(IllegalArgumentException e)
			{
				Log.e("antenas", "Error pidiendo updates de GPS", e);
				Toast.makeText(this, getString(R.string.no_ubicacion), Toast.LENGTH_SHORT).show();
				finish();
			}
		}
		if(locationClient != null)
			locationClient.onResume();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if(locationManager != null)
			//noinspection MissingPermission
			locationManager.removeUpdates(locationListener);
		if(locationClient != null)
			locationClient.onPause();
	}

	@Override
	protected void onStart()
	{
		Log.d("antenas", "hola!");
		super.onStart();
		if(locationClient != null)
			locationClient.onStart();
	}

	@Override
	protected void onStop()
	{
		if(locationClient != null)
			locationClient.onStop();
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		if(antenasAdapter != null)
			antenasAdapter.onDestroy();
		super.onDestroy();
	}

	@Override
	public void onConnected(Bundle bundle)
	{
		//noinspection MissingPermission
		Location location = locationClient.getLastLocation();
		if(location != null)
			nuevaUbicación(location);
		//noinspection MissingPermission
		locationClient.onConnected();
	}

	private void nuevaUbicación(Location location)
	{
		Log.d("anenas", "loc="+location);
		GlobalCoordinates coords = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
		antenasAdapter.nuevaUbicación(coords);
	}

	@Override
	public void onConnectionFailed(ConnectionResult r)
	{
		if(r.hasResolution())
		{
			Log.w("antenas", "Play Services: " + r);
			try
			{
				r.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch(IntentSender.SendIntentException e)
			{
				Log.e("antenas", "uh?", e);
			}
		} else
		{
			Log.e("antenas", "Play Services no disponible: " + r + ". No importa, sobreviviremos.");

			locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
			locationClient = null;
		}
	}

	@Override
	public void onLocationChanged(Location location)
	{
		nuevaUbicación(location);
	}
}
