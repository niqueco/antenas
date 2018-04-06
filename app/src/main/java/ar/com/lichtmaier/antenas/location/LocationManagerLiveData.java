package ar.com.lichtmaier.antenas.location;

import android.Manifest;
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
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import ar.com.lichtmaier.antenas.R;

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
	public void inicializarConPermiso(Activity activity)
	{
		if(Log.isLoggable(TAG, Log.DEBUG))
			Log.d(TAG, "Inicializando con Location Manager");
		if(ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;
		Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if(location != null && location.getAccuracy() < precisiónAceptable)
			setValue(location);
		else
		{
			location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			if(location != null && location.getAccuracy() < precisiónAceptable)
				setValue(location);
		}
		if(hasActiveObservers())
			onActive();
	}

	@Override
	public void verificarConfiguración(Activity activity) { }

	@Override
	protected void onActive()
	{
		if(Log.isLoggable(TAG, Log.DEBUG))
			Log.d(TAG, "active");
		if(ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;

		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_COARSE);
		criteria.setCostAllowed(true);

		try
		{
			locationManager.requestLocationUpdates(1000 * 60, 0, criteria, this, null);
		} catch(Exception e)
		{
			Crashlytics.logException(e);
			Toast.makeText(context, R.string.no_ubicacion, Toast.LENGTH_SHORT).show();
		}
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
