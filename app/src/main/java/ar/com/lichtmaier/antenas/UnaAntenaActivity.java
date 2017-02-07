package ar.com.lichtmaier.antenas;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		//noinspection ConstantConditions
		getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);

		Bundle bundle = getIntent().getExtras();
		antena = Antena.dameAntena(this, País.valueOf(bundle.getString("ar.com.lichtmaier.antenas.antenaPaís")), bundle.getInt("ar.com.lichtmaier.antenas.antenaIndex"));
		final TextView antenaDesc = (TextView) findViewById(R.id.antena_desc);
		assert antenaDesc != null;
		if(antena.descripción != null)
			antenaDesc.setText(antena.descripción);
		else
			antenaDesc.setVisibility(View.GONE);
		final TextView tvPotencia = (TextView)findViewById(R.id.antena_potencia);
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

		flecha = (FlechaView)findViewById(R.id.flecha);
		assert flecha != null;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		flecha.setMostrarAlineación(prefs.getBoolean("mostrarAlineación", false));
		prefs.registerOnSharedPreferenceChangeListener(this);

		if(bundle.getBoolean(PACKAGE + ".sinValor"))
			flecha.sinValor(false);

		if(antena.canales != null && !antena.canales.isEmpty())
		{
			View antes = findViewById(R.id.antes_de_canales);
			if(antes != null)
				antes.setVisibility(View.VISIBLE);
			ViewGroup p = (ViewGroup)findViewById(R.id.columna_derecha);
			if(p == null)
				p = (ViewGroup)findViewById(R.id.principal);
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

		if(savedInstanceState == null && brújula == null)
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

					if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					{
						AnimationSet anim = new AnimationSet(true);
						anim.addAnimation(new ScaleAnimation(escalaAncho, 1, escalaAlto, 1, 0, 0));
						anim.addAnimation(new TranslateAnimation(mLeftDelta, 0, mTopDelta, 0));
						anim.setInterpolator(new AccelerateDecelerateInterpolator());
						anim.setDuration(400);
						flecha.startAnimation(anim);

						AlphaAnimation aa = new AlphaAnimation(0, 1);
						aa.setDuration(500);
						aa.setInterpolator(new AccelerateInterpolator());
						fondo.startAnimation(aa);

						if(AntenaActivity.flechaADesaparecer != null)
						{
							anim.setAnimationListener(new Animation.AnimationListener()
							{
								@Override
								public void onAnimationStart(Animation animation)
								{
									flechaADesaparecer.setVisibility(View.INVISIBLE);
								/*
								flechaADesaparecer.postDelayed(new Runnable()
								{
									@Override
									public void run()
									{
										flechaADesaparecer.setVisibility(View.INVISIBLE);
									}
								}, 200);*/
								}

								@Override
								public void onAnimationEnd(Animation animation) { }

								@Override
								public void onAnimationRepeat(Animation animation) { }
							});

							aa.setAnimationListener(new Animation.AnimationListener()
							{
								@Override
								public void onAnimationStart(Animation animation) { }

								@Override
								public void onAnimationEnd(Animation animation)
								{
									AntenaActivity.flechaADesaparecer.setVisibility(View.VISIBLE);
								}

								@Override
								public void onAnimationRepeat(Animation animation) { }
							});
						}

					} else
					{
						ViewCompat.setScaleX(flecha, escalaAncho);
						ViewCompat.setScaleY(flecha, escalaAlto);
						ViewCompat.setPivotX(flecha, 0);
						ViewCompat.setPivotY(flecha, 0);
						ViewCompat.setTranslationX(flecha, mLeftDelta);
						ViewCompat.setTranslationY(flecha, mTopDelta);
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

						ViewCompat.setAlpha(fondo, 0);
						ViewCompat.animate(fondo)
								.alpha(1)
								.setDuration(500)
								.setInterpolator(new AccelerateInterpolator())
								.withEndAction(new Runnable()
								{
									@Override
									public void run()
									{
										AntenaActivity.flechaADesaparecer.setVisibility(View.VISIBLE);
									}
								});
					}

					Toolbar tb = (Toolbar)findViewById(R.id.toolbar);
					assert tb != null;
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
					TextView antenaDist = (TextView) findViewById(R.id.antena_dist);
					vistasAnimadas.add(antenaDist);
					vistasAnimadas.add(tvPotencia);
					int d = getWindow().getDecorView().getBottom();
					for(View v : vistasAnimadas)
					{
						if(v.getTop() > d)
							continue;
						if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
						{
							TranslateAnimation ta = new TranslateAnimation(0, 0, d, 0);
							ta.setInterpolator(new DecelerateInterpolator());
							ta.setDuration(500);
							ta.setStartOffset(200);
							v.startAnimation(ta);
						} else
						{
							v.setTranslationY(d);
							ViewCompat.animate(v)
								.translationY(0)
								.setInterpolator(new DecelerateInterpolator())
								.setDuration(500)
								.setStartDelay(200)
								.withLayer();
						}
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
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			AlphaAnimation aa = new AlphaAnimation(1, 0);
			aa.setDuration(400);
			aa.setInterpolator(new AccelerateInterpolator());
			aa.setFillAfter(true);
			fondo.startAnimation(aa);
			if(navigationIcon != null)
			{
				AlphaAnimation ab = new AlphaAnimation(1, 0);
				ab.setDuration(400);
				ab.setInterpolator(new AccelerateInterpolator());
				ab.setFillAfter(true);
				navigationIcon.startAnimation(ab);
			}
			aa.setAnimationListener(new Animation.AnimationListener()
			{
				@Override
				public void onAnimationStart(Animation animation)
				{
				}

				@Override
				public void onAnimationEnd(Animation animation)
				{
					finish();
					if(AntenaActivity.flechaADesaparecer != null)
					{
						AntenaActivity.flechaADesaparecer.setÁngulo(ángulo);
						if(brújula != null && brújula.sinValor())
							AntenaActivity.flechaADesaparecer.sinValor(false);
						AntenaActivity.flechaADesaparecer.setVisibility(View.VISIBLE);
					}
				}

				@Override
				public void onAnimationRepeat(Animation animation)
				{
				}
			});

			AnimationSet anim = new AnimationSet(true);
			anim.addAnimation(new ScaleAnimation(1, escalaAncho, 1, escalaAlto, 0, 0));
			anim.addAnimation(new TranslateAnimation(0, mLeftDelta, 0, mTopDelta));
			anim.setInterpolator(new AccelerateDecelerateInterpolator());
			anim.setDuration(400);
			anim.setFillAfter(true);
			flecha.startAnimation(anim);

			int d = getWindow().getDecorView().getBottom();
			for(View v : vistasAnimadas)
			{
				TranslateAnimation ta = new TranslateAnimation(0, 0, 0, d);
				ta.setInterpolator(new AccelerateInterpolator());
				ta.setDuration(400);
				ta.setFillAfter(true);
				v.startAnimation(ta);
			}
		} else
		{
			AlphaAnimation aa = new AlphaAnimation(1, 0);
			aa.setDuration(400);
			aa.setInterpolator(new AccelerateInterpolator());
			aa.setFillAfter(true);
			fondo.startAnimation(aa);

			ViewCompat.animate(fondo)
				.alpha(0)
				.setDuration(400)
				.setInterpolator(new AccelerateInterpolator())
				.withEndAction(new Runnable()
				{
					@Override
					public void run()
					{
						finish();
						if(AntenaActivity.flechaADesaparecer != null)
						{
							AntenaActivity.flechaADesaparecer.setÁngulo(ángulo);
							if(brújula != null && brújula.sinValor())
								AntenaActivity.flechaADesaparecer.sinValor(false);
							AntenaActivity.flechaADesaparecer.setVisibility(View.VISIBLE);
						}
					}
				});

			if(navigationIcon != null)
				ViewCompat.animate(navigationIcon)
					.alpha(0)
					.setDuration(400)
					.setInterpolator(new AccelerateInterpolator())
					.withLayer();

			ViewCompat.setPivotX(flecha, 0);
			ViewCompat.setPivotY(flecha, 0);
			ViewCompat.animate(flecha)
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
			TextView antenaDist = (TextView)findViewById(R.id.antena_dist);
			assert antenaDist != null;
			antenaDist.setText(Formatos.formatDistance(this, antena.distanceTo(AntenaActivity.coordsUsuario)));
		}
	}

	@Override
	public void nuevaOrientación(double brújula)
	{
		double rumbo = antena.rumboDesde(coordsUsuario);
		FlechaView f = (FlechaView)findViewById(R.id.flecha);
		assert f != null;
		ángulo = rumbo - brújula;
		f.setÁngulo(ángulo);
	}

	@Override
	public void desorientados()
	{
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
