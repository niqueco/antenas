package ar.com.lichtmaier.antenas;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.LatLng;

import org.gavaghan.geodesy.GlobalCoordinates;

import ar.com.lichtmaier.antenas.location.LocationLiveData;
import ar.com.lichtmaier.util.GeoUtils;

/** Un lugar elegido para ser el centro de referencia de la información de antenas.
 */
class Lugar implements Parcelable
{
	static final MutableLiveData<Lugar> actual = new MutableLiveData<>();
	private static final String KEY = "lugar";
	private static boolean lugarSet = false;

	final GlobalCoordinates coords;
	final String name;

	private Lugar(LatLng latLng, String name)
	{
		this(new GlobalCoordinates(latLng.latitude, latLng.longitude), name);
	}

	private Lugar(GlobalCoordinates coords, String name)
	{
		this.coords = coords;
		this.name = name;
	}

	private Lugar(Parcel in)
	{
		coords = new GlobalCoordinates(in.readDouble(), in.readDouble());
		name = in.readString();
	}

	@NonNull
	static Lugar from(Place place)
	{
		return new Lugar(place.getLatLng(), place.getName().toString());
	}

	static void save(Bundle outState)
	{
		if(actual.getValue() != null)
			outState.putParcelable(KEY, actual.getValue());
	}

	static void restore(Bundle savedInstanceState)
	{
		if(lugarSet)
			return;
		lugarSet = true;

		if(savedInstanceState != null)
			actual.setValue(savedInstanceState.getParcelable(KEY));
	}

	@NonNull
	static LiveData<Location> dameUbicaciónFusionada(LocationLiveData realLocation)
	{
		LiveData<Location> location = Transformations.switchMap(actual, locActual -> locActual == null
				? realLocation
				: Transformations.map(actual, lugar -> GeoUtils.toLocation(lugar.coords)));

		// emito un valor para que se active la fuente bien
		actual.setValue(actual.getValue());
		return location;
	}

	public static final Creator<Lugar> CREATOR = new Creator<Lugar>()
	{
		@Override
		public Lugar createFromParcel(Parcel in)
		{
			return new Lugar(in);
		}

		@Override
		public Lugar[] newArray(int size)
		{
			return new Lugar[size];
		}
	};

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeDouble(coords.getLatitude());
		dest.writeDouble(coords.getLongitude());
		dest.writeString(name);
	}
}
