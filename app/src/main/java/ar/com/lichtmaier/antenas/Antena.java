package ar.com.lichtmaier.antenas;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.google.android.gms.maps.model.LatLng;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class Antena implements Serializable
{
	private static final long serialVersionUID = 1L;

	final public String descripción, ref;
	private final GlobalCoordinates c;
	public final int index;
	final public País país;

	public double dist;

	final static private List<Antena> antenas = new ArrayList<>();
	final static private List<Antena> antenasAlgoCerca = new ArrayList<>();
	final static private Map<País, List<Antena>> antenasPorPaís = new EnumMap<>(País.class);

	private Antena(String descripción, double lat, double lon, int index, País país, String ref)
	{
		this.descripción = descripción;
		this.index = index;
		c = new GlobalCoordinates(lat, lon);
		this.país = país;
		this.ref = ref;
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
		double latitud = coordsUsuario.getLatitude();
		double longitud = coordsUsuario.getLongitude();
		cargar(ctx, longitud > -27
			? EnumSet.of(País.UK)
			: ((latitud > 13)
				? (latitud < 40 ? EnumSet.of(País.US) : EnumSet.of(País.US, País.CA))
				: (latitud < -34 || (latitud < -18 && longitud < -58)
					? EnumSet.of(País.AR, País.UY)
					: EnumSet.of(País.AR, País.BR, País.UY))));
		if(antenasAlgoCerca.isEmpty())
			for(Antena antena : antenas)
				if(Math.abs(latitud - antena.c.getLatitude()) < 1)
					antenasAlgoCerca.add(antena);
		List<Antena> res = new ArrayList<>();
		for(Antena antena : antenasAlgoCerca)
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

	private synchronized static void cargar(Context ctx, Set<País> países)
	{
		for(País país : países)
			cargar(ctx, país);
	}

	private synchronized static void cargar(Context ctx, País país)
	{
		List<Antena> l = antenasPorPaís.get(país);
		if(l != null)
			return;
		long antes = System.currentTimeMillis();
		try {
			XmlPullParser xml = XmlPullParserFactory.newInstance().newPullParser();
			int res;
			switch(país)
			{
				case AR:
					res = R.raw.antenas_ar;
					break;
				case BR:
					res = R.raw.antenas_br;
					break;
				case CA:
					res = R.raw.antenas_ca;
					break;
				case UK:
					res = R.raw.antenas_uk;
					break;
				case US:
					res = R.raw.antenas_us;
					break;
				case UY:
					res = R.raw.antenas_uy;
					break;
				default:
					throw new RuntimeException(String.valueOf(país));
			}
			InputStream in = ctx.getResources().openRawResource(res);
			xml.setInput(in, "UTF-8");
			int t, index = 0;
			l = new ArrayList<>();
			antenasPorPaís.put(país, l);
			while( (t = xml.getEventType()) != XmlPullParser.END_DOCUMENT )
			{
				if(t == XmlPullParser.START_TAG)
				{
					String name = xml.getName();
					if(name.equals("antena"))
					{
						Antena antena = new Antena(xml.getAttributeValue(null, "desc"), Double.parseDouble(xml.getAttributeValue(null, "lat")), Double.parseDouble(xml.getAttributeValue(null, "lon")), index++, país, xml.getAttributeValue(null, "ref"));
						antenas.add(antena);
						l.add(antena);
					}
				}
				xml.next();
			}
			in.close();
		} catch (XmlPullParserException | IOException e)
		{
			throw new RuntimeException(e);
		}
		Log.i("antenas", l.size() + " antenas de " + país + " cargadas en " + (System.currentTimeMillis() - antes) + "ms");
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

	public static List<Antena> dameAntenas(Context ctx, País país)
	{
		cargar(ctx, país);
		return antenasPorPaís.get(país);
	}

	/** Devuelve una antena en base al número de orden.
	 *
	 * @param index el número de orden
	 * @return una antena
	 */
	public static Antena dameAntena(Context ctx, País país, int index)
	{
		cargar(ctx, país);
		return antenasPorPaís.get(país).get(index);
	}

	public void mostrarInformacion(Context ctx)
	{
		if(país != País.UK || ref == null)
			return;
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.ukfree.tv/txdetail.php?a=" + ref));
		ctx.startActivity(intent);
	}
}
