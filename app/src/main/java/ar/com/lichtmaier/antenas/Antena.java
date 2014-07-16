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
import android.preference.PreferenceManager;

public class Antena implements Serializable
{
	private static final long serialVersionUID = 1L;

	final public String descripción;
	private final GlobalCoordinates c;
	public final int index;

	public double dist;

	final static private List<Antena> antenas = new ArrayList<>();

	private Antena(String descripción, double lat, double lon, int index)
	{
		this.descripción = descripción;
		this.index = index;
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

	public static List<Antena> dameAntenasCerca(Context ctx, GlobalCoordinates coordsUsuario, int maxDist, boolean mostrarMenos)
	{
		if(antenas.isEmpty())
			cargar(ctx);
		List<Antena> res = new ArrayList<>();
		PreferenceManager.getDefaultSharedPreferences(ctx);
		for(Antena antena : antenas)
			if((antena.dist = antena.distanceTo(coordsUsuario)) < maxDist)
				res.add(antena);
		Collections.sort(res, distComparator);
		if(mostrarMenos)
		{
			ListIterator<Antena> it = res.listIterator(res.size());
			while(it.previousIndex() > 4)
				if(it.previous().dist > maxDist / 2)
					it.remove();
		}
		return res;
	}

	private static void cargar(Context ctx)
	{
		try {
			XmlResourceParser xml = ctx.getResources().getXml(R.xml.antenas);
			int t, index = 0;
			while( (t = xml.getEventType()) != XmlPullParser.END_DOCUMENT )
			{
				if(t == XmlPullParser.START_TAG && xml.getName().equals("antena"))
					antenas.add(new Antena(xml.getAttributeValue(null, "desc"), Double.parseDouble(xml.getAttributeValue(null, "lat")), Double.parseDouble(xml.getAttributeValue(null, "lon")), index++));
				xml.next();
			}
			xml.close();
		} catch (XmlPullParserException | IOException e)
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

	/** Devuelve una antena en base al número de orden.
	 *
	 * @param index el número de orden
	 * @return una antena
	 */
	public static Antena dameAntena(Context ctx, int index)
	{
		if(antenas.isEmpty())
			cargar(ctx);
		return antenas.get(index);
	}
}
