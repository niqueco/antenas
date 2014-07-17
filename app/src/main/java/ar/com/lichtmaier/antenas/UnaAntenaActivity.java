package ar.com.lichtmaier.antenas;

import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.*;

public class UnaAntenaActivity extends AntenaActivity
{
	private Antena antena;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		asignarLayout();
		Bundle bundle = getIntent().getExtras();
		antena = Antena.dameAntena(this, bundle.getInt("ar.com.lichtmaier.antenas.antena"));

		final int top = bundle.getInt(PACKAGE + ".top");
		final int left = bundle.getInt(PACKAGE + ".left");
		final int ancho = bundle.getInt(PACKAGE + ".width");
		final int alto = bundle.getInt(PACKAGE + ".height");
		//final int mOriginalOrientation = bundle.getInt(PACKAGE + ".orientation");
		final double ángulo = bundle.getDouble(PACKAGE + ".ángulo");

		if(savedInstanceState == null)
		{
			final FlechaView flecha = (FlechaView)findViewById(R.id.flecha);
			flecha.setÁngulo(ángulo);
			flecha.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
			{
				@Override
				public boolean onPreDraw()
				{
					flecha.getViewTreeObserver().removeOnPreDrawListener(this);

					int[] screenLocation = new int[2];
					flecha.getLocationOnScreen(screenLocation);
					int mLeftDelta = left - screenLocation[0];
					int mTopDelta = top - screenLocation[1];

					// Scale factors to make the large version the same size as the thumbnail
					float mWidthScale = (float) ancho / flecha.getWidth();
					float mHeightScale = (float) alto / flecha.getHeight();

					AnimationSet anim = new AnimationSet(true);
					anim.addAnimation(new ScaleAnimation(mWidthScale, 1, mHeightScale, 1, 0, 0));
					anim.addAnimation(new TranslateAnimation(mLeftDelta, 0, mTopDelta, 0));
					anim.setInterpolator(new AccelerateDecelerateInterpolator());
					anim.setDuration(500);
					flecha.startAnimation(anim);

					AlphaAnimation aa = new AlphaAnimation(0, 1);
					aa.setDuration(600);
					aa.setInterpolator(new AccelerateInterpolator());
					findViewById(R.id.principal).startAnimation(aa);

					if(AntenaActivity.flechaADesaparecer != null)
					{
						anim.setAnimationListener(new Animation.AnimationListener()
						{
							@Override
							public void onAnimationStart(Animation animation)
							{
								flechaADesaparecer.postDelayed(new Runnable()
								{
									@Override
									public void run()
									{
										flechaADesaparecer.setVisibility(View.INVISIBLE);
									}
								}, 200);
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

					/*
					f.setPivotX(0);
					f.setPivotY(0);
					f.setScaleX(mWidthScale);
					f.setScaleY(mHeightScale);
					f.setTranslationX(mLeftDelta);
					f.setTranslationY(mTopDelta);
					f.animate().setDuration(3000).scaleX(1).scaleY(1).translationX(0).translationY(0);
					*/
					return true;
				}
			});
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		((Aplicacion)getApplication()).reportActivityStart(this);
	}

	@Override
	protected void onStop()
	{
		((Aplicacion)getApplication()).reportActivityStop(this);
		super.onStop();
	}

	@Override
	protected void asignarLayout()
	{
		setContentView(R.layout.activity_una_antena);
	}

	@Override
	protected void nuevaUbicación()
	{
	}

	@Override
	void nuevaOrientación(double brújula)
	{
		double rumbo = antena.rumboDesde(coordsUsuario);
		FlechaView f = (FlechaView)findViewById(R.id.flecha);
		f.setÁngulo(rumbo - brújula);
	}
}
