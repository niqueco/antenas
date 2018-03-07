package ar.com.lichtmaier.antenas;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.location.LocationRequest;

import ar.com.lichtmaier.antenas.location.LocationLiveData;

public class AntenasViewModel extends AndroidViewModel
{
	private boolean unaAntena;

	@Nullable Brújula brújula;
	private boolean sinBrújula;

	@NonNull LocationLiveData locationLiveData;

	private static final int PRECISIÓN_ACEPTABLE = 150;

	public AntenasViewModel(@NonNull Application application)
	{
		super(application);

		locationLiveData = LocationLiveData.create(getApplication(), LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(10000)
				.setFastestInterval(2000)
				.setSmallestDisplacement(10), PRECISIÓN_ACEPTABLE);
	}

	public void init(boolean unaAntena)
	{
		this.unaAntena = unaAntena;

		if(brújula == null && !sinBrújula)
		{
			brújula = Brújula.crear(getApplication());
			if(brújula == null)
				sinBrújula = true;
		}
	}
}
