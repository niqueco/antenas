package ar.com.lichtmaier.antenas;

import java.lang.reflect.Field;
import java.util.*;

import org.gavaghan.geodesy.GlobalCoordinates;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationRequest;

public class AntenaActivity extends AppCompatActivity implements LocationClientCompat.Callback
{
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	public static final String PACKAGE = "ar.com.lichtmaier.antenas";
	private static final int PEDIDO_DE_PERMISO_FINE_LOCATION = 131;
	public static final int PRECISIÓN_ACEPTABLE = 150;

	static GlobalCoordinates coordsUsuario;
	static float alturaUsuario;
	protected Brújula brújula;
	private AntenasAdapter antenasAdapter;
	private Publicidad publicidad;
	boolean huboSavedInstanceState;
	private boolean seMuestraRuegoDePermisos;

	private LocationManager locationManager;

	private MenuItem opciónAyudaArgentina, opciónAyudaReinoUnido;
	private boolean mostrarOpciónAyudaArgentina = false, mostrarOpciónAyudaReinoUnido = false;

	private long comienzoUsoPantalla;

	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) { }
		@Override
		public void onProviderEnabled(String provider) { }
		@Override
		public void onProviderDisabled(String provider) { }

		@Override
		public void onLocationChanged(Location location)
		{
			if(location.getAccuracy() > 300)
				return;
			nuevaUbicación(location);
		}
	};

	static FlechaView flechaADesaparecer;
	private boolean abriendoAntena = false;
	private final AntenasAdapter.Callback onAntenaClickedListener = new AntenasAdapter.Callback()
	{
		@Override
		public void onAntenaClicked(Antena antena, View v)
		{
			if(abriendoAntena)
				return;
			abriendoAntena = true;
			suspender();

			Intent i = new Intent(AntenaActivity.this, UnaAntenaActivity.class);

			int[] screenLocation = new int[2];
			FlechaView flecha = (FlechaView)v.findViewById(R.id.flecha);
			flecha.getLocationOnScreen(screenLocation);
			int orientation = getResources().getConfiguration().orientation;
			i.putExtra(PACKAGE + ".antenaIndex", antena.index).
					putExtra(PACKAGE + ".antenaPaís", antena.país.name()).
					putExtra(PACKAGE + ".orientation", orientation).
					putExtra(PACKAGE + ".left", screenLocation[0]).
					putExtra(PACKAGE + ".top", screenLocation[1]).
					putExtra(PACKAGE + ".width", flecha.getWidth()).
					putExtra(PACKAGE + ".height", flecha.getHeight()).
					putExtra(PACKAGE + ".ángulo", flecha.getÁngulo()).
					putExtra(PACKAGE + ".ánguloDibujado", flecha.getÁnguloDibujado()).
					putExtra(PACKAGE + ".sinValor", brújula != null && brújula.sinValor());
			flechaADesaparecer = flecha;

			startActivity(i);
			overridePendingTransition(0, 0);
		}

		@Override
		public void onAdapterReady()
		{
			terminarDeConfigurar();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		asignarLayout();

		Toolbar tb = (Toolbar)findViewById(R.id.toolbar);
		setSupportActionBar(tb);
		final ActionBar actionBar = getSupportActionBar();
		assert actionBar != null;
		actionBar.setDisplayShowTitleEnabled(false);

		try
		{
			Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
			if(menuKeyField != null)
			{
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(ViewConfiguration.get(this), false);
			}
		} catch(Exception ignored) { }

		ProgressBar pb = (ProgressBar)findViewById(R.id.progressBar);
		if(pb != null)
		{
			prenderAnimación = new PrenderAnimación(pb);
			pb.postDelayed(prenderAnimación, 400);
		}

		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener()
		{
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
			{
				if(key.equals("usar_contornos"))
					antenasAdapter.reset();
			}
		});
		if(!prefs.getBoolean("unidad_configurada", false))
		{
			Locale locale = Locale.getDefault();
			SharedPreferences.Editor editor = prefs.edit()
					.putString("unit", ("US".equals(locale.getCountry()) && (!"es".equals(locale.getLanguage()))) ? "mi" : "km")
					.putBoolean("unidad_configurada", true);
			Compat.applyPreferences(editor);
		}

		brújula = Brújula.crear(this);

		if(brújula == null && !(this instanceof UnaAntenaActivity) && !Build.FINGERPRINT.equals(prefs.getString("aviso_no_brújula",null)))
		{
			Snackbar sb = Snackbar.make(findViewById(R.id.principal), R.string.aviso_no_hay_brújula, Snackbar.LENGTH_INDEFINITE)
					.setAction(android.R.string.ok, new View.OnClickListener()
					{
						@Override
						public void onClick(View v) { }
					})
					.setCallback(new Snackbar.Callback()
					{
						@Override
						public void onDismissed(Snackbar snackbar, int event)
						{
							if(event == DISMISS_EVENT_ACTION || event == DISMISS_EVENT_SWIPE)
								Compat.applyPreferences(prefs.edit().putString("aviso_no_brújula", Build.FINGERPRINT));
						}
					});
			TextView tv = (TextView)sb.getView().findViewById(android.support.design.R.id.snackbar_text);
			if(tv != null)
				tv.setMaxLines(4);
			sb.show();
		}
		if(brújula != null)
			Compat.applyPreferences(prefs.edit().remove("aviso_no_brújula"));

		final RecyclerView rv = (RecyclerView)findViewById(R.id.antenas);

		if(rv != null)
		{
			antenasAdapter = new AntenasAdapter(this, brújula, onAntenaClickedListener, R.layout.antena);
			rv.setAdapter(antenasAdapter);
			final RecyclerView.LayoutManager rvLayoutManager = rv.getLayoutManager();
			if(rvLayoutManager instanceof LinearLayoutManager)
				rv.addItemDecoration(new DivisoresItemDecoration(getResources().getDimension(R.dimen.alto_divisor)));
			else if(rvLayoutManager instanceof StaggeredGridLayoutManager)
			{
				rv.addItemDecoration(new RecyclerView.ItemDecoration()
				{
					@Override
					public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state)
					{
						super.getItemOffsets(outRect, view, parent, state);
						outRect.right = outRect.left = (int)(32 * getResources().getDisplayMetrics().density);
					}
				});
			}
		}


		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION))
			{
				ViewGroup principal = (ViewGroup)findViewById(R.id.principal);
				View view = getLayoutInflater().inflate(R.layout.permiso_necesario, principal, false);
				((TextView)view.findViewById(android.R.id.text1)).setText(R.string.explicacion_permiso_gps);
				view.findViewById(R.id.button).setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						ActivityCompat.requestPermissions(AntenaActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_FINE_LOCATION);
					}
				});
				principal.addView(view);
				seMuestraRuegoDePermisos = true;
			} else
			{
				ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_FINE_LOCATION);
			}
		} else
		{
			crearLocationClientCompat();
		}

		if(savedInstanceState != null && savedInstanceState.containsKey("lat"))
			nuevaUbicación(savedInstanceState.getDouble("lat"), savedInstanceState.getDouble("lon"), savedInstanceState.getDouble("alt"));

		huboSavedInstanceState = savedInstanceState != null;

		publicidad = new Publicidad(this, this instanceof UnaAntenaActivity
				? "ca-app-pub-0461170458442008/1711829752"
				: "ca-app-pub-0461170458442008/6164714153");

		if(rv != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			final float density = getResources().getDisplayMetrics().density;
			final RecyclerView.LayoutManager lm = rv.getLayoutManager();
			rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
				@Override
				public void onScrolled(RecyclerView recyclerView, int dx, int dy)
				{
					View v = lm.findViewByPosition(0);
					int y;
					if(v != null)
					{
						ViewGroup.LayoutParams lp = v.getLayoutParams();
						int margen = lp instanceof ViewGroup.MarginLayoutParams
								? ((ViewGroup.MarginLayoutParams)lp).topMargin
								: 0;
						y = margen - v.getTop();
					} else
						y = 1000;
					actionBar.setElevation(Math.min(y / 8f, density * 8f));
				}
			});
		}

		if(!(this instanceof UnaAntenaActivity) && savedInstanceState == null)
			Calificame.registrarLanzamiento(this);
	}

	@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
	private void crearLocationClientCompat()
	{
		if(seMuestraRuegoDePermisos)
		{
			ViewGroup principal = (ViewGroup)findViewById(R.id.principal);
			View pedido = principal.findViewById(R.id.pedido_de_permisos);
			principal.removeView(pedido);
		}
		locationClient = new LocationClientCompat(this, LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(10000)
				.setFastestInterval(2000)
				.setSmallestDisplacement(10), this);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_FINE_LOCATION)
		{
			if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
				//noinspection ResourceType
				crearLocationClientCompat();
			else
				finish();
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	protected void asignarLayout()
	{
		setContentView(R.layout.activity_antena);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if(coordsUsuario != null)
		{
			outState.putDouble("lat", coordsUsuario.getLatitude());
			outState.putDouble("lon", coordsUsuario.getLongitude());
			outState.putDouble("alt", alturaUsuario);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.antena, menu);
		opciónAyudaArgentina = menu.findItem(R.id.action_ayuda_ar);
		opciónAyudaReinoUnido = menu.findItem(R.id.action_ayuda_uk);
		configurarMenú();
		return true;
	}

	private void configurarMenú()
	{
		if(opciónAyudaArgentina != null)
			opciónAyudaArgentina.setVisible(mostrarOpciónAyudaArgentina);
		if(opciónAyudaReinoUnido != null)
			opciónAyudaReinoUnido.setVisible(mostrarOpciónAyudaReinoUnido);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		Intent i;
		switch(id)
		{
			case R.id.action_settings:
				i = new Intent(this, PreferenciasActivity.class);
				startActivity(i);
				return true;
			case R.id.action_mapa:
				i = new Intent(this, MapaActivity.class);
				startActivity(i);
				return true;
			case R.id.action_ayuda_ar:
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://poné-tda.com.ar/"));
				if(i.resolveActivity(getPackageManager()) != null)
					startActivity(i);
				return true;
			case R.id.action_ayuda_uk:
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.digitaluk.co.uk/coveragechecker/"));
				if(i.resolveActivity(getPackageManager()) != null)
					startActivity(i);
				return true;
			case R.id.action_niqueco:
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://twitter.com/niqueco"));
				PackageManager pm = getPackageManager();
				List<ResolveInfo> l = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
				for(ResolveInfo info : l)
					if(info.activityInfo.packageName.equals("com.twitter.android"))
					{
						i.setPackage(info.activityInfo.packageName);
						break;
					}
				startActivity(i);
				if(i.resolveActivity(getPackageManager()) != null)
					startActivity(i);
				else
					Toast.makeText(this, R.string.app_no_disponible, Toast.LENGTH_LONG).show();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		((Aplicacion)getApplication()).reportActivityStart(this);
		if(locationClient != null)
			locationClient.onStart();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		abriendoAntena = false;
		if(brújula != null)
			brújula.onResume(this);
		if(locationManager != null)
		{
			Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_COARSE);
			criteria.setCostAllowed(true);
			try
			{
				//noinspection MissingPermission
				Compat.requestLocationUpdates(locationManager, 1000 * 60, 0, criteria, locationListener);
			} catch(IllegalArgumentException e)
			{
				Log.e("antenas", "Error pidiendo updates de GPS", e);
				Toast.makeText(this, getString(R.string.no_ubicacion), Toast.LENGTH_SHORT).show();
				finish();
			}
		}
		if(locationClient != null)
			locationClient.onResume();
		publicidad.onResume();
		comienzoUsoPantalla = System.currentTimeMillis();
	}

	@Override
	protected void onPause()
	{
		if(System.currentTimeMillis() - comienzoUsoPantalla > 1000 * 30)
			Calificame.registráQueMiróLasAntenas(this);
		suspender();
		super.onPause();
	}

	private void suspender()
	{
		publicidad.onPause();
		if(brújula != null)
			brújula.onPause(this);
		if(locationManager != null)
			//noinspection MissingPermission
			locationManager.removeUpdates(locationListener);
		if(locationClient != null)
			locationClient.onPause();
	}

	@Override
	protected void onStop()
	{
		if(locationClient != null)
			locationClient.onStop();
		((Aplicacion)getApplication()).reportActivityStop(this);
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		if(antenasAdapter != null)
			antenasAdapter.onDestroy();
		publicidad.onDestroy();
		super.onDestroy();
	}

	private LocationClientCompat locationClient;
	private SharedPreferences prefs;

	private boolean menúConfigurado = false;

	private void nuevaUbicación(Location location)
	{
		if(location.hasAccuracy())
		{
			float accuracy = location.getAccuracy();
			if(accuracy > PRECISIÓN_ACEPTABLE)
			{
				Log.i("antenas", "Rechazando ubicación de poca precisión (" + accuracy + "m)");
				return;
			}
		}

		nuevaUbicación(location.getLatitude(), location.getLongitude(), location.getAltitude());
	}

	private void nuevaUbicación(double lat, double lon, double altura)
	{
		coordsUsuario = new GlobalCoordinates(lat, lon);
		alturaUsuario = (float)altura;
		nuevaUbicación();
	}

	protected void nuevaUbicación()
	{
		if(coordsUsuario == null)
			return;
		if(brújula != null)
			brújula.setCoordinates(coordsUsuario.getLatitude(), coordsUsuario.getLongitude(), alturaUsuario);
		antenasAdapter.nuevaUbicación(coordsUsuario);
	}

	/** Se llama cuando antenasAdapter avisa que ya está toda la información. */
	private void terminarDeConfigurar()
	{
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		if(!menúConfigurado)
		{
			Set<País> países = EnumSet.noneOf(País.class);
			for(Antena antena : antenasAdapter.antenasCerca)
				países.add(antena.país);
			if(países.contains(País.AR) || países.contains(País.UY))
				mostrarOpciónAyudaArgentina = true;
			if(países.contains(País.UK))
				mostrarOpciónAyudaReinoUnido = true;
			configurarMenú();
			if(!prefs.getBoolean("paises_configurados", false))
			{
				SharedPreferences.Editor editor = prefs.edit();
				for(País país : País.values())
					editor.putBoolean("mapa_país_" + país, países.contains(país));
				editor.putBoolean("paises_configurados", true);
				Compat.applyPreferences(editor);
			}
			menúConfigurado = true;
		}

		// Si estamos en EE.UU. la distancia máxima default se establece en 100 km.
		if(maxDist == 60000 && !prefs.getBoolean("distancia_configurada", false))
		{
			Set<País> países = EnumSet.noneOf(País.class);
			for(Antena antena : antenasAdapter.antenasCerca)
				países.add(antena.país);
			SharedPreferences.Editor editor = prefs.edit();
			boolean volver = false;
			if(países.contains(País.US))
			{
				editor.putString("max_dist", "100");
				volver = true;
			}
			Compat.applyPreferences(editor.putBoolean("distancia_configurada", true));
			if(volver)
			{
				nuevaUbicación();
			}
		}

		final ProgressBar pb = (ProgressBar)findViewById(R.id.progressBar);
		if(pb != null)
		{
			if(prenderAnimación.comienzoAnimación != -1)
			{
				long falta = 600 - (System.currentTimeMillis() - prenderAnimación.comienzoAnimación);
				if(falta <= 0)
					pb.setVisibility(View.GONE);
				else
					pb.postDelayed(new Runnable()
					{
						@Override
						public void run()
						{
							pb.setVisibility(View.GONE);
						}
					}, falta);
			} else
			{
				prenderAnimación.cancelado = true;
				pb.removeCallbacks(prenderAnimación);
			}
			prenderAnimación = null;
		}
		TextView problema = (TextView)findViewById(R.id.problema);
		if(antenasAdapter.getItemCount() == 0)
		{
			StringBuilder sb = new StringBuilder(getString(R.string.no_se_encontraron_antenas, Formatos.formatDistance(this, maxDist)));
			String[] vv = getResources().getStringArray(R.array.pref_max_dist_values);
			if(Integer.parseInt(vv[vv.length-1]) * 1000 != maxDist)
				sb.append(' ').append(getString(R.string.podes_incrementar_radio));
			problema.setText(sb.toString());
			problema.setVisibility(View.VISIBLE);
		} else
		{
			problema.setVisibility(View.GONE);
		}
	}

	public void onConnectionFailed(ConnectionResult r)
	{
		if(r.hasResolution())
		{
			Log.w("antenas", "Play Services: " + r);
			try
			{
				r.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch(SendIntentException e)
			{
				e.printStackTrace();
			}
		} else
		{
			Log.e("antenas", "Play Services no disponible: " + r + ". No importa, sobreviviremos.");

			pedirCambioConfiguración();

			locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
			locationClient = null;
			if(coordsUsuario == null)
			{
				//noinspection MissingPermission
				Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				if(location != null && location.getAccuracy() < PRECISIÓN_ACEPTABLE)
					nuevaUbicación(location);
				else
				{
					//noinspection MissingPermission
					locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
					if(location != null && location.getAccuracy() < PRECISIÓN_ACEPTABLE)
						nuevaUbicación(location);
				}
			}
		}
	}

	void pedirCambioConfiguración()
	{
		if(!huboSavedInstanceState && !((LocationManager)getSystemService(Context.LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER))
		{
			View v = findViewById(R.id.scroll);
			if(v == null)
				v = findViewById(R.id.root);
			Snackbar.make(v, R.string.gps_is_off, Snackbar.LENGTH_INDEFINITE)
					.setAction(R.string.gps_prender, new View.OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
						}
					})
					.show();
		}
	}

	@SuppressWarnings("MissingPermission")
	public void onConnected(Bundle arg0)
	{
		Location location = locationClient.getLastLocation();
		if(location != null)
			nuevaUbicación(location);
		locationClient.onConnected();
		publicidad.load(location);
	}

	@Override
	public void onLocationChanged(Location location)
	{
		nuevaUbicación(location);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch(requestCode)
		{
			case CONNECTION_FAILURE_RESOLUTION_REQUEST:
				if(resultCode == RESULT_OK)
				{
					locationClient.connect();
				}
				return;
		}
		if(locationClient.onActivityResult(requestCode, resultCode, data))
			return;
		super.onActivityResult(requestCode, resultCode, data);
	}

	private PrenderAnimación prenderAnimación;

	private static class PrenderAnimación implements Runnable
	{
		private final View pb;
		public long comienzoAnimación = -1;
		boolean cancelado = false;

		public PrenderAnimación(View pb)
		{
			this.pb = pb;
		}

		@Override
		public void run()
		{
			if(cancelado)
				return;
			pb.setVisibility(View.VISIBLE);
			comienzoAnimación = System.currentTimeMillis();
		}
	}

	private static class DivisoresItemDecoration extends RecyclerView.ItemDecoration
	{
		private final float alto;
		private final Paint pintura;

		public DivisoresItemDecoration(float alto)
		{
			this.alto = alto;
			pintura = new Paint();
			pintura.setColor(0xff29b6f6);
			pintura.setStrokeWidth(alto);
		}

		@Override
		public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state)
		{
			int left = parent.getPaddingLeft();
			int right = parent.getWidth() - parent.getPaddingRight();
			int n = parent.getChildCount() - 1;
			for(int i = 0 ; i < n ; i++)
			{
				View v = parent.getChildAt(i);
				RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) v.getLayoutParams();
				int y = v.getBottom() + params.bottomMargin;
				c.drawLine(left, y, right, y, pintura);
			}
		}

		@Override
		public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state)
		{
			super.getItemOffsets(outRect, view, parent, state);
			outRect.bottom = (int)alto;
		}
	}
}
