package ar.com.lichtmaier.antenas;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.gavaghan.geodesy.GlobalCoordinates;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.*;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;

public class AntenaActivity extends AppCompatActivity implements SensorEventListener, com.google.android.gms.location.LocationListener
{
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	public static final String PACKAGE = "ar.com.lichtmaier.antenas";
	private static final int PEDIDO_DE_PERMISO_FINE_LOCATION = 131;
	public static final int PRECISIÓN_ACEPTABLE = 150;

	final private Map<Antena, View> antenaAVista = new HashMap<>();
	final private Map<View, Antena> vistaAAntena = new HashMap<>();
	static GlobalCoordinates coordsUsuario;
	static float alturaUsuario;
	final private float[] gravity = new float[3];
	final private float[] geomagnetic = new float[3];
	private SensorManager sensorManager;
	private Sensor acelerómetro;
	private Sensor magnetómetro;
	private boolean hayInfoDeMagnetómetro = false, hayInfoDeAcelerómetro = false;
	protected boolean usarBrújula;
	private float declinaciónMagnética = Float.MAX_VALUE;
	private Publicidad publicidad;
	private int rotación;
	boolean huboSavedInstanceState;
	private boolean seMuestraRuegoDePermisos;
	private Thread threadContornos;
	final private BlockingQueue<Antena> colaParaContornos = new LinkedBlockingQueue<>();

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
	private final View.OnClickListener onAntenaClickedListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			Intent i = new Intent(AntenaActivity.this, UnaAntenaActivity.class);
			Antena antena = vistaAAntena.get(v);

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
					putExtra(PACKAGE + ".ángulo", flecha.getÁngulo());
			flechaADesaparecer = flecha;

			startActivity(i);
			overridePendingTransition(0, 0);
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

		ContentLoadingProgressBar pb = (ContentLoadingProgressBar)findViewById(R.id.progressBar);
		if(pb != null)
			pb.show();

		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener()
		{
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
			{
				if(key.equals("usar_contornos"))
					reset();
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

		if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS))
		{
			sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
			magnetómetro = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			acelerómetro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		}

		usarBrújula = magnetómetro != null && acelerómetro != null;

		if(!usarBrújula && !(this instanceof UnaAntenaActivity) && !Build.FINGERPRINT.equals(prefs.getString("aviso_no_brújula",null)))
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
		if(usarBrújula)
			Compat.applyPreferences(prefs.edit().remove("aviso_no_brújula"));

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

		rotación = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation();

