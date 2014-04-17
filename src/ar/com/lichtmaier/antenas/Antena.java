package ar.com.lichtmaier.antenas;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.google.android.gms.maps.model.LatLng;

import android.content.Context;
import android.content.res.XmlResourceParser;

public class Antena implements Serializable
{
	private static final long serialVersionUID = 1L;

	final public String descripción;
	private final GlobalCoordinates c;

	public double dist;

	final static private List<Antena> antenas = new ArrayList<>();

	private Antena(String descripción, double lat, double lon)
	{
		this.descripción = descripción;
		c = new GlobalCoordinates(lat, lon);
	}

	@Override
	public String toString()
	{
		return descripción;
	}

	public LatLng getLatLng()
	{
		return new LatLng(c.getLatitude(), c.getLongitude());
	}

	private static final class DistComparator implements Comparator<Antena>
	{
		@Override
		public int compare(Antena lhs, Antena rhs)
		{
			return Double.compare(lhs.dist, rhs.dist);
		}
	}
	final static private DistComparator distComparator = new DistComparator();

	public static List<Antena> dameAntenasCerca(Context ctx, GlobalCoordinates coordsUsuario)
	{
		if(antenas.isEmpty())
			cargar(ctx);
		List<Antena> res = new ArrayList<>();
		for(Antena antena : antenas)
			if((antena.dist = antena.distanceTo(coordsUsuario)) < 60000)
				res.add(antena);
		Collections.sort(res, distComparator);
		ListIterator<Antena> it = res.listIterator(res.size());
		while(it.previousIndex() > 4)
			if(it.previous().dist > 30000)
				it.remove();
		return res;
	}

	private static void cargar(Context ctx)
	{
		try {
			XmlResourceParser xml = ctx.getResources().getXml(R.xml.antenas);
			int t;
			while( (t = xml.getEventType()) != XmlPullParser.END_DOCUMENT )
			{
				if(t == XmlPullParser.START_TAG && xml.getName().equals("antena"))
					antenas.add(new Antena(xml.getAttributeValue(null, "desc"), Double.parseDouble(xml.getAttributeValue(null, "lat")), Double.parseDouble(xml.getAttributeValue(null, "lon"))));
				xml.next();
			}
			xml.close();
		} catch (XmlPullParserException e)
		{
			throw new RuntimeException(e);
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private GlobalCoordinates coordsCache = null;
	private double rumboCacheado, distCacheada;

	public double distanceTo(GlobalCoordinates coords)
	{
		calcular(coords);
		return distCacheada;
	}

	public double rumboDesde(GlobalCoordinates coords)
	{
		calcular(coords);
		return rumboCacheado;
	}

	private void calcular(GlobalCoordinates coords)
	{
		if(coords == coordsCache || coords.equals(coordsCache))
			return;
		GeodeticCurve curva = GeodeticCalculator.calculateGeodeticCurve(Ellipsoid.WGS84, coords, c);
		rumboCacheado = curva.getAzimuth();
		distCacheada = curva.getEllipsoidalDistance();
		coordsCache = coords;
	}

	public static List<Antena> dameAntenas(Context ctx)
	{
		if(antenas.isEmpty())
			cargar(ctx);
		return antenas;
	}
}
