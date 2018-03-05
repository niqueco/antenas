package ar.com.lichtmaier.antenas;

import java.lang.ref.WeakReference;
import java.util.*;

import org.gavaghan.geodesy.GlobalCoordinates;

import android.Manifest;
import android.annotation.SuppressLint;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.*;
import android.util.Log;
import android.view.*;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;

import ar.com.lichtmaier.antenas.location.LocationLiveData;
import ar.com.lichtmaier.antenas.location.LocationManagerLiveData;

public class AntenaActivity extends AppCompatActivity implements Brújula.Callback
{
	public static final String PACKAGE = "ar.com.lichtmaier.antenas";
	private static final int PEDIDO_DE_PERMISO_FINE_LOCATION = 131;
	private static final int PRECISIÓN_ACEPTABLE = 150;
	private static final int REQUEST_CODE_ELEGIR_LUGAR = 889;

	private static final String PREF_PAGAME_MES_MOSTRADO = "pagame_mes_mostrado";
	private static final String PREF_LANZAMIENTOS = "lanzamientos";

	static GlobalCoordinates coordsUsuario;
	private static float alturaUsuario;
	protected Brújula brújula;
	private AntenasAdapter antenasAdapter;
	private Publicidad publicidad;
	private boolean huboSavedInstanceState;
	private boolean seMuestraRuegoDePermisos;

	private MenuItem opciónAyudaArgentina, opciónAyudaReinoUnido, opciónPagar;
	private boolean mostrarOpciónAyudaArgentina = false, mostrarOpciónAyudaReinoUnido = false;

	private long comienzoUsoPantalla;

