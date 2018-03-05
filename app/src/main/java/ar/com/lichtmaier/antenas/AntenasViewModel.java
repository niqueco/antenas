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

	public AntenasViewModel(@NonNull Application application)
	{
		super(application);
	}

	public void init(boolean unaAntena)
	{
		this.unaAntena = unaAntena;
	}
}
