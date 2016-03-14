package ar.com.lichtmaier.antenas;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;

public class MapCompat
{
	public static void getMapAsync(SupportMapFragment mapFragment, final OnMapReadyCallback callback)
	{
		mapFragment.getMapAsync(new com.google.android.gms.maps.OnMapReadyCallback()
		{
			@Override
			public void onMapReady(GoogleMap googleMap)
			{
				callback.onMapReady(googleMap);
			}
		});
	}

	public interface OnMapReadyCallback
	{
		void onMapReady(GoogleMap googleMap);
	}
}
