package ar.com.lichtmaier.antenas;

import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;

public class MapCompat
{
	public static void getMapAsync(SupportMapFragment mapFragment, OnMapReadyCallback callback)
	{
		GoogleMap map = mapFragment.getMap();
		if(map == null)
		{
			Log.e("antenas", "Mapa no disponible!");
			return;
		}
		callback.onMapReady(map);
	}

	public interface OnMapReadyCallback
	{
		void onMapReady(GoogleMap googleMap);
	}
}
