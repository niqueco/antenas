package ar.com.lichtmaier.antenas.location;

import android.arch.lifecycle.LiveData;
import android.content.Intent;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.LocationRequest;

public abstract class LocationLiveData extends LiveData<Location>
{
	final float precisiónAceptable;

	LocationLiveData(float precisiónAceptable)
	{
		this.precisiónAceptable = precisiónAceptable;
	}

	@NonNull
	public static LocationLiveData create(FragmentActivity activity, LocationRequest locationRequest, PlayServicesLocationLiveData.Callback callback, float precisiónAceptable)
	{
		int googlePlayServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
		if(googlePlayServicesAvailable == ConnectionResult.SERVICE_MISSING || googlePlayServicesAvailable == ConnectionResult.SERVICE_INVALID)
		{
			Log.e("antenas", "Play Services no disponible. No importa, sobreviviremos.");
			return new LocationManagerLiveData(activity, precisiónAceptable);
		}
		return new PlayServicesLocationLiveData(activity, locationRequest, callback, precisiónAceptable);
	}

	public abstract void inicializarConPermiso();

	public abstract boolean onActivityResult(int requestCode, int resultCode, Intent data);
}
