package ar.com.lichtmaier.antenas;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

@SuppressWarnings({"UnusedParameters", "SameReturnValue"})
public class LocationClientCompat implements GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener
{
	private final LocationClient locationClient;
	private final AntenaActivity activity;
	private final LocationRequest locationRequest;
	private final Callback callback;

	public LocationClientCompat(AntenaActivity activity, LocationRequest locationRequest, Callback callback)
	{
		this.activity = activity;
		this.locationRequest = locationRequest;
		this.callback = callback;
		locationClient = new LocationClient(activity, this, this);
	}

	public void onStart()
	{
		locationClient.connect();
	}

	public void onResume()
	{
		if(locationClient.isConnected())
			locationClient.requestLocationUpdates(locationRequest, callback);
		else if(!locationClient.isConnecting())
			locationClient.connect();
	}

	public void onPause()
	{
		if(locationClient.isConnected())
			locationClient.removeLocationUpdates(callback);
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
		locationClient.requestLocationUpdates(locationRequest, callback);
	}

	public void connect()
	{
		locationClient.connect();
	}

	@Override
	public void onConnected(Bundle bundle)
	{
		callback.onConnected(bundle);
	}

	@Override
	public void onDisconnected()
	{
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult)
	{
		callback.onConnectionFailed(connectionResult);
	}

	public boolean onActivityResult(int requestCode, int resultCode, Intent data)
	{
		return false;
	}

	interface Callback extends LocationListener
	{
		void onConnected(Bundle bundle);

		void onConnectionFailed(ConnectionResult connectionResult);
	}
}
