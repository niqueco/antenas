package ar.com.lichtmaier.antenas;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.location.LocationRequest;

import java.util.List;

import ar.com.lichtmaier.antenas.location.LocationLiveData;

public class AntenasViewModel extends AndroidViewModel
{
	@Nullable Brújula brújula;
	private boolean sinBrújula;

	@NonNull final LiveData<Location> location;
	@NonNull final LocationLiveData realLocation;

	private static final int PRECISIÓN_ACEPTABLE = 150;
	public LiveData<List<AntenasRepository.AntenaListada>> antenasAlrededor;

	public AntenasViewModel(@NonNull Application application)
	{
		super(application);

		realLocation = LocationLiveData.create(getApplication(), LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(10000)
				.setFastestInterval(2000)
				.setSmallestDisplacement(10), PRECISIÓN_ACEPTABLE);

		location = Lugar.dameUbicaciónFusionada(realLocation);
	}

	public void init(boolean unaAntena)
	{
		if(brújula == null && !sinBrújula)
		{
			brújula = Brújula.crear(getApplication());
			if(brújula == null)
				sinBrújula = true;
		}

		if(!unaAntena && antenasAlrededor == null)
		{
			AntenasRepository antenasRepository = new AntenasRepository(getApplication());
			antenasAlrededor = antenasRepository.dameAntenasAlrededor(location);
		}
	}

}
