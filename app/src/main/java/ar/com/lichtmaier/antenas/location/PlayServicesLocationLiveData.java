package ar.com.lichtmaier.antenas.location;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.Task;

import java.lang.ref.WeakReference;

public class PlayServicesLocationLiveData extends LocationLiveData
{
	private FusedLocationProviderClient flpc;
	private final LocationRequest locationRequest;
	private final int REQUEST_CHECK_SETTINGS = 9988;
	private static boolean noPreguntar;

	PlayServicesLocationLiveData(Context context, LocationRequest locationRequest, float precisiónAceptable)
	{
		super(context, precisiónAceptable);
		this.locationRequest = locationRequest;

		locationRequest.setMaxWaitTime(locationRequest.getInterval() * 6);
	}

	@Override
	public void inicializarConPermiso(Activity activity)
	{
		if(ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;
		flpc = LocationServices.getFusedLocationProviderClient(context);
		if(hasActiveObservers())
			flpc.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
		Task<Location> task = flpc.getLastLocation();
		task.addOnCompleteListener(t -> {
			try
			{
				Location lastLocation = t.getResult(ApiException.class);
				if(lastLocation != null && getValue() == null)
					setValue(lastLocation);
			} catch(ApiException e)
			{
				Crashlytics.logException(e);
			}
		});
		verificarConfiguración(activity);
	}

	@Override
	protected void onActive()
	{
		try {
			if(flpc != null)
				flpc.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
		} catch(SecurityException ignored) { }
	}

	@Override
	public void verificarConfiguración(Activity activity)
	{
		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
			.addLocationRequest(locationRequest);
		Task<LocationSettingsResponse> result =
				LocationServices.getSettingsClient(context).checkLocationSettings(builder.build());

		disponibilidad.setValue(true);

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
						Log.e("antenas", "Error configurando ubicación.", e1);
					}
				}
			}
		});
	}

	@Override
	protected void onInactive()
	{
		if(flpc != null)
			flpc.removeLocationUpdates(locationCallback);
	}

	@Override
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
		final private WeakReference<PlayServicesLocationLiveData> lcc;

		private MyLocationCallback(PlayServicesLocationLiveData lcc)
		{
			this.lcc = new WeakReference<>(lcc);
		}

		@Override
		public void onLocationAvailability(LocationAvailability locationAvailability)
		{
			lcc.get().disponibilidad.setValue(false);
		}

		@Override
		public void onLocationResult(LocationResult result)
		{
			Location loc = result.getLastLocation();
			if(loc != null)
				lcc.get().setValue(loc);
		}
	}
	final private LocationCallback locationCallback = new MyLocationCallback(this);
}
