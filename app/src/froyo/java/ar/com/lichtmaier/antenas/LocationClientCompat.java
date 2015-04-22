package ar.com.lichtmaier.antenas;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;

@SuppressWarnings({"UnusedParameters", "SameReturnValue"})
public class LocationClientCompat implements GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener
{
	private final LocationClient locationClient;
	private final AntenaActivity activity;
	private final LocationRequest locationRequest;

	public LocationClientCompat(AntenaActivity activity, LocationRequest locationRequest)
	{
		this.activity = activity;
		this.locationRequest = locationRequest;
		locationClient = new LocationClient(activity, this, this);
	}

	public void onStart()
	{
		locationClient.connect();
	}

	public void onResume()
	{
		if(locationClient.isConnected())
			locationClient.requestLocationUpdates(locationRequest, activity);
		else if(!locationClient.isConnecting())
			locationClient.connect();
	}

	public void onPause()
	{
		if(locationClient.isConnected())
			locationClient.removeLocationUpdates(activity);
	}

	public void onStop()
	{
		locationClient.disconnect();
	}

	public Location getLastLocation()
	{
		return locationClient.getLastLocation();
	}

	public void onConnected()
	{
		activity.pedirCambioConfiguración();
		locationClient.requestLocationUpdates(locationRequest, activity);
	}

	public void connect()
	{
		locationClient.connect();
	}

	@Override
	public void onConnected(Bundle bundle)
	{
		activity.onConnected(bundle);
	}

	@Override
	public void onDisconnected()
	{
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult)
	{
		activity.onConnectionFailed(connectionResult);
	}

	public boolean onActivityResult(int requestCode, int resultCode, Intent data)
	{
		return false;
	}
}
