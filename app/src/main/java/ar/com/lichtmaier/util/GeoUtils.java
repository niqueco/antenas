package ar.com.lichtmaier.util;

import android.location.Location;
import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import org.gavaghan.geodesy.GlobalCoordinates;

public class GeoUtils
{
	public static final String NO_PROVIDER = "*";

	public static Location toLocation(@NonNull GlobalCoordinates coords)
	{
		Location loc = new Location(NO_PROVIDER);
		loc.setLatitude(coords.getLatitude());
		loc.setLongitude(coords.getLongitude());
		return loc;
	}

	public static LatLng toLatLng(@NonNull Location location)
	{
		return new LatLng(location.getLatitude(), location.getLongitude());
	}

	public static GlobalCoordinates toGlobalCoordinates(@NonNull Location location)
	{
		return new GlobalCoordinates(location.getLatitude(), location.getLongitude());
	}
}
