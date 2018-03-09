package ar.com.lichtmaier.antenas.location;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.LocationRequest;

public abstract class LocationLiveData extends LiveData<Location>
{
	protected final Context context;
	final float precisiónAceptable;

	public final MutableLiveData<Boolean> disponibilidad = new MutableLiveData<>();

	LocationLiveData(Context context, float precisiónAceptable)
	{
		this.context = context.getApplicationContext();
		this.precisiónAceptable = precisiónAceptable;
		disponibilidad.setValue(true);
	}

	@NonNull
	public static LocationLiveData create(Context context, LocationRequest locationRequest, float precisiónAceptable)
	{
		int googlePlayServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
		if(googlePlayServicesAvailable != ConnectionResult.SUCCESS)
		{
			Log.e("antenas", "Play Services no disponible. No importa, sobreviviremos.");
			return new LocationManagerLiveData(context, precisiónAceptable);
		}
		return new PlayServicesLocationLiveData(context, locationRequest, precisiónAceptable);
	}

	public abstract void inicializarConPermiso(Activity activity);

	public abstract void verificarConfiguración(Activity activity);

	public abstract boolean onActivityResult(int requestCode, int resultCode, Intent data);
}