		final View principal = findViewById(R.id.principal);
		ViewTreeObserver tvo = principal.getViewTreeObserver();
		if(tvo.isAlive())
		{
			tvo.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
			{
				private int pw = -1, n = -1;

				@Override
				public void onGlobalLayout()
				{
					int w = principal.getWidth();
					if(w == pw && antenaAVista.size() == n)
						return;
					pw = w;
					n = antenaAVista.size();
					for(Entry<Antena, View> e : antenaAVista.entrySet())
						actualizarDescripción(e.getValue(), e.getKey());
				}
			});
		}

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			final ScrollView sv = (ScrollView)findViewById(R.id.scroll);
			final float density = getResources().getDisplayMetrics().density;
			if(sv != null)
			{
				final ViewTreeObserver.OnScrollChangedListener scrollChangedListener;
				scrollChangedListener = new ViewTreeObserver.OnScrollChangedListener()
				{
					@Override
					public void onScrollChanged()
					{
						actionBar.setElevation(Math.min(sv.getScrollY() / 8f, density * 8f));
					}
				};
				final ViewTreeObserver o = sv.getViewTreeObserver();
				o.addOnScrollChangedListener(scrollChangedListener);
				o.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
				{
					@Override
					public boolean onPreDraw()
					{
						sv.getViewTreeObserver().removeOnPreDrawListener(this);
						scrollChangedListener.onScrollChanged();
						return true;
					}
				});
			}
		}

		if(!(this instanceof UnaAntenaActivity) && savedInstanceState == null)
			Calificame.registrarLanzamiento(this);
	}

	private synchronized void crearThreadContornos()
	{
		if(threadContornos != null)
			return;
		threadContornos = new Thread("antenas-contornos") {

			private CachéDeContornos cachéDeContornos;

			@Override
			public void run()
			{
				cachéDeContornos = CachéDeContornos.dameInstancia(AntenaActivity.this);
				try
				{
					//noinspection InfiniteLoopStatement
					while(true)
					{
						final Antena antena = colaParaContornos.poll(15, TimeUnit.SECONDS);

						if(antena == null)
							break;

						if(Log.isLoggable("antenas", Log.DEBUG))
							Log.d("antenas", "buscando contorno para " + antena);

						List<Canal> canalesLejos = null;

						for(Canal c : antena.canales)
						{
							if(c.ref == null)
								continue;

							Polígono polígono = cachéDeContornos.dameContornoFCC(Integer.parseInt(c.ref));

							if(polígono == null)
								continue;

							if(!polígono.contiene(new LatLng(coordsUsuario.getLatitude(), coordsUsuario.getLongitude())))
							{
								if(canalesLejos == null)
									canalesLejos = new ArrayList<>();
								canalesLejos.add(c);
							}
						}

						if(canalesLejos != null)
						{
							final List<Canal> finalCanalesLejos = canalesLejos;
							runOnUiThread(new Runnable()
							{
								@Override
								public void run()
								{
									marcarCanalesComoLejos(antena, finalCanalesLejos);
								}
							});
						}
					}
				} catch(InterruptedException ignored) { }
				finally
				{
					cachéDeContornos.devolver();
					synchronized(AntenaActivity.this)
					{
						threadContornos = null;
					}
				}
			}
		};
		threadContornos.start();
	}

	private void marcarCanalesComoLejos(Antena antena, List<Canal> canalesLejos)
	{
		if(Log.isLoggable("antenas", Log.DEBUG))
			Log.d("antenas", "La antena " + antena + " tiene canales lejos: " + canalesLejos);
		View v = antenaAVista.get(antena);
		if(v == null)
		{
			Log.w("antenas", "La antena a bajar " + antena + " no tiene una vista asociada!");
			return;
		}
		if(antena.canales.size() == canalesLejos.size())
			bajar(v);
	}

	final private List<View> vistasABajar = new ArrayList<>();

	private void bajar(View v)
	{
		synchronized(vistasABajar)
		{
			vistasABajar.add(v);
		}
		bajarHandler.removeMessages(0);
		if(colaParaContornos.isEmpty())
			bajarHandler.sendEmptyMessage(0);
		else
			bajarHandler.sendEmptyMessageDelayed(0, 1000);
	}

	final private BajarHandler bajarHandler = new BajarHandler(this);

	static class BajarHandler extends Handler
	{
		final private WeakReference<AntenaActivity> actRef;

		public BajarHandler(AntenaActivity antenaActivity)
		{
			actRef = new WeakReference<>(antenaActivity);
		}

		@Override
		public void handleMessage(Message msg)
		{
			final AntenaActivity act = actRef.get();

			final List<View> vistas;

			synchronized(act.vistasABajar)
			{
				vistas = new ArrayList<>(act.vistasABajar);
				act.vistasABajar.clear();
			}

			if(act.isFinishing() || Compat.activityIsDestroyed(act) || vistas.isEmpty())
				return;

			final ViewGroup p = (ViewGroup)vistas.get(0).getParent();

			final IdentityHashMap<View, Integer> offsets = new IdentityHashMap<>();

			for(int i = 0 ; i < p.getChildCount() ; i++)
			{
				View v = p.getChildAt(i);
				offsets.put(v, v.getTop());
			}

			for(View v : vistas)
			{
				p.removeView(v);
				p.addView(v);
				v.findViewById(R.id.aviso_lejos).setVisibility(View.VISIBLE);
			}

			if(p.getChildCount() > 15)
				return;

			final ViewTreeObserver vto = p.getViewTreeObserver();

			vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
			{
				@Override
				public boolean onPreDraw()
				{
					vto.removeOnPreDrawListener(this);
					for(int i = 0 ; i < p.getChildCount() ; i++)
					{
						View v = p.getChildAt(i);
						int dif = offsets.get(v) - v.getTop();
						if(dif != 0)
						{
							ViewCompat.setTranslationY(v, dif);
							ViewCompat.animate(v)
									.setInterpolator(new AccelerateDecelerateInterpolator())
									.setDuration(300)
									.withLayer()
									.translationY(0);
						}
						if(vistas.contains(v))
						{
							View aviso = v.findViewById(R.id.aviso_lejos);
							ViewCompat.setAlpha(aviso, 0);
							ViewCompat.animate(aviso)
									.setInterpolator(new AccelerateDecelerateInterpolator())
									.setStartDelay(200)
									.setDuration(400)
									.withLayer()
									.alpha(1);
						}
					}
					return true;
				}
			});
		}
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
				.setSmallestDisplacement(10));
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

	protected void actualizarDescripción(View v, Antena antena)
	{
		CharSequence detalleCanales = antena.dameDetalleCanales(this);
		TextView tvDesc = (TextView)v.findViewById(R.id.antena_desc);
		TextView tvDet = (TextView)v.findViewById(R.id.antena_detalle_canales);

		if(antena.descripción == null && detalleCanales == null)
		{
			Log.e("antenas", "Antena sin nombre ni canales: " + antena);
			return;
		}

		tvDesc.setText(TextUtils.commaEllipsize(antena.descripción != null ? antena.descripción : detalleCanales, tvDesc.getPaint(), tvDesc.getWidth() * 3, getString(R.string.one_more), getString(R.string.some_more)));
		if(tvDet.getVisibility() != View.GONE)
			tvDet.setText(TextUtils.commaEllipsize(detalleCanales, tvDet.getPaint(), tvDet.getWidth() * 3, getString(R.string.one_more), getString(R.string.some_more)));
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
		if(usarBrújula)
		{
			sensorManager.registerListener(this, magnetómetro, SensorManager.SENSOR_DELAY_UI);
			sensorManager.registerListener(this, acelerómetro, SensorManager.SENSOR_DELAY_UI);
		}
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
		publicidad.onPause();
		hayInfoDeAcelerómetro = false;
		hayInfoDeMagnetómetro = false;
		if(usarBrújula)
			sensorManager.unregisterListener(this);
		if(locationManager != null)
			//noinspection MissingPermission
			locationManager.removeUpdates(locationListener);
		if(locationClient != null)
			locationClient.onPause();
		super.onPause();
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
		synchronized(this)
		{
			if(threadContornos != null)
			{
				threadContornos.interrupt();
				threadContornos = null;
			}
		}
		publicidad.onDestroy();
		super.onDestroy();
	}

	private LocationClientCompat locationClient;
	private SharedPreferences prefs;
	private long lastUpdate = 0;
	void nuevaOrientación(double brújula)
	{
		long now = System.currentTimeMillis();
		if(now - lastUpdate < 20)
			return;
		lastUpdate = now;
		//NumberFormat nf = NumberFormat.getInstance(new Locale("es", "AR"));
		//((TextView)findViewById(R.id.orientacion)).setText(nf.format(brújula) /*+ " " + nf.format(Math.PI/2.0 - brújula)*/);
		//Log.d("antenas", "orientacion: " + values[0]);
		for(Entry<Antena, View> e : antenaAVista.entrySet())
		{
			Antena antena = e.getKey();
			double rumbo = antena.rumboDesde(coordsUsuario);
			FlechaView f = (FlechaView)e.getValue().findViewById(R.id.flecha);
			f.setÁngulo(rumbo - brújula);
		}
	}

	final private float[] r = new float[9];
	final private float[] values = new float[3];
	final private float[] r2 = new float[9];

	@SuppressWarnings("SuspiciousNameCombination")
	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if(event.sensor == magnetómetro)
		{
			System.arraycopy(event.values, 0, geomagnetic, 0, 3);
			hayInfoDeMagnetómetro = true;
		} else if(event.sensor == acelerómetro)
		{
			System.arraycopy(event.values, 0, gravity, 0, 3);
			hayInfoDeAcelerómetro = true;
		}
		if(hayInfoDeAcelerómetro && hayInfoDeMagnetómetro)
		{
			SensorManager.getRotationMatrix(r, null, gravity, geomagnetic);
			int axisX;
			int axisY;
			switch(rotación)
			{
				case Surface.ROTATION_0:
					axisX = SensorManager.AXIS_X;
					axisY = SensorManager.AXIS_Y;
					break;
				case Surface.ROTATION_90:
					axisX = SensorManager.AXIS_Y;
					axisY = SensorManager.AXIS_MINUS_X;
					break;
				case Surface.ROTATION_180:
					axisX = SensorManager.AXIS_MINUS_X;
					axisY = SensorManager.AXIS_MINUS_Y;
					break;
				case Surface.ROTATION_270:
					axisX = SensorManager.AXIS_MINUS_Y;
					axisY = SensorManager.AXIS_X;
					break;
				default:
					throw new RuntimeException("rot: " + rotación);
			}
			SensorManager.remapCoordinateSystem(r, axisX, axisY, r2);
			SensorManager.getOrientation(r2, values);
			double brújula = Math.toDegrees(values[0]);
			if(declinaciónMagnética != Float.MAX_VALUE)
				brújula += declinaciónMagnética;
			if(brújula < 0)
				brújula += 360;
			else if(brújula >= 360)
				brújula -= 360;
			nuevaOrientación(brújula);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }

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
		if(declinaciónMagnética == Float.MAX_VALUE)
		{
			declinaciónMagnética = new GeomagneticField((float)coordsUsuario.getLatitude(), (float)coordsUsuario.getLongitude(), alturaUsuario, System.currentTimeMillis()).getDeclination();
			if(Log.isLoggable("antenas", Log.DEBUG))
				Log.d("antenas", "Declinación magnética: " + declinaciónMagnética);
		}
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		List<Antena> antenasCerca = Antena.dameAntenasCerca(this, coordsUsuario,
				maxDist,
				prefs.getBoolean("menos", true));
		if(!menúConfigurado)
		{
			Set<País> países = EnumSet.noneOf(País.class);
			for(Antena antena : antenasCerca)
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
			for(Antena antena : antenasCerca)
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
				return;
			}
		}
		Iterator<Entry<Antena, View>> it = antenaAVista.entrySet().iterator();
		ViewGroup contenedor = (ViewGroup)findViewById(R.id.antenas);
		while(it.hasNext())
		{
			Entry<Antena, View> e = it.next();
			if(!antenasCerca.contains(e.getKey()))
			{
				contenedor.removeView(e.getValue());
				vistaAAntena.remove(e.getValue());
				it.remove();
			} else
			{
				ponéDistancia(e.getKey(), e.getValue());
			}
		}
		LayoutInflater inf = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
		for(Antena a : antenasCerca)
		{
			if(!antenaAVista.containsKey(a))
			{
				if(a.país == País.US && prefs.getBoolean("usar_contornos", true))
				{
					Log.i("antenas", "agregando a la cola a " + a);
					crearThreadContornos();
					colaParaContornos.add(a);
				}

				View v = inf.inflate(R.layout.antena, contenedor, false);
				int n = contenedor.getChildCount(), i;
				for(i = 0 ; i < n ; i++)
				{
					View child = contenedor.getChildAt(i);
					if(vistaAAntena.get(child).dist > a.dist)
					{
						contenedor.addView(v, i);
						break;
					}
				}
				if(i == n)
					contenedor.addView(v);
				v.setOnClickListener(onAntenaClickedListener);
				v.setFocusable(true);
				CharSequence detalleCanales = a.dameDetalleCanales(this);
				TextView tvDesc = (TextView)v.findViewById(R.id.antena_desc);
				TextView tvDetalle = (TextView)v.findViewById(R.id.antena_detalle_canales);
				if(a.descripción != null)
				{
					tvDesc.setText(a.descripción);
					if(detalleCanales != null)
						tvDetalle.setText(detalleCanales);
					else
						tvDetalle.setVisibility(View.GONE);
				} else
				{
					tvDesc.setText(detalleCanales);
					tvDetalle.setVisibility(View.GONE);
				}
				TextView tvPotencia = (TextView)v.findViewById(R.id.antena_potencia);
				if(tvPotencia != null)
					tvPotencia.setText(a.potencia > 0 ? a.potencia + " kW" : null);
				ponéDistancia(a, v);
				antenaAVista.put(a, v);
				vistaAAntena.put(v, a);

				if(!usarBrújula)
				{
					FlechaView f = (FlechaView)v.findViewById(R.id.flecha);
					f.setÁngulo(a.rumboDesde(coordsUsuario));
					f.setMostrarPuntosCardinales(true);
				}
			}
		}
		ContentLoadingProgressBar pb = (ContentLoadingProgressBar)findViewById(R.id.progressBar);
		pb.hide();
		TextView problema = (TextView)findViewById(R.id.problema);
		if(antenasCerca.isEmpty())
		{
			StringBuilder sb = new StringBuilder(getString(R.string.no_se_encontraron_antenas, formatDistance(maxDist)));
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

	private void ponéDistancia(Antena a, View v)
	{
		ponéDistancia(a, (TextView)v.findViewById(R.id.antena_dist));
	}

	protected void ponéDistancia(Antena a, TextView tv)
	{
		tv.setText(formatDistance(a.distanceTo(coordsUsuario)));
	}

	final static private NumberFormat nf = NumberFormat.getNumberInstance(
			"es".equals(Locale.getDefault().getLanguage())
				? new Locale("es", "AR")
				: Locale.getDefault());
	private String formatDistance(double distancia)
	{
		String unit = prefs.getString("unit", "km");
		double f;
		switch(unit)
		{
			case "km":
				f = 1000.0;
				break;
			case "mi":
				f = 1609.344;
				break;
			default:
				throw new RuntimeException("unit: " + unit);
		}
		nf.setMaximumFractionDigits(distancia < f ? 2 : 1);
		return nf.format(distancia / f) + ' ' + unit;
	}

	protected static String formatPower(float potencia)
	{
		nf.setMaximumFractionDigits(potencia < 1 ? 2 : 1);
		return nf.format(potencia) + " kW";
	}

	private void reset()
	{
		((ViewGroup)findViewById(R.id.antenas)).removeAllViews();
		vistaAAntena.clear();
		antenaAVista.clear();
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
}
