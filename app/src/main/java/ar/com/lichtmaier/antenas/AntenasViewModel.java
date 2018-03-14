package ar.com.lichtmaier.antenas;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Transformations;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.location.LocationRequest;

import org.gavaghan.geodesy.GlobalCoordinates;

import java.util.List;

import ar.com.lichtmaier.antenas.location.LocationLiveData;

public class AntenasViewModel extends AndroidViewModel
{
	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	private boolean unaAntena;

	@Nullable Brújula brújula;
	private boolean sinBrújula;

	@NonNull final LiveData<Location> location;
	@NonNull final LocationLiveData realLocation;

	private static final int PRECISIÓN_ACEPTABLE = 150;
	public static final String NO_PROVIDER = "*";
	public LiveData<List<AntenasRepository.AntenaListada>> antenasAlrededor;

	public AntenasViewModel(@NonNull Application application)
	{
		super(application);

		realLocation = LocationLiveData.create(getApplication(), LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(10000)
				.setFastestInterval(2000)
				.setSmallestDisplacement(10), PRECISIÓN_ACEPTABLE);

		location = Transformations.switchMap(Lugar.actual, locActual -> locActual == null
				? realLocation
				: Transformations.map(Lugar.actual, lugar -> toLocation(lugar.coords)));

		// emito un valor para que se active la fuente bien
		Lugar.actual.setValue(Lugar.actual.getValue());
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

		if(!unaAntena && antenasAlrededor == null)
		{
			AntenasRepository antenasRepository = new AntenasRepository(getApplication());
			antenasAlrededor = antenasRepository.dameAntenasAlrededor(location);
		}
	}

	private static Location toLocation(@NonNull GlobalCoordinates coords)
	{
		Location loc = new Location(NO_PROVIDER);
		loc.setLatitude(coords.getLatitude());
		loc.setLongitude(coords.getLongitude());
		return loc;
	}
}
