package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;

public class LocationClientCompat
{
	private final LocationClient locationClient;
	private final AntenaActivity activity;
	private LocationRequest locationRequest;

	public LocationClientCompat(AntenaActivity activity, LocationRequest locationRequest)
	{
		this.activity = activity;
		this.locationRequest = locationRequest;
		locationClient = new LocationClient(activity, activity, activity);
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
		locationClient.requestLocationUpdates(locationRequest, activity);
	}

	public void connect()
	{
		locationClient.connect();
	}
}
