package ar.com.lichtmaier.antenas.location;

import android.arch.lifecycle.LiveData;
import android.location.Location;
import android.support.annotation.NonNull;

public interface TieneLocation
{
	@NonNull LiveData<Location> getLocation();
}
