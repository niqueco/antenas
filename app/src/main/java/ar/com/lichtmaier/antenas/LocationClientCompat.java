package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.Task;

import java.lang.ref.WeakReference;

@SuppressWarnings("WeakerAccess")
public class LocationClientCompat extends LiveData<Location>
{
	private final FusedLocationProviderClient flpc;
	private final Activity activity;
	private final LocationRequest locationRequest;
	private final int REQUEST_CHECK_SETTINGS = 9988;
	private static boolean noPreguntar;
	private final Callback callback;

	private LocationClientCompat(FragmentActivity activity, LocationRequest locationRequest, Callback callback)
	{
		this.activity = activity;
		this.locationRequest = locationRequest;
		this.callback = callback;

		locationRequest.setMaxWaitTime(locationRequest.getInterval() * 6);

		flpc = LocationServices.getFusedLocationProviderClient(activity);

		if(ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;
		Task<Location> task = flpc.getLastLocation();
		task.addOnCompleteListener(activity, t -> {
			try
			{
				Location lastLocation = t.getResult(ApiException.class);
				if(lastLocation != null && getValue() == null)
				{
					callback.onLocationChanged(lastLocation);
					setValue(lastLocation);
				}
			} catch(ApiException e)
			{
				Log.e("antenas", "Error", e);
				callback.onConnectionFailed();
			}
		});
		start();
		verificarConfiguración();
	}

	@Nullable
	public static LocationClientCompat create(FragmentActivity activity, LocationRequest locationRequest, Callback callback)
	{
		int googlePlayServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
		if(googlePlayServicesAvailable == ConnectionResult.SERVICE_MISSING || googlePlayServicesAvailable == ConnectionResult.SERVICE_INVALID)
		{
			callback.onConnectionFailed();
			return null;
		}
		return new LocationClientCompat(activity, locationRequest, callback);
	}

	public void start()
	{
		try {
			flpc.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
		} catch(SecurityException ignored) { }
	}

	private void verificarConfiguración()
	{
		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
			.addLocationRequest(locationRequest);
		Task<LocationSettingsResponse> result =
				LocationServices.getSettingsClient(activity).checkLocationSettings(builder.build());

		result.addOnCompleteListener(task -> {
			try
			{
				task.getResult(ApiException.class);
			} catch(ApiException e)
			{
				if(e.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED && !noPreguntar /* && !activity.huboSavedInstanceState */)
				{
					try {
						ResolvableApiException resolvable = (ResolvableApiException)e;
						resolvable.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS);
						noPreguntar = true;
					} catch(Exception e1)
					{
						e1.printStackTrace();
					}
				}
			}
		});

	}

	public void stop()
	{
		flpc.removeLocationUpdates(locationCallback);
	}

	public boolean onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(requestCode == REQUEST_CHECK_SETTINGS)
		{
			if(resultCode != Activity.RESULT_CANCELED)
				noPreguntar = false;
			Log.i("antenas", "resultCode=" + resultCode + " data="+ data);
			return true;
		}
		return false;
	}

	private static class MyLocationCallback extends LocationCallback
	{
		// because https://code.google.com/p/android/issues/detail?id=227856 =(
		final private WeakReference<LocationClientCompat> lcc;

		private MyLocationCallback(LocationClientCompat lcc)
		{
			this.lcc = new WeakReference<>(lcc);
		}

		@Override
		public void onLocationAvailability(LocationAvailability locationAvailability)
		{
			lcc.get().verificarConfiguración();
		}

		@Override
		public void onLocationResult(LocationResult result)
		{
			lcc.get().callback.onLocationChanged(result.getLastLocation());
			lcc.get().setValue(result.getLastLocation());
		}
	}
	final private LocationCallback locationCallback = new MyLocationCallback(this);

	interface Callback extends com.google.android.gms.location.LocationListener
	{
		void onConnectionFailed();
	}
}
