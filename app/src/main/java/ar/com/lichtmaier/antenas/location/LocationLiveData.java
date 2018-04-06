package ar.com.lichtmaier.antenas.location;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.LocationRequest;

import ar.com.lichtmaier.antenas.BuildConfig;

public abstract class LocationLiveData extends LiveData<Location>
{
	protected final Context context;
	final float precisiónAceptable;

	public final MutableLiveData<Boolean> disponibilidad = new MutableLiveData<>();

	static final String TAG = "location";

	LocationLiveData(Context context, float precisiónAceptable)
	{
		this.context = context.getApplicationContext();
		this.precisiónAceptable = precisiónAceptable;
		disponibilidad.setValue(true);
	}

	@NonNull
	public static LocationLiveData create(Context context, LocationRequest locationRequest, float precisiónAceptable)
	{
		if((BuildConfig.DEBUG && Build.FINGERPRINT.contains("generic")) || GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS)
		{
			if(BuildConfig.DEBUG)
			{
				if(!Build.FINGERPRINT.contains("generic"))
					Log.w(TAG, "Play Services no disponible. No importa, sobreviviremos.");
				else
					Log.i(TAG, "Emulador detectado, usando location manager");
			} else
			{
				Crashlytics.log(Log.WARN, TAG, "Play Services no disponible. No importa, sobreviviremos.");
			}
			return new LocationManagerLiveData(context, precisiónAceptable);
		}
		return new PlayServicesLocationLiveData(context, locationRequest, precisiónAceptable);
	}

	public abstract void inicializarConPermiso(Activity activity);

	public abstract void verificarConfiguración(Activity activity);

	public abstract boolean onActivityResult(int requestCode, int resultCode, Intent data);

	protected void emitir(Location location)
	{
		float accuracy = location.getAccuracy();
		if(accuracy > precisiónAceptable)
		{
			Log.i(TAG, "Rechazando ubicación de poca precisión (" + accuracy + "m)");
			return;
		}
		if(Log.isLoggable(TAG, Log.DEBUG))
			Log.d(TAG, "loc: " + location);
		setValue(location);
	}
}
