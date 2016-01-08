package ar.com.lichtmaier.antenas;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.maps.android.PolyUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Polígono
{
	final List<LatLng> puntos;
	private final LatLngBounds bounds;

	public Polígono(List<LatLng> puntos, LatLngBounds bounds)
	{
		this.puntos = puntos;
		this.bounds = bounds;
	}

	public boolean contiene(LatLng punto)
	{
		return bounds.contains(punto) && PolyUtil.containsLocation(punto, puntos, false);
	}

	public LatLngBounds getBoundingBox()
	{
		return bounds;
	}

	public List<LatLng> getPuntos()
	{
		return Collections.unmodifiableList(puntos);
	}

	public byte[] aBytes()
	{
		try
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream s = new DataOutputStream(bos);
			for(LatLng punto : puntos)
			{
				s.writeFloat((float)punto.latitude);
				s.writeFloat((float)punto.longitude);
			}
			s.close();
			return bos.toByteArray();
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static Polígono deBytes(byte[] blob)
	{
		DataInputStream s = new DataInputStream(new ByteArrayInputStream(blob));
		try
		{
			Builder builder = new Builder();
			int n = blob.length / (Float.SIZE / 8) / 2;
			for(int i = 0 ; i < n; i++)
			{
				float lat = s.readFloat();
				float lon = s.readFloat();
				builder.add(new LatLng(lat, lon));
			}
			return builder.build();
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	static class Builder
	{
		final private LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
		final List<LatLng> puntos = new ArrayList<>();

		@SuppressWarnings("UnusedReturnValue")
		public Builder add(LatLng punto)
		{
			boundsBuilder.include(punto);
			puntos.add(punto);
			return this;
		}

		public Polígono build()
		{
			return new Polígono(puntos, boundsBuilder.build());
		}
	}
}
