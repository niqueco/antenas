package ar.com.lichtmaier.antenas.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

public class LocationManagerLiveData extends LocationLiveData implements LocationListener
{
	private final LocationManager locationManager;

	LocationManagerLiveData(Context ctx, float precisiónAceptable)
	{
		super(ctx, precisiónAceptable);
		locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		if(locationManager == null)
			throw new NullPointerException();
	}

	@Override
	@SuppressLint("MissingPermission")
	public void inicializarConPermiso(Activity activity)
	{
		Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if(location != null && location.getAccuracy() < precisiónAceptable)
			setValue(location);
		else
		{
			location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			if(location != null && location.getAccuracy() < precisiónAceptable)
				setValue(location);
		}
	}

	@Override
	public void verificarConfiguración(Activity activity) { }

	@SuppressLint("MissingPermission")
	@Override
	protected void onActive()
	{
		if(ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;

		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_COARSE);
		criteria.setCostAllowed(true);

		locationManager.requestLocationUpdates(1000 * 60, 0, criteria, this, null);
	}

	@Override
	protected void onInactive()
	{
		locationManager.removeUpdates(this);
	}

	@Override
	public void onLocationChanged(Location location)
	{
		emitir(location);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) { }

	@Override
	public void onProviderEnabled(String provider) { }

	@Override
	public void onProviderDisabled(String provider) { }

	@Override
	public boolean onActivityResult(int requestCode, int resultCode, Intent data)
	{
		return false;
	}
}
