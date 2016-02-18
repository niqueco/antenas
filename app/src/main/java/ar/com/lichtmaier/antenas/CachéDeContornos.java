package ar.com.lichtmaier.antenas;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CachéDeContornos
{
	public static final int VERSION_BASE = 1;

	private static CachéDeContornos instancia;

	final private SQLiteDatabase db;

	final private LruCache<Integer, Polígono> lruCache = new LruCache<>(50);
	private final XmlPullParserFactory xmlPullParserFactory;

	@NonNull
	public static synchronized CachéDeContornos dameInstancia(Context ctx)
	{
		if(instancia == null)
			instancia = new CachéDeContornos(ctx);
		else
			instancia.db.acquireReference();
		return instancia;
	}

	public void devolver()
	{
		synchronized(CachéDeContornos.class)
		{
			db.releaseReference();
			if(!db.isOpen())
			{
				instancia = null;
				lruCache.evictAll();
			}
		}
	}

	private CachéDeContornos(Context ctx)
	{
		db = SQLiteDatabase.openDatabase(ctx.getExternalCacheDir() + "/contornos.db", null, SQLiteDatabase.CREATE_IF_NECESSARY);

		if(db.getVersion() != VERSION_BASE)
		{
			db.execSQL("create table contorno ("
					+ "app_id integer primary key on conflict replace, "
					+ "poligono blob not null, "
					+ "ult_uso integer not null, "
					+ "fecha_obtenido integer not null"
					+ ")");
			db.setVersion(VERSION_BASE);
		}

		try
		{
			xmlPullParserFactory = XmlPullParserFactory.newInstance();
		} catch(XmlPullParserException e)
		{
			throw new RuntimeException(e);
		}

	}

	@Nullable
	Polígono dameContornoFCC(int appId)
	{
		Polígono contorno = lruCache.get(appId);
		if(contorno == null)
		{
			String selection = "app_id=?";
			String[] selectionArgs = {Integer.toString(appId)};
			Cursor cursor = db.query("contorno", new String[] {"poligono"}, selection, selectionArgs, null, null, null);
			try
			{
				if(cursor.moveToFirst())
					contorno = Polígono.deBytes(cursor.getBlob(0));
			} finally
			{
				cursor.close();
			}
			if(contorno != null)
			{
				ContentValues values = new ContentValues(1);
				values.put("ult_uso", (int)(System.currentTimeMillis() / 1000L));
				db.update("contorno", values, selection, selectionArgs);
			}
		}
		if(contorno == null)
		{
			Log.i("antenas", "buscando el polígono con appId=" + appId);
			String url = "http://transition.fcc.gov/fcc-bin/contourplot.kml?appid=" + appId;
			InputStream in = null;
			try
			{
				in = new URL(url).openStream();
				XmlPullParser parser = xmlPullParserFactory.newPullParser();
				parser.setInput(in, "UTF-8");
				int t;
				boolean inLineString = false;
				while((t=parser.next()) != XmlPullParser.END_DOCUMENT)
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

						Pattern pat = Pattern.compile("^\\s*([^,]+),([^,]+),.*$", Pattern.MULTILINE);

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
						ContentValues values = new ContentValues(3);
						values.put("app_id", appId);
						values.put("poligono", contorno.aBytes());
						int ahora = (int)(System.currentTimeMillis() / 1000L);
						values.put("ult_uso", ahora);
						values.put("fecha_obtenido", ahora);
						db.insert("contorno", null, values);
						break;
					}
				}
			} catch(XmlPullParserException | IOException e)
			{
				Log.e("antenas", "Obteniendo el contorno de " + url, e);
			} finally
			{
				if(in != null)
					try { in.close(); } catch (IOException ignored) { }
			}
		}
		return contorno;
	}
}