	static {
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

	private AyudanteDePagos ayudanteDePagos;

	static FlechaView flechaADesaparecer;
	private boolean abriendoAntena = false;
	private final AntenasAdapter.Callback onAntenaClickedListener = new AntenasAdapter.Callback()
	{
		@Override
		public void onAntenaClicked(final Antena antena, final View v)
		{
			if(abriendoAntena)
				return;
			abriendoAntena = true;

			if(intersticial == null || prefs.getInt(PREF_LANZAMIENTOS, 0) < 2)
			{
				abrir(antena, v, true);
			} else
			{
				intersticial.mostrar(huboAviso -> abrir(antena, v, !huboAviso));
			}
		}

		private void abrir(Antena antena, View v, boolean animar)
		{
			Intent i = new Intent(AntenaActivity.this, UnaAntenaActivity.class);

			int[] screenLocation = new int[2];
			FlechaView flecha = v.findViewById(R.id.flecha);
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
					putExtra(PACKAGE + ".sinValor", brújula != null && brújula.sinValor()).
					putExtra(PACKAGE + ".animar", animar);
			flechaADesaparecer = flecha;

			startActivity(i);

			if(animar)
				overridePendingTransition(0, 0);
		}

		@Override
		public void onAdapterReady()
		{
			terminarDeConfigurar();
		}
	};
	protected Publicidad.Intersticial intersticial;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		asignarLayout();

		Toolbar tb = findViewById(R.id.toolbar);
		setSupportActionBar(tb);
		final ActionBar actionBar = getSupportActionBar();
		assert actionBar != null;
		actionBar.setDisplayShowTitleEnabled(false);

		ayudanteDePagos = AyudanteDePagos.dameInstancia(this);
		ayudanteDePagos.observe(this, this::esPro);

		ProgressBar pb = findViewById(R.id.progressBar);
		if(pb != null)
		{
			prenderAnimación = new PrenderAnimación(pb);
			pb.postDelayed(prenderAnimación, 400);
		}

		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if(!prefs.getBoolean("unidad_configurada", false))
		{
			Locale locale = Locale.getDefault();
			SharedPreferences.Editor editor = prefs.edit()
					.putString("unit", ("US".equals(locale.getCountry()) && (!"es".equals(locale.getLanguage()))) ? "mi" : "km")
					.putBoolean("unidad_configurada", true);
			editor.apply();
		}

		brújula = Brújula.crear(this);

		if(brújula == null && !(this instanceof UnaAntenaActivity) && !Build.FINGERPRINT.equals(prefs.getString("aviso_no_brújula",null)))
		{
			View principal = findViewById(R.id.principal);
			Snackbar sb = Snackbar.make(principal, R.string.aviso_no_hay_brújula, Snackbar.LENGTH_INDEFINITE)
					.addCallback(new Snackbar.Callback()
					{
						@Override
						public void onDismissed(Snackbar snackbar, int event)
						{
							if(event == DISMISS_EVENT_ACTION || event == DISMISS_EVENT_SWIPE)
								prefs.edit().putString("aviso_no_brújula", Build.FINGERPRINT).apply();
						}
					});
			TextView tv = sb.getView().findViewById(android.support.design.R.id.snackbar_text);
			if(tv != null)
				tv.setMaxLines(4);
			sb.show();
		}
		if(brújula != null)
		{
			prefs.edit().remove("aviso_no_brújula").apply();
			brújula.registerListener(this);
		}

		final RecyclerView rv = findViewById(R.id.antenas);

		if(rv != null)
		{
			antenasAdapter = new AntenasAdapter(this, brújula, onAntenaClickedListener, R.layout.antena);
			rv.setAdapter(antenasAdapter);
			final RecyclerView.LayoutManager rvLayoutManager = rv.getLayoutManager();
			if(rvLayoutManager instanceof LinearLayoutManager)
				rv.addItemDecoration(new DivisoresItemDecoration(getResources().getDimension(R.dimen.alto_divisor), ContextCompat.getColor(this, R.color.líneas)));
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
			RecyclerView.ItemAnimator animator = rv.getItemAnimator();
			if(animator instanceof SimpleItemAnimator)
				((SimpleItemAnimator)animator).setSupportsChangeAnimations(false);
		}


		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION))
			{
				ViewGroup principal = findViewById(R.id.principal);
				View view = getLayoutInflater().inflate(R.layout.permiso_necesario, principal, false);
				((TextView)view.findViewById(android.R.id.text1)).setText(R.string.explicacion_permiso_gps);
				view.findViewById(R.id.button).setOnClickListener(v -> ActivityCompat.requestPermissions(AntenaActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_FINE_LOCATION));
				principal.addView(view);
				seMuestraRuegoDePermisos = true;
			} else
			{
				ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_FINE_LOCATION);
			}
		} else
		{
			crearLocationLiveData();
		}

		if(savedInstanceState != null && savedInstanceState.containsKey("lat"))
			nuevaUbicación(savedInstanceState.getDouble("lat"), savedInstanceState.getDouble("lon"), savedInstanceState.getDouble("alt"));

		huboSavedInstanceState = savedInstanceState != null;

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
					ViewCompat.setElevation(findViewById(R.id.toolbar_wrapper), Math.min(y / 8f, density * 8f));
				}
			});
		}

		if(!(this instanceof UnaAntenaActivity) && !huboSavedInstanceState)
		{
			int lanzamientos = prefs.getInt(PREF_LANZAMIENTOS, 0) + 1;
			prefs.edit().putInt(PREF_LANZAMIENTOS, lanzamientos).apply();

			Calificame.registrarLanzamiento(this);
		}

		View botónLimpiarLugar = findViewById(R.id.place_cerrar);
		if(botónLimpiarLugar != null)
			botónLimpiarLugar.setOnClickListener(view -> Lugar.actual.setValue(null));

		Lugar.restore(savedInstanceState);

		Lugar.actual.observe(this, l -> {
			View pl = findViewById(R.id.place);
			if(l != null)
			{
				if(pl != null)
				{
					pl.setVisibility(View.VISIBLE);
					((TextView)findViewById(R.id.place_text)).setText(String.format(getString(R.string.título_lugar), l.name));
				}
				if(antenasAdapter != null)
					antenasAdapter.setForzarDireccionesAbsolutas(true);
			} else
			{
				if(pl != null)
					pl.setVisibility(View.GONE);
				if(antenasAdapter != null)
					antenasAdapter.setForzarDireccionesAbsolutas(false);
				if(locationLiveData != null)
					locationLiveData.observe(this, locationObserver);
			}
			nuevaUbicación();

		});
	}

	@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
	private void crearLocationLiveData()
	{
		if(seMuestraRuegoDePermisos)
		{
			ViewGroup principal = findViewById(R.id.principal);
			View pedido = principal.findViewById(R.id.pedido_de_permisos);
			principal.removeView(pedido);
		}

		ProgressBar pb = findViewById(R.id.progressBar);
		if(pb != null)
			pb.postDelayed(avisoDemora = new AvisoDemora(this), 15000);

		locationLiveData = LocationLiveData.create(this, LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(10000)
				.setFastestInterval(2000)
				.setSmallestDisplacement(10), PRECISIÓN_ACEPTABLE);

		locationLiveData.inicializarConPermiso();
		locationLiveData.observe(this, locationObserver);

		if(locationLiveData instanceof LocationManagerLiveData)
			pedirCambioConfiguración();
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_FINE_LOCATION)
		{
			if(grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
			{
				if(locationLiveData != null)
				{
					Crashlytics.log(Log.ERROR, "antenas", "locationClient no es null en onRequestPermissionsResult");
					return;
				}

				crearLocationLiveData();
			} else
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
		Lugar.save(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.antena, menu);
		opciónAyudaArgentina = menu.findItem(R.id.action_ayuda_ar);
		opciónAyudaReinoUnido = menu.findItem(R.id.action_ayuda_uk);
		configurarMenú();
		opciónPagar = menu.findItem(R.id.action_pagar);
		Boolean pro = ayudanteDePagos.getValue();
		if(pro != null)
			opciónPagar.setVisible(!pro);
		if(this instanceof UnaAntenaActivity)
			menu.findItem(R.id.action_elegir_lugar).setVisible(false);
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
				configurarIntentParaAbrirMapa(i);
				if(intersticial != null)
					intersticial.siguienteActividad(this, i, null);
				else
					startActivity(i);
				return true;
			case R.id.action_elegir_lugar:
				if(!funciónProHabilitada())
					return true;
				try
				{
					i = new PlacePicker.IntentBuilder().build(this);
					startActivityForResult(i, REQUEST_CODE_ELEGIR_LUGAR);
				} catch(GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e)
				{
					Log.e("antenas", "PlacePicker error", e);
				}
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
			case R.id.action_pagar:
				pagar();
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
				if(i.resolveActivity(getPackageManager()) != null)
					startActivity(i);
				else
					Toast.makeText(this, R.string.app_no_disponible, Toast.LENGTH_LONG).show();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void configurarIntentParaAbrirMapa(Intent i) { }

	private boolean funciónProHabilitada()
	{
		if(ayudanteDePagos.getValue() == null)
		{
			Toast.makeText(this, R.string.pago_no_verificado, Toast.LENGTH_SHORT).show();
			return false;
		}
		if(!ayudanteDePagos.getValue())
		{
			View v = findViewById(R.id.principal);
			Snackbar.make(v, R.string.cambiar_lugar_cuesta, 7000)
					.setAction(R.string.ver_precio, view -> pagar())
					.show();
			return false;
		}
		return true;
	}

	void pagar()
	{
		ayudanteDePagos.pagar(this);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		if(brújula != null)
			brújula.onResume(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		abriendoAntena = false;
		comienzoUsoPantalla = System.currentTimeMillis();
	}

	@Override
	protected void onPause()
	{
		if(System.currentTimeMillis() - comienzoUsoPantalla > 1000 * 30)
			Calificame.registráQueMiróLasAntenas(this);
		super.onPause();
	}

	@Override
	protected void onStop()
	{
		if(brújula != null)
			brújula.onPause(this);
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		if(brújula != null)
			brújula.removeListener(this);
		if(antenasAdapter != null)
			antenasAdapter.onDestroy();
		super.onDestroy();
	}

	@Nullable private LocationLiveData locationLiveData;
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
		Lugar l = Lugar.actual.getValue();
		if(coordsUsuario == null && l == null)
			return;
		if(brújula != null && coordsUsuario != null)
			brújula.setCoordinates(coordsUsuario.getLatitude(), coordsUsuario.getLongitude(), alturaUsuario);

		antenasAdapter.nuevaUbicación(l != null ? l.coords : coordsUsuario);
	}

	/** Se llama cuando antenasAdapter avisa que ya está toda la información. */
	private void terminarDeConfigurar()
	{
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		int cantAntenas = antenasAdapter.getItemCount();
		if(!menúConfigurado)
		{
			Set<País> países = EnumSet.noneOf(País.class);
			for(int i = 0; i < cantAntenas; i++)
				países.add(antenasAdapter.getItem(i).antena.país);
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
				editor.apply();
			}
			menúConfigurado = true;
		}

		// Si estamos en EE.UU. la distancia máxima default se establece en 100 km.
		if(maxDist == 60000 && !prefs.getBoolean("distancia_configurada", false))
		{
			Set<País> países = EnumSet.noneOf(País.class);
			for(int i = 0 ; i < cantAntenas ; i++)
				países.add(antenasAdapter.getItem(i).antena.país);
			SharedPreferences.Editor editor = prefs.edit();
			boolean volver = false;
			if(países.contains(País.US))
			{
				editor.putString("max_dist", "100");
				volver = true;
			}
			editor.putBoolean("distancia_configurada", true).apply();
			if(volver)
			{
				nuevaUbicación();
			}
		}

		final ProgressBar pb = findViewById(R.id.progressBar);
		if(pb != null)
		{
			if(prenderAnimación.comienzoAnimación != -1)
			{
				long falta = 600 - (System.currentTimeMillis() - prenderAnimación.comienzoAnimación);
				if(falta <= 0)
					pb.setVisibility(View.GONE);
				else
					pb.postDelayed(() -> pb.setVisibility(View.GONE), falta);
			} else
			{
				prenderAnimación.cancelado = true;
				pb.removeCallbacks(prenderAnimación);
			}
			prenderAnimación = null;

			if(avisoDemora != null)
			{
				avisoDemora.cancelado = true;
				pb.removeCallbacks(avisoDemora);
				avisoDemora = null;
			}
		}
		TextView problema = findViewById(R.id.problema);
		assert problema != null;
		if(cantAntenas == 0)
		{
			((ViewGroup.MarginLayoutParams)problema.getLayoutParams()).topMargin = 0;
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

	private void pedirCambioConfiguración()
	{
		LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		if(locationManager == null)
			throw new NullPointerException();
		if(!huboSavedInstanceState && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
		{
			View v = findViewById(R.id.antenas);
			if(v == null)
				v = findViewById(R.id.root);
			Snackbar.make(v, R.string.gps_is_off, Snackbar.LENGTH_INDEFINITE)
					.setAction(R.string.gps_prender, v1 -> startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
					.show();
		}
	}

	final private Observer<Location> locationObserver = new Observer<Location>()
	{
		@Override
		public void onChanged(@Nullable Location location)
		{
			if(location == null)
				return;
			if(publicidad != null)
				publicidad.load(location);
			nuevaUbicación(location);
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(requestCode == REQUEST_CODE_ELEGIR_LUGAR)
		{
			if(data == null)
				return;

			if(locationLiveData != null)
				locationLiveData.removeObservers(this);

			if(antenasAdapter != null)
				antenasAdapter.setForzarDireccionesAbsolutas(true);

			Place place = PlacePicker.getPlace(this, data);
			Log.i("antenas", "place=" + place);
			Lugar.actual.setValue(Lugar.from(place));

			return;
		}
		if(locationLiveData != null && locationLiveData.onActivityResult(requestCode, resultCode, data))
			return;
		if(ayudanteDePagos.onActivityResult(requestCode, resultCode, data))
			return;
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void faltaCalibrar()
	{
		try {
			if(!getSupportFragmentManager().isStateSaved())
				CalibrarBrújulaFragment.mostrar(this);
		} catch(Exception e) {
			Crashlytics.logException(e);
		}
	}

	@Override
	public void desorientados() { }

	@Override
	public void nuevaOrientación(double orientación) { }

	private PrenderAnimación prenderAnimación;

	private void esPro(Boolean pro)
	{
		if(pro == null)
			return;
		if(opciónPagar != null)
			opciónPagar.setVisible(!pro);
		if(pro)
		{
			if(publicidad != null)
			{
				publicidad.sacar();
				publicidad = null;
			}
			if(intersticial != null)
			{
				intersticial = null;
			}
		} else
		{
			if(publicidad == null)
			{
				publicidad = new Publicidad(this, getLifecycle(), this instanceof UnaAntenaActivity
						? "ca-app-pub-0461170458442008/1711829752"
						: "ca-app-pub-0461170458442008/6164714153");
				intersticial = publicidad.crearIntersticial(this, "ca-app-pub-0461170458442008/1312574153");
			}

			if(!(this instanceof UnaAntenaActivity) && !huboSavedInstanceState)
			{
				int lanzamientos = prefs.getInt(PREF_LANZAMIENTOS, 0);
				if(lanzamientos > 4 && !Calificame.mostrando(this))
				{
					try
					{
						long installTime = getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0).firstInstallTime;
						int mesMostrado = prefs.getInt(PREF_PAGAME_MES_MOSTRADO, -1);
						int días = (int)((System.currentTimeMillis() - installTime) / (1000L * 3600L * 24L));
						int meses = días / 30;
						if((mesMostrado < meses) && (días > 1))
						{
							prefs.edit().putInt(PREF_PAGAME_MES_MOSTRADO, meses).apply();
							Pagame.mostrar(this);
						}
					} catch(Exception e)
					{
						Crashlytics.logException(e);
					}
				}
			}
		}
	}

	private static class PrenderAnimación implements Runnable
	{
		private final View pb;
		long comienzoAnimación = -1;
		boolean cancelado = false;

		PrenderAnimación(View pb)
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

	private AvisoDemora avisoDemora;

	private static class AvisoDemora implements Runnable
	{
		private final WeakReference<AntenaActivity> actRef;
		boolean cancelado = false;

		private AvisoDemora(AntenaActivity act)
		{
			this.actRef = new WeakReference<>(act);
		}

		@Override
		public void run()
		{
			if(cancelado)
				return;
			AntenaActivity act = actRef.get();
			if(act == null)
				return;

			TextView problemaView = act.findViewById(R.id.problema);
			problemaView.setText(R.string.aviso_demora_ubicación);
			problemaView.setVisibility(View.VISIBLE);
			((ViewGroup.MarginLayoutParams)problemaView.getLayoutParams()).topMargin = (int)(48 * act.getResources().getDisplayMetrics().density);
		}
	}

	private static class DivisoresItemDecoration extends RecyclerView.ItemDecoration
	{
		private final float alto;
		private final Paint pintura;

		DivisoresItemDecoration(float alto, @ColorInt int color)
		{
			this.alto = alto;
			pintura = new Paint();
			pintura.setColor(color);
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
