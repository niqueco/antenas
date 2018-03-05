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

	public AntenasViewModel(@NonNull Application application)
	{
		super(application);
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
