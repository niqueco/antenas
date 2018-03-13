package ar.com.lichtmaier.antenas;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
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
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

import ar.com.lichtmaier.util.AsyncLiveData;
import ar.com.lichtmaier.util.StringUtils;

public class Antena implements Parcelable
{
	final public String descripción;
	final private String ref;
	private final GlobalCoordinates c;
	final public float potencia;
	public final int index;
	final public País país;
	@Nullable public List<Canal> canales;

	private double dist;

	final static private Map<País, Future<List<Antena>>> antenasPorPaís = new EnumMap<>(País.class);
	final static private SortedMap<String, List<Antena>> geohashAAntenas = new TreeMap<>();

	@SuppressLint("StaticFieldLeak")
	static Context applicationContext;

	private static final ExecutorService executor = Executors.newSingleThreadExecutor();

	private Antena(String descripción, double lat, double lon, int index, País país, String ref, float potencia)
	{
		this.descripción = descripción;
		this.index = index;
		this.potencia = potencia;
		c = new GlobalCoordinates(lat, lon);
		this.país = país;
		this.ref = ref;
		String geohash = GeoHash.encodeHash(lat, lon, 4);
		synchronized(geohashAAntenas)
		{
			List<Antena> l = geohashAAntenas.get(geohash);
			if(l == null)
			{
				l = new ArrayList<>();
				geohashAAntenas.put(geohash, l);
			}
			l.add(this);
		}
	}

	private transient String nombre = null;
	private transient Locale localeNombre = null;

	public String dameNombre(Context context)
	{
		Locale locale = Locale.getDefault();
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

	private transient List<CharSequence> listaDetallesCanales = null;
	private transient Locale localeListaDetallesCanales = null;

	@Nullable
	private CharSequence dameDetalleCanales(@NonNull Context context)
	{
		if(canales == null || canales.isEmpty())
			return null;
		return canales.size() > 12 ? dameResumenCanales(context) : StringUtils.join(dameListaDetallesCanales(context), ", ");
	}

	@NonNull
	private CharSequence dameResumenCanales(@NonNull Context context)
	{
		assert canales != null;
		SpannableString spannableString = new SpannableString(context.getResources().getQuantityString(R.plurals.senales, canales.size(), canales.size()));
		spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), 0);
		return spannableString;
	}

	@NonNull
	private List<CharSequence> dameListaDetallesCanales(@NonNull Context context)
	{
		if(canales == null)
			return Collections.emptyList();
		Locale locale = Locale.getDefault();
		if(listaDetallesCanales == null)
			listaDetallesCanales = new ArrayList<>(canales.size());
		else
		{
			if(locale.equals(localeListaDetallesCanales))
				return listaDetallesCanales;
			else
				listaDetallesCanales.clear();
		}

		for(Canal canal : canales)
		{
			SpannableStringBuilder sb = new SpannableStringBuilder();
			if(canal.nombre != null)
			{
				String n = canal.nombre;
				if(país == País.US && canal.cadena != null)
					n = n.replaceAll("-.*$", "");
				sb.append(n);
			}
			Bitmap bmp = canal.dameThumbnail(context);
			if(bmp != null)
			{
				sb.append(' ');
				int desde = sb.length();
				sb.append(canal.cadena);
				ImageSpan what = new ImageSpan(context, bmp, ImageSpan.ALIGN_BASELINE);
				sb.setSpan(what, desde, sb.length(), 0);
			}
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
			listaDetallesCanales.add(sb);
		}

		localeListaDetallesCanales = locale;

		return listaDetallesCanales;
	}

	void ponéDetalles(CommaEllipsizeTextView v)
	{
		Context context = v.getContext();
		assert canales != null;
		if(canales.size() > 12)
			v.setText(dameResumenCanales(context));
		else
			v.setItems(dameListaDetallesCanales(context));
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

	public static LiveData<List<Antena>> dameAntenasCercaLD(Context ctx, GlobalCoordinates coordsUsuario, int maxDist, boolean mostrarMenos)
	{
		return AsyncLiveData.create(() -> dameAntenasCerca(ctx, coordsUsuario, maxDist, mostrarMenos, Long.MAX_VALUE));
	}

	public static List<Antena> dameAntenasCerca(Context ctx, GlobalCoordinates coordsUsuario, int maxDist, boolean mostrarMenos, long timeout) throws TimeoutException
	{
		double latitud = coordsUsuario.getLatitude();
		double longitud = coordsUsuario.getLongitude();
		Set<País> países = longitud > -32
			? (longitud < 60 ? EnumSet.of(País.UK, País.PT, País.AT) : (latitud > 0 ? EnumSet.of(País.JA) : EnumSet.of(País.AU, País.NZ)))
			: ((latitud > 13)
				? (latitud < 40 ? EnumSet.of(País.US) : EnumSet.of(País.US, País.CA))
				: (latitud < -34 || (latitud < -18 && longitud < -58)
					? EnumSet.of(País.AR, País.UY)
					: EnumSet.of(País.AR, País.BR, País.CO, País.UY)));
		try
		{
			long t = System.nanoTime() + timeout;
			for(País país1 : países)
			{
				long paísTimeout = Math.max(t - System.nanoTime(), 0);
				dameAntenasFuturo(ctx, país1).get(paísTimeout, TimeUnit.NANOSECONDS);
			}
		} catch(InterruptedException|ExecutionException e)
		{
			throw new RuntimeException(e);
		}
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
		synchronized(geohashAAntenas)
		{
			for(String hash : coverage.getHashes())
				for(Map.Entry<String, List<Antena>> e : geohashAAntenas.subMap(hash, hashMásUno(hash)).entrySet())
					antenas.addAll(e.getValue());
		}
	}

	@NonNull
	private static String hashMásUno(String hash)
	{
		int len = hash.length();
		return hash.substring(0, len - 1) + (char)((hash.charAt(len - 1) + 1));
	}

	private static class CargarAntenasPaís implements Callable<List<Antena>>
	{
		private final País país;
		private final Context ctx;

		CargarAntenasPaís(País país, Context ctx)
		{
			this.país = país;
			if(ctx == null)
			{
				if(applicationContext == null)
					throw new NullPointerException("applicationContext");
				this.ctx = applicationContext;
			} else
			{
				this.ctx = ctx;
			}
		}

		@Override
		public List<Antena> call() throws Exception
		{
			ArrayList<Antena> l;
			long antes = System.nanoTime();
			XmlPullParser xml = XmlPullParserFactory.newInstance().newPullParser();
			int res;
			switch(país)
			{
				case AR:
					res = R.raw.antenas_ar;
					break;
				case AT:
					res = R.raw.antenas_at;
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
				case JA:
					res = R.raw.antenas_ja;
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
			Log.i("antenas", l.size() + " antenas de " + país + " cargadas en " + (System.nanoTime() - antes) / 1000000 + "ms");
			return l;
		}
	}

	private void agregar(Canal canal)
	{
		if(canales == null)
			canales = new ArrayList<>();
		canales.add(canal);
	}

	public boolean hayImágenes()
	{
		assert canales != null;
		for(Canal canal : canales)
			if(canal.dameLogo() != 0)
				return true;
		return false;
	}

	private GlobalCoordinates coordsCache = null;
	private double rumboCacheado, distCacheada;

	public double distanceTo(@NonNull GlobalCoordinates coords)
	{
		calcular(coords);
		return distCacheada;
	}

	public double rumboDesde(@NonNull GlobalCoordinates coords)
	{
		calcular(coords);
		return rumboCacheado;
	}

	private void calcular(@NonNull GlobalCoordinates coords)
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
		try
		{
			return dameAntenasFuturo(ctx, país).get();
		} catch(InterruptedException|ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}

	private synchronized static Future<List<Antena>> dameAntenasFuturo(final Context ctx, final País país)
	{
		Future<List<Antena>> f = antenasPorPaís.get(país);
		if(f != null)
			return f;
		f = executor.submit(new CargarAntenasPaís(país, ctx));
		antenasPorPaís.put(país, f);
		return f;
	}

	/** Devuelve una antena en base al número de orden.
	 *
	 * @param index el número de orden
	 * @return una antena
	 */
	public static Antena dameAntena(Context ctx, País país, int index)
	{
		try
		{
			return dameAntenasFuturo(ctx, país).get().get(index);
		} catch(InterruptedException|ExecutionException e)
		{
			throw new RuntimeException(e);
		}
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

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i)
	{
		parcel.writeInt(país.ordinal());
		parcel.writeInt(index);
	}

	@SuppressWarnings("unused")
	public static final Parcelable.Creator<Antena> CREATOR
			= new Parcelable.Creator<Antena>()
	{
		@Override
		public Antena createFromParcel(Parcel parcel)
		{
			País país = País.values()[parcel.readInt()];
			int index = parcel.readInt();
			return dameAntena(null, país, index);
		}

		@Override
		public Antena[] newArray(int tam)
		{
			return new Antena[tam];
		}
	};
}
