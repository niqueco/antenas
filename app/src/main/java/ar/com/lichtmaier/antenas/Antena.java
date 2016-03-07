package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import com.github.davidmoten.geo.Coverage;
import com.github.davidmoten.geo.GeoHash;
import com.google.android.gms.maps.model.LatLng;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

public class Antena implements Serializable
{
	private static final long serialVersionUID = 1L;

	final public String descripción, ref, geohash;
	private final GlobalCoordinates c;
	final public float potencia;
	public final int index;
	final public País país;
	public List<Canal> canales;

	public double dist;

	final static private Map<País, List<Antena>> antenasPorPaís = new EnumMap<>(País.class);
	final static private SortedMap<String, List<Antena>> geohashAAntenas = new TreeMap<>();

	private Antena(String descripción, double lat, double lon, int index, País país, String ref, float potencia)
	{
		this.descripción = descripción;
		this.index = index;
		this.potencia = potencia;
		c = new GlobalCoordinates(lat, lon);
		this.país = país;
		this.ref = ref;
		geohash = GeoHash.encodeHash(lat, lon, 4);
		List<Antena> l = geohashAAntenas.get(geohash);
		if(l == null)
		{
			l = new ArrayList<>();
			geohashAAntenas.put(geohash, l);
		}
		l.add(this);
	}

	private transient String nombre = null;
	private transient Locale localeNombre = null;

	public String dameNombre(Context context)
	{
		Locale locale = context.getResources().getConfiguration().locale;
		if(nombre == null || !locale.equals(localeNombre))
		{
			StringBuilder sb = new StringBuilder();
			if(descripción != null)
			{
				sb.append(descripción);
				if(canales != null && !canales.isEmpty())
					sb.append(" (");
			}
			if(canales != null)
				sb.append(dameDetalleCanales(context));

			if(descripción != null && canales != null && !canales.isEmpty())
				sb.append(")");
			nombre = sb.toString();
			localeNombre = locale;
		}
		return nombre;
	}

	private transient CharSequence detalleCanales = null;
	private transient Locale localeDetalleCanales = null;

	public CharSequence dameDetalleCanales(Context context)
	{
		if(canales == null || canales.isEmpty())
			return null;
		if(canales.size() > 12)
		{
			SpannableString spannableString = new SpannableString(context.getResources().getQuantityString(R.plurals.senales, canales.size(), canales.size()));
			spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), 0);
			return spannableString;
		}
		Locale locale = context.getResources().getConfiguration().locale;
		if(detalleCanales != null && locale.equals(localeDetalleCanales))
			return detalleCanales;

		SpannableStringBuilder sb = new SpannableStringBuilder();
		boolean primero = true;
		for(Canal canal : canales)
		{
			if(primero)
				primero = false;
			else
				sb.append(", ");
			if(canal.nombre != null)
				sb.append(canal.nombre);
			if(canal.numero != null && (canal.nombre == null || !canal.númeroEnElNombre()))
			{
				int desde = 0;
				if(canal.nombre != null)
				{
					desde = sb.length() + 1;
					sb.append(" (");
				}
				sb.append(context.getString(R.string.ch_number, canal.numero));
				if(canal.nombre != null)
				{
					sb.append(")");
					sb.setSpan(new RelativeSizeSpan(.8f), desde, sb.length(), 0);
				}
			}
		}
		detalleCanales = sb;
		localeDetalleCanales = locale;
		return detalleCanales;
	}

	@NonNull
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
		cargar(ctx, longitud > -32
			? (longitud < 60 ? EnumSet.of(País.UK, País.PT) : EnumSet.of(País.AU, País.NZ))
			: ((latitud > 13)
				? (latitud < 40 ? EnumSet.of(País.US) : EnumSet.of(País.US, País.CA))
				: (latitud < -34 || (latitud < -18 && longitud < -58)
					? EnumSet.of(País.AR, País.UY)
					: EnumSet.of(País.AR, País.BR, País.CO, País.UY))));
		List<Antena> res = new ArrayList<>();
		double distance = Math.hypot(maxDist, maxDist);
		GlobalCoordinates topLeftCoords = GeodeticCalculator.calculateEndingGlobalCoordinates(Ellipsoid.WGS84, coordsUsuario, 315, distance);
		GlobalCoordinates bottomRightCoords = GeodeticCalculator.calculateEndingGlobalCoordinates(Ellipsoid.WGS84, coordsUsuario, 135, distance);
		antenasEnRectángulo(topLeftCoords.getLatitude(), topLeftCoords.getLongitude(),
				bottomRightCoords.getLatitude(), bottomRightCoords.getLongitude(),
				res);
		for(Iterator<Antena> it = res.iterator(); it.hasNext() ; )
		{
			Antena antena = it.next();
			if((antena.dist = antena.distanceTo(coordsUsuario)) > maxDist)
				it.remove();
		}
		Collections.sort(res, distComparator);
		if(mostrarMenos)
		{
			ListIterator<Antena> it = res.listIterator(res.size());
			while(it.previousIndex() > 4)
				if(it.previous().dist > (maxDist * 2) / 3)
					it.remove();
		}
		return res;
	}

	public static void antenasEnRectángulo(double topLeftLat, double topLeftLon, double bottomRightLat, double bottomRightLon, @NonNull List<Antena> antenas)
	{
		if(topLeftLon > bottomRightLon)
		{
			antenasEnRectángulo(topLeftLat, topLeftLon, bottomRightLat, 180, antenas);
			antenasEnRectángulo(topLeftLat, -180, bottomRightLat, bottomRightLon, antenas);
			return;
		}
		Coverage coverage = GeoHash.coverBoundingBox(topLeftLat, topLeftLon, bottomRightLat, bottomRightLon);
		if(coverage == null)
		{
			Log.w("antenas", "mapa coverBoundingBox(" + topLeftLat + ", " + topLeftLon + ", "
					+ bottomRightLat + ", " + bottomRightLon + ") dio null");
			return;
		}
		for(String hash : coverage.getHashes())
			for(Map.Entry<String, List<Antena>> e : geohashAAntenas.subMap(hash, hashMásUno(hash)).entrySet())
				antenas.addAll(e.getValue());
	}

	@NonNull
	private static String hashMásUno(String hash)
	{
		int len = hash.length();
		return hash.substring(0, len - 1) + (char)((hash.charAt(len - 1) + 1));
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
		long antes = System.nanoTime();
		try {
			XmlPullParser xml = XmlPullParserFactory.newInstance().newPullParser();
			int res;
			switch(país)
			{
				case AR:
					res = R.raw.antenas_ar;
					break;
				case AU:
					res = R.raw.antenas_au;
					break;
				case BR:
					res = R.raw.antenas_br;
					break;
				case CA:
					res = R.raw.antenas_ca;
					break;
				case CO:
					res = R.raw.antenas_co;
					break;
				case NZ:
					res = R.raw.antenas_nz;
					break;
				case PT:
					res = R.raw.antenas_pt;
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
			Antena antena = null;
			while( (t = xml.getEventType()) != XmlPullParser.END_DOCUMENT )
			{
				switch(t)
				{
					case XmlPullParser.START_TAG:
						String name = xml.getName();
						switch(name)
						{
							case "antena":
								String pot = xml.getAttributeValue(null, "potencia");
								antena = new Antena(xml.getAttributeValue(null, "desc"),
										Double.parseDouble(xml.getAttributeValue(null, "lat")),
										Double.parseDouble(xml.getAttributeValue(null, "lon")),
										index++, país, xml.getAttributeValue(null, "ref"),
										pot == null ? 0 : Float.parseFloat(pot));
								l.add(antena);
								break;
							case "canal":
								if(antena == null)
									throw new RuntimeException(xml.getPositionDescription() +": canal sin antena?");
								antena.agregar(new Canal(xml.getAttributeValue(null, "nombre"),
										xml.getAttributeValue(null, "numero"),
										xml.getAttributeValue(null, "numero_virtual"),
										xml.getAttributeValue(null, "cadena"),
										xml.getAttributeValue(null, "polarizacion"),
										xml.getAttributeValue(null, "ref")));
								break;
						}
						break;
					case XmlPullParser.END_TAG:
						if(xml.getName().equals("antena"))
							antena = null;
						break;
				}
				xml.next();
			}
			in.close();
		} catch (XmlPullParserException | IOException e)
		{
			throw new RuntimeException(e);
		}
		Log.i("antenas", l.size() + " antenas de " + país + " cargadas en " + (System.nanoTime() - antes) / 1000000 + "ms");
	}

	private void agregar(Canal canal)
	{
		if(canales == null)
			canales = new ArrayList<>();
		canales.add(canal);
	}

	public boolean hayImágenes()
	{
		for(Canal canal : canales)
			if(canal.dameLogo() != 0)
				return true;
		return false;
	}

	private GlobalCoordinates coordsCache = null;
	private double rumboCacheado, distCacheada;

	public double distanceTo(GlobalCoordinates coords)
	{
		calcular(coords);
		return distCacheada;
	}

	public double rumboDesde(@NonNull GlobalCoordinates coords)
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

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(!(o instanceof Antena))
			return false;

		Antena antena = (Antena)o;
		return index == antena.index && país == antena.país;
	}

	@Override
	public int hashCode()
	{
		return 929 * index + país.ordinal();
	}

	@Override
	public String toString()
	{
		return país + "-" + index;
	}
}
