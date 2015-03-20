package ar.com.lichtmaier.antenas;

import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class LocationClientCompat implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
{
	private final GoogleApiClient google;
	private final AntenaActivity activity;
	private LocationRequest locationRequest;

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
}
