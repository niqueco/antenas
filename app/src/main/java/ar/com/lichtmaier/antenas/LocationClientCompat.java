package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.*;

public class LocationClientCompat implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
{
	private final GoogleApiClient google;
	private final Activity activity;
	private final LocationRequest locationRequest;
	private final int REQUEST_CHECK_SETTINGS = 9988;
	private static boolean noPreguntar;
	private final Callback callback;

	public LocationClientCompat(Activity activity, LocationRequest locationRequest, Callback callback)
	{
		this.activity = activity;
		this.locationRequest = locationRequest;
		this.callback = callback;
		locationRequest.setMaxWaitTime(locationRequest.getInterval() * 6);
		google = new GoogleApiClient.Builder(activity).addApi(LocationServices.API)
			.addConnectionCallbacks(this)
			.addOnConnectionFailedListener(this)
			.build();
	}

	public void onStart()
	{
		google.connect();
	}

	public void onResume()
	{
		if(google.isConnected())
			//noinspection MissingPermission
			LocationServices.FusedLocationApi.requestLocationUpdates(google, locationRequest, locationCallback, Looper.getMainLooper());
		else if(!google.isConnecting())
			google.connect();
	}

	public void onPause()
	{
		if(google.isConnected())
			LocationServices.FusedLocationApi.removeLocationUpdates(google, locationCallback);
	}

	public void onStop()
	{
		google.disconnect();
	}

	@RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
	public Location getLastLocation()
	{
		return LocationServices.FusedLocationApi.getLastLocation(google);
	}

	@RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
	public void onConnected()
	{
		LocationServices.FusedLocationApi.requestLocationUpdates(google, locationRequest, locationCallback, Looper.getMainLooper());
	}

	public void connect()
	{
		google.connect();
	}

	@Override
	public void onConnected(Bundle bundle)
	{
		callback.onConnected(bundle);

		verificarConfiguración();
	}

	private void verificarConfiguración()
	{
		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
			.addLocationRequest(locationRequest);
		PendingResult<LocationSettingsResult> result =
				LocationServices.SettingsApi.checkLocationSettings(google, builder.build());
		result.setResultCallback(new ResultCallback<LocationSettingsResult>()
		{
			@Override
			public void onResult(@NonNull LocationSettingsResult result)
			{
				Status status = result.getStatus();
				if(status.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED && !noPreguntar /* && !activity.huboSavedInstanceState */)
				{
					try
					{
						status.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS);
						noPreguntar = true;
					} catch(IntentSender.SendIntentException ignored)
					{
					}
				}
			}
		});
	}

	@Override
	public void onConnectionSuspended(int i)
	{
	}

	@Override
	public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
	{
		callback.onConnectionFailed(connectionResult);
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

	final private LocationCallback locationCallback = new LocationCallback()
	{
		@Override
		public void onLocationAvailability(LocationAvailability locationAvailability)
		{
			verificarConfiguración();
		}

		@Override
		public void onLocationResult(LocationResult result)
		{
			callback.onLocationChanged(result.getLastLocation());
		}
	};

	interface Callback extends com.google.android.gms.location.LocationListener
	{
		void onConnected(Bundle bundle);

		void onConnectionFailed(ConnectionResult connectionResult);
	}
}