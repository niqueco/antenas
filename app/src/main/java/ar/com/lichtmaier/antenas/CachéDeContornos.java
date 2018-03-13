package ar.com.lichtmaier.antenas;

import android.app.ActivityManager;
import android.arch.lifecycle.LiveData;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.app.ActivityManagerCompat;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.util.Pair;

import com.crashlytics.android.Crashlytics;
import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import com.google.android.gms.maps.model.LatLng;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ar.com.lichtmaier.util.AsyncLiveData;
import de.lighti.clipper.*;

public class CachéDeContornos
{
	private static final int VERSION_BASE = 1;

	private static CachéDeContornos instancia;

	private SQLiteDatabase db;
	private int referencias = 1;

	private static LruCache<Integer, Polígono> lruCache;
	private final XmlPullParserFactory xmlPullParserFactory;
	private int[] cachéNegativo;
	private int tamañoCachéNegativo = 0;
	private boolean ensureDatabaseCalled = false;
	private final File externalCacheDir;

	@NonNull
	public static synchronized CachéDeContornos dameInstancia(Context ctx)
	{
		if(lruCache == null)
		{
			ActivityManager am = (ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);
			lruCache = new LruCache<>(am == null ? 30 : (ActivityManagerCompat.isLowRamDevice(am) || am.getMemoryClass() <= 96 ? 30 : 150));
		}
		if(instancia == null)
			instancia = new CachéDeContornos(ctx);
		else
			instancia.referencias++;
		return instancia;
	}

	public void devolver()
	{
		synchronized(CachéDeContornos.class)
		{
			if(--referencias == 0)
			{
				if(db != null)
					db.close();
				instancia = null;
			}
		}
	}

	private CachéDeContornos(Context ctx)
	{
		externalCacheDir = ctx.getExternalCacheDir();

		try {
			xmlPullParserFactory = XmlPullParserFactory.newInstance();
		} catch(XmlPullParserException e) {
			throw new RuntimeException(e);
		}
	}

	@WorkerThread
	private synchronized void ensureDatabase()
	{
		if(ensureDatabaseCalled)
			return;
		ensureDatabaseCalled = true;
		SQLiteDatabase base = null;
		try
		{
			if(externalCacheDir != null && externalCacheDir.isDirectory())
			{
				int flags = SQLiteDatabase.CREATE_IF_NECESSARY;
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
					flags |= SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
				base = SQLiteDatabase.openDatabase(externalCacheDir + "/contornos.db", null, flags);

				if(base.getVersion() != VERSION_BASE)
				{
					base.execSQL("create table contorno ("
							+ "app_id integer primary key on conflict replace, "
							+ "poligono blob not null, "
							+ "ult_uso integer not null, "
							+ "fecha_obtenido integer not null"
							+ ")");
					base.setVersion(VERSION_BASE);
				}
			} else
			{
				Log.w("antenas", "No hay almacenamiento externo. El caché de contornos será sólo en memoria.");
			}
		} catch(RuntimeException e)
		{
			Log.e("antenas", "Error creando la base. El caché de contornos será sólo en memoria.", e);
		}
		db = base;
	}

	public Polígono dameContornoFCC(String ref)
	{
		if(ref.indexOf(',') == -1)
			return dameContornoFCC(Integer.parseInt(ref));
		DefaultClipper clipper = new DefaultClipper();

		for(String str : ref.split(","))
		{
			Polígono p = dameContornoFCC(Integer.parseInt(str));
			if(p == null)
				continue;

			Path path = new Path();
			for(LatLng punto : p.getPuntos())
				path.add(new Point.LongPoint((long)(punto.longitude * 1000000), (long)(punto.latitude * 1000000)));
			clipper.addPath(path, Clipper.PolyType.CLIP, true);
		}
		Paths union = new Paths();
		if(!clipper.execute(Clipper.ClipType.UNION, union))
		{
			Log.e("antenas", "Falló la unión de los polígonos " + ref);
			return null;
		}
		Polígono.Builder builder = new Polígono.Builder();
		Iterator<Path> it = union.iterator();
		if(!it.hasNext())
		{
			Log.e("antenas", "¿La unión de " + ref + " es vacía?");
			return null;
		}
		for(Point.LongPoint punto : it.next())
			builder.add(new LatLng((double)punto.getY() / 1000000.0, (double)punto.getX() / 1000000.0));
		return builder.build();
	}

	@Nullable
	@WorkerThread
	private Polígono dameContornoFCC(int appId)
	{
		synchronized(CachéDeContornos.class)
		{
			if(referencias == 0)
			{
				Crashlytics.logException(new IllegalStateException("referencias==0"));
				return null;
			}
			referencias++;
		}
		try
		{
			ensureDatabase();
			Polígono contorno = lruCache.get(appId);
			if(contorno == null && db != null)
				contorno = dameContornoFCCDeLaBase(appId);
			if(contorno == null)
				contorno = dameContornoFCCDeLaRed(appId);
			return contorno;
		} finally
		{
			devolver();
		}
	}

	@Nullable
	private Polígono dameContornoFCCDeLaBase(int appId)
	{
		Polígono contorno = null;
		String selection = "app_id=?";
		String[] selectionArgs = {Integer.toString(appId)};
		Cursor cursor = db.query("contorno", new String[]{"poligono"}, selection, selectionArgs, null, null, null);
		try
		{
			if(cursor.moveToFirst())
				contorno = Polígono.deBytes(cursor.getBlob(0));
		} catch(SQLException e)
		{
			Crashlytics.logException(e);
		} finally
		{
			cursor.close();
		}
		if(contorno != null)
		{
			try
			{
				ContentValues values = new ContentValues(1);
				values.put("ult_uso", (int)(System.currentTimeMillis() / 1000L));
				db.update("contorno", values, selection, selectionArgs);
			} catch (SQLException e)
			{
				Crashlytics.logException(e);
			}
		}
		return contorno;
	}

	@Nullable
	private Polígono dameContornoFCCDeLaRed(int appId)
	{
		Polígono contorno = null;
		for(int i = 0; i < tamañoCachéNegativo; i++)
			if(cachéNegativo[i] == appId)
			{
				Log.i("antenas", "Al polígono con appId=" + appId + " ya lo buscamos sin éxito.");
				return null;
			}
		Log.i("antenas", "buscando el polígono con appId=" + appId);
		String url = "http://transition.fcc.gov/fcc-bin/contourplot.kml?appid=" + appId + "&.txt";
		InputStream in = null;
		try
		{
			TrafficStats.setThreadStatsTag(0xfcc);
			in = new URL(url).openStream();
			XmlPullParser parser = xmlPullParserFactory.newPullParser();
			parser.setInput(in, "UTF-8");
			int t;
			boolean inLineString = false;
			while((t = parser.next()) != XmlPullParser.END_DOCUMENT)
			{
				if(t == XmlPullParser.START_TAG && parser.getName().equals("LineString"))
				{
					inLineString = true;
					continue;
				}
				if(t == XmlPullParser.END_TAG && parser.getName().equals("LineString"))
				{
					inLineString = false;
					continue;
				}
				if(inLineString && t == XmlPullParser.START_TAG && parser.getName().equals("coordinates"))
				{
					t = parser.next();
					if(t != XmlPullParser.TEXT)
					{
						Log.e("antenas", "No se encontró el texto esperado en la línea " + parser.getLineNumber() + " de contorno appid=" + appId);
						return null;
					}

					Pattern pat = Pattern.compile("\\s+(-?[0-9.]+),(-?[0-9.]+),.*$", Pattern.MULTILINE);

					Polígono.Builder pb = new Polígono.Builder();

					Matcher m = pat.matcher(parser.getText());
					while(m.find())
					{
						float lat = Float.parseFloat(m.group(2));
						float lon = Float.parseFloat(m.group(1));
						pb.add(new LatLng(lat, lon));
					}
					contorno = pb.build();
					lruCache.put(appId, contorno);
					if(db != null)
					{
						ContentValues values = new ContentValues(3);
						values.put("app_id", appId);
						values.put("poligono", contorno.aBytes());
						int ahora = (int)(System.currentTimeMillis() / 1000L);
						values.put("ult_uso", ahora);
						values.put("fecha_obtenido", ahora);
						db.insert("contorno", null, values);
					}
					break;
				}
			}
			return contorno;
		} catch(XmlPullParserException e)
		{
			Log.e("antenas", "Obteniendo el contorno de " + url, e);
			if(cachéNegativo == null)
			{
				cachéNegativo = new int[16];
			} else if(tamañoCachéNegativo == cachéNegativo.length)
			{
				cachéNegativo = Arrays.copyOf(cachéNegativo, cachéNegativo.length + 16);
			}

			cachéNegativo[tamañoCachéNegativo++] = appId;
		} catch(IOException e)
		{
			Log.e("antenas", "Obteniendo el contorno de " + url, e);
		} catch(Exception e)
		{
			throw new RuntimeException("Obteniendo el contorno de " + url, e);
		} finally
		{
			if(in != null)
				try
				{
					in.close();
				} catch(IOException ignored)
				{
				}
			TrafficStats.clearThreadStatsTag();
		}
		return null;
	}

	public static void vaciarCache()
	{
		if(Log.isLoggable("antenas", Log.DEBUG))
		{
			int n = lruCache == null ? 0 : lruCache.size();
			if(n != 0)
				Log.d("antenas", "Vaciando caché de contornos de " + n + " elementos.");
		}
		if(lruCache != null)
			lruCache.evictAll();
		if(cachéEnContorno != null)
			cachéEnContorno.evictAll();
	}

	public LiveData<Boolean> enContorno(Antena antena, LatLng coords)
	{
		return AsyncLiveData.create(() -> enContorno(antena, coords, true));
	}

	private static LruCache<Pair<String, Antena>, Boolean> cachéEnContorno;

	/** Busca si un punto dado está cubierto por el contorno de algún canal de la antena.
	 */
	public boolean enContorno(final Antena antena, final LatLng coords, boolean puedoEsperar)
	{
		if(antena.país != País.US || antena.canales == null)
			return true;

		if(cachéEnContorno == null)
		{
			synchronized(this)
			{
				if(cachéEnContorno == null)
					cachéEnContorno = new LruCache<>(400);
			}
		}

		String hashCoords = GeoHash.encodeHash(new LatLong(coords.latitude, coords.longitude), 8);

		Pair<String, Antena> claveCaché = Pair.create(hashCoords, antena);
		Boolean cached = cachéEnContorno.get(claveCaché);
		if(cached != null)
			return cached;

		if(!puedoEsperar)
		{
			synchronized(CachéDeContornos.class) {
				referencias++;
			}
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... voids)
				{
					try {
						enContorno(antena, coords, true);
					} finally {
						devolver();
					}
					return null;
				}
			}.execute();
			return true;
		}

		if(Log.isLoggable("antenas", Log.DEBUG))
			Log.d("antenas", "buscando contorno para " + antena);

		List<Canal> canalesLejos = null;

		for(Canal c : antena.canales)
		{
			if(c.ref == null)
				continue;

			Polígono polígono = dameContornoFCC(c.ref);

			if(polígono == null)
				continue;

			if(!polígono.contiene(coords))
			{
				if(canalesLejos == null)
					canalesLejos = new ArrayList<>();
				canalesLejos.add(c);
			}
		}

		final boolean cerca = canalesLejos == null || antena.canales.size() != canalesLejos.size();
		if(!cerca && Log.isLoggable("antenas", Log.DEBUG))
			Log.d("antenas", "La antena " + antena + " tiene canales lejos: " + canalesLejos);

		cachéEnContorno.put(claveCaché, cerca);
		return cerca;
	}
}
