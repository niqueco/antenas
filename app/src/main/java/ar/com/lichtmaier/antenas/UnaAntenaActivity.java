package ar.com.lichtmaier.antenas;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorUpdateListener;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.*;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class UnaAntenaActivity extends AntenaActivity implements SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener
{
	private Antena antena;
	private int orientaciónOriginal;
	private int flechaOriginalY;
	private int flechaOriginalX;
	private int flechaOriginalAncho;
	private int flechaOriginalAlto;
	private float escalaAncho;
	private float escalaAlto;
	private int mLeftDelta;
	private int mTopDelta;
	private double ángulo;
	private FlechaView flecha;
	final private List<View> vistasAnimadas = new ArrayList<>();
	private View navigationIcon;
	private boolean mostrarDireccionesRelativas;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		//noinspection ConstantConditions
		getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);

		Bundle bundle = getIntent().getExtras();
		antena = Antena.dameAntena(this, País.valueOf(bundle.getString("ar.com.lichtmaier.antenas.antenaPaís")), bundle.getInt("ar.com.lichtmaier.antenas.antenaIndex"));
		final TextView antenaDesc = findViewById(R.id.antena_desc);
		assert antenaDesc != null;
		if(antena.descripción != null)
			antenaDesc.setText(antena.descripción);
		else
			antenaDesc.setVisibility(View.GONE);
		final TextView tvPotencia = findViewById(R.id.antena_potencia);
		assert tvPotencia != null;
		tvPotencia.setText(antena.potencia > 0 ? Formatos.formatPower(antena.potencia) : null);
		nuevaUbicación(); // para que se configure la distancia

		flechaOriginalY = bundle.getInt(PACKAGE + ".top");
		flechaOriginalX = bundle.getInt(PACKAGE + ".left");
		flechaOriginalAncho = bundle.getInt(PACKAGE + ".width");
		flechaOriginalAlto = bundle.getInt(PACKAGE + ".height");
		orientaciónOriginal = bundle.getInt(PACKAGE + ".orientation");
		ángulo = bundle.getDouble(PACKAGE + ".ángulo");
		double ánguloDibujado = bundle.getDouble(PACKAGE + ".ánguloDibujado");
		boolean animar = bundle.getBoolean(PACKAGE + ".animar");

		flecha = findViewById(R.id.flecha);
		assert flecha != null;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		flecha.setMostrarAlineación(prefs.getBoolean("mostrarAlineación", false));
		prefs.registerOnSharedPreferenceChangeListener(this);

		if(bundle.getBoolean(PACKAGE + ".sinValor"))
			flecha.sinValor(false);

		configurarMostrarDireccionesRelativas();

		if(antena.canales != null && !antena.canales.isEmpty())
		{
			View antes = findViewById(R.id.antes_de_canales);
			if(antes != null)
				antes.setVisibility(View.VISIBLE);
			ViewGroup p = findViewById(R.id.columna_derecha);
			if(p == null)
				p = findViewById(R.id.principal);
			assert p != null;
			boolean hayImágenes = antena.hayImágenes();
			float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
			float density = getResources().getDisplayMetrics().density;
			for(Canal canal : antena.canales)
			{
				View vc = canal.dameViewCanal(this, p, hayImágenes, true, antena.país.equals(País.AU));
				ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams((int)(200 * scaledDensity), ViewGroup.LayoutParams.WRAP_CONTENT);
				lp.setMargins(0, 0, 0, (int)(8 * density));
				p.addView(vc, lp);
				if(p.getId() != R.id.columna_derecha)
					vistasAnimadas.add(vc);

				if(antena.país == País.US)
				{
					vc.setClickable(true);
					vc.setFocusable(true);
					vc.setTag(canal);
					vc.setOnClickListener(this);
				}
			}
			if(p.getId() == R.id.columna_derecha)
			{
				if(p.getParent() instanceof ScrollView)
					p = (ViewGroup)p.getParent();
				vistasAnimadas.add(p);
			}
		}

		if(savedInstanceState == null && brújula == null && !mostrarDireccionesRelativas)
			flecha.setMostrarPuntosCardinales(true);

		if(savedInstanceState == null && animar)
		{
			flecha.setÁngulo(ángulo, ánguloDibujado);
			flecha.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
			{
				@Override
				public boolean onPreDraw()
				{
					flecha.getViewTreeObserver().removeOnPreDrawListener(this);

					calcularDeltas();

					View fondo = findViewById(R.id.fondo);
					assert fondo != null;

					flecha.setScaleX(escalaAncho);
					flecha.setScaleY(escalaAlto);
					flecha.setPivotX(0);
					flecha.setPivotY(0);
					flecha.setTranslationX(mLeftDelta);
					flecha.setTranslationY(mTopDelta);
					ViewCompat.animate(flecha)
							.scaleX(1)
							.scaleY(1)
							.translationX(0)
							.translationY(0)
							.setInterpolator(new AccelerateDecelerateInterpolator())
							.setDuration(400)
							.setUpdateListener(new ViewPropertyAnimatorUpdateListener()
							{
								private int fr = 0;
								@Override
								public void onAnimationUpdate(View view)
								{
									if(fr++ == 1 && AntenaActivity.flechaADesaparecer != null)
										flechaADesaparecer.setVisibility(View.INVISIBLE);
								}
							});

					fondo.setAlpha(0);
					ViewCompat.animate(fondo)
							.alpha(1)
							.setDuration(500)
							.setInterpolator(new AccelerateInterpolator())
							.withEndAction(() -> {
								if(AntenaActivity.flechaADesaparecer != null)
									AntenaActivity.flechaADesaparecer.setVisibility(View.VISIBLE);
							});

					Toolbar tb = findViewById(R.id.toolbar);
					assert tb != null;

					tb.setAlpha(0);
					tb.animate()
						.alpha(1)
						.setDuration(200)
						.setInterpolator(new AccelerateInterpolator());

					int n = tb.getChildCount();
					for(int i = 0 ; i < n ; i++)
					{
						View v = tb.getChildAt(i);
						if(v instanceof ImageButton && tb.getNavigationIcon() == ((ImageButton)v).getDrawable())
						{
							navigationIcon = v;
							break;
						}
					}

					if(navigationIcon != null)
					{
						AlphaAnimation ab = new AlphaAnimation(0, 1);
						ab.setDuration(300);
						ab.setStartOffset(300);
						ab.setInterpolator(new DecelerateInterpolator());
						navigationIcon.startAnimation(ab);
					}

					vistasAnimadas.add(antenaDesc);
					TextView antenaDist = findViewById(R.id.antena_dist);
					vistasAnimadas.add(antenaDist);
					vistasAnimadas.add(tvPotencia);
					int d = getWindow().getDecorView().getBottom();
					for(View v : vistasAnimadas)
					{
						if(v.getTop() > d)
							continue;
						v.setTranslationY(d);
						ViewCompat.animate(v)
							.translationY(0)
							.setInterpolator(new DecelerateInterpolator())
							.setDuration(500)
							.setStartDelay(200)
							.withLayer();
					}

					return true;
				}
			});
		}
	}

	private void calcularDeltas()
	{
		int[] screenLocation = new int[2];
		flecha.getLocationOnScreen(screenLocation);
		mLeftDelta = flechaOriginalX - screenLocation[0];
		mTopDelta = flechaOriginalY - screenLocation[1];

		escalaAncho = (float) flechaOriginalAncho / flecha.getWidth();
		escalaAlto = (float) flechaOriginalAlto / flecha.getHeight();
	}

	@Override
	public void onBackPressed()
	{
		cerrar();
	}

	private void cerrar()
	{
		if (getResources().getConfiguration().orientation != orientaciónOriginal)
		{
			finish();
			return;
		}
		calcularDeltas();
		if(AntenaActivity.flechaADesaparecer != null)
			AntenaActivity.flechaADesaparecer.setVisibility(View.INVISIBLE);
		View fondo = findViewById(R.id.fondo);
		assert fondo != null;
		View flecha = findViewById(R.id.flecha);
		assert flecha != null;
		AlphaAnimation aa = new AlphaAnimation(1, 0);
		aa.setDuration(400);
		aa.setInterpolator(new AccelerateInterpolator());
		aa.setFillAfter(true);
		fondo.startAnimation(aa);

		ViewCompat.animate(fondo)
			.alpha(0)
			.setDuration(400)
			.setInterpolator(new AccelerateInterpolator())
			.withEndAction(() -> {
				finish();
				if(AntenaActivity.flechaADesaparecer != null)
				{
					AntenaActivity.flechaADesaparecer.setÁngulo(ángulo);
					if(brújula != null && brújula.sinValor())
						AntenaActivity.flechaADesaparecer.sinValor(false);
					AntenaActivity.flechaADesaparecer.setVisibility(View.VISIBLE);
				}
			});

		Toolbar tb = findViewById(R.id.toolbar);
		assert tb != null;
		tb.animate()
			.alpha(0)
			.setStartDelay(200)
			.setDuration(200)
			.setInterpolator(new AccelerateInterpolator());

		if(navigationIcon != null)
			ViewCompat.animate(navigationIcon)
				.alpha(0)
				.setDuration(400)
				.setInterpolator(new AccelerateInterpolator())
				.withLayer();

		flecha.setPivotX(0);
		flecha.setPivotY(0);
		flecha.animate()
			.scaleX(escalaAncho)
			.scaleY(escalaAlto)
			.translationX(mLeftDelta)
			.translationY(mTopDelta)
			.setInterpolator(new AccelerateDecelerateInterpolator())
			.setDuration(400);

		int d = getWindow().getDecorView().getBottom();
		for(View v : vistasAnimadas)
			ViewCompat.animate(v)
				.translationY(d)
				.setInterpolator(new AccelerateInterpolator())
				.setDuration(400)
				.withLayer();

	}

	@Override
	public void finish()
	{
		super.finish();
		if (getResources().getConfiguration().orientation == orientaciónOriginal)
			overridePendingTransition(0, 0);
	}

	@Override
	protected void asignarLayout()
	{
		setContentView(R.layout.activity_una_antena);
	}

	@Override
	protected void nuevaUbicación()
	{
		if(antena != null)
		{
			Lugar l = Lugar.actual.getValue();
			TextView antenaDist = findViewById(R.id.antena_dist);
			antenaDist.setText(Formatos.formatDistance(this, antena.distanceTo(l == null ? AntenaActivity.coordsUsuario : l.coords)));
		}
	}

	@Override
	public void nuevaOrientación(double brújula)
	{
		if(!mostrarDireccionesRelativas)
			return;
		double rumbo = antena.rumboDesde(coordsUsuario);
		FlechaView f = findViewById(R.id.flecha);
		assert f != null;
		ángulo = rumbo - brújula;
		f.setÁngulo(ángulo);
	}

	@Override
	public void desorientados()
	{
		if(!mostrarDireccionesRelativas)
			return;
		flecha.sinValor(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if(item.getItemId() == android.R.id.home)
		{
			cerrar();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if(key.equals("mostrarAlineación"))
			flecha.setMostrarAlineación(sharedPreferences.getBoolean("mostrarAlineación", false));
		else if(key.equals("forzar_direcciones_absolutas"))
			configurarMostrarDireccionesRelativas();
	}

	private void configurarMostrarDireccionesRelativas()
	{
		this.mostrarDireccionesRelativas = brújula != null && !PreferenceManager.getDefaultSharedPreferences(this).getBoolean("forzar_direcciones_absolutas", false) && Lugar.actual.getValue() == null;
		flecha.setÁngulo(antena.rumboDesde(coordsUsuario), false);
		flecha.setMostrarPuntosCardinales(!mostrarDireccionesRelativas);
	}

	@Override
	public void onClick(View view)
	{
		Canal canal = (Canal)view.getTag();
		Intent intent = new Intent(this, MapaActivity.class);
		intent.putExtra("ar.com.lichtmaier.antenas.antena", antena);
		intent.putExtra("ar.com.lichtmaier.antenas.canal", antena.canales.indexOf(canal));
		ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight());
		if(intersticial != null)
			intersticial.siguienteActividad(this, intent, options.toBundle());
		else
			ActivityCompat.startActivity(this, intent, options.toBundle());
	}
}
