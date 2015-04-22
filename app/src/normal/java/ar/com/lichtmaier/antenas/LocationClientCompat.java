package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
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
	private final AntenaActivity activity;
	private final LocationRequest locationRequest;
	private final int REQUEST_CHECK_SETTINGS = 9988;
	private static boolean noPreguntar;

	public LocationClientCompat(AntenaActivity activity, LocationRequest locationRequest)
	{
		this.activity = activity;
		this.locationRequest = locationRequest;
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
			LocationServices.FusedLocationApi.requestLocationUpdates(google, locationRequest, activity);
		else if(!google.isConnecting())
			google.connect();
	}

	public void onPause()
	{
		if(google.isConnected())
			LocationServices.FusedLocationApi.removeLocationUpdates(google, activity);
	}

	public void onStop()
	{
		google.disconnect();
	}

	public Location getLastLocation()
	{
		return LocationServices.FusedLocationApi.getLastLocation(google);
	}

	public void onConnected()
	{
		LocationServices.FusedLocationApi.requestLocationUpdates(google, locationRequest, activity);
	}

	public void connect()
	{
		google.connect();
	}

	@Override
	public void onConnected(Bundle bundle)
	{
		activity.onConnected(bundle);

		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
			.addLocationRequest(locationRequest);
		PendingResult<LocationSettingsResult> result =
				LocationServices.SettingsApi.checkLocationSettings(google, builder.build());
		result.setResultCallback(new ResultCallback<LocationSettingsResult>()
		{
			@Override
			public void onResult(LocationSettingsResult result)
			{
				Status status = result.getStatus();
				if(status.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED && !noPreguntar && !activity.huboSavedInstanceState)
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
	public void onConnectionFailed(ConnectionResult connectionResult)
	{
		activity.onConnectionFailed(connectionResult);
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
}
