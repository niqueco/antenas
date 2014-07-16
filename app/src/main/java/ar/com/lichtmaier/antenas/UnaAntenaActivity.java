package ar.com.lichtmaier.antenas;

import android.os.Bundle;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Toast;

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
			final FlechaView f = (FlechaView)findViewById(R.id.flecha);
			f.setÁngulo(ángulo);
			f.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
			{
				@Override
				public boolean onPreDraw()
				{
					f.getViewTreeObserver().removeOnPreDrawListener(this);

					int[] screenLocation = new int[2];
					f.getLocationOnScreen(screenLocation);
					int mLeftDelta = left - screenLocation[0];
					int mTopDelta = top - screenLocation[1];

					// Scale factors to make the large version the same size as the thumbnail
					float mWidthScale = (float) ancho / f.getWidth();
					float mHeightScale = (float) alto / f.getHeight();

					//Toast.makeText(UnaAntenaActivity.this, " antes=" + ancho + " ahora=" + f.getWidth(), Toast.LENGTH_SHORT).show();
					//Toast.makeText(UnaAntenaActivity.this, "escala x=" + mWidthScale + " y=" + mHeightScale, Toast.LENGTH_SHORT).show();
					Toast.makeText(UnaAntenaActivity.this, "left antes=" + left + " ahora=" + screenLocation[0] + " delta=" + mLeftDelta, Toast.LENGTH_SHORT).show();

					AnimationSet anim = new AnimationSet(true);
					anim.addAnimation(new ScaleAnimation(mWidthScale, 1, mHeightScale, 1, 0, 0));
					anim.addAnimation(new TranslateAnimation(mLeftDelta, 0, mTopDelta, 0));
					anim.setInterpolator(new AccelerateDecelerateInterpolator());
					anim.setDuration(500);
					f.startAnimation(anim);

					/*
					f.setScaleX(.1f);
					f.setScaleY(.1f);
					f.setTranslationX(300);
					f.setTranslationY(300);
					f.animate().setDuration(1000).scaleX(1).scaleY(1).translationX(0).translationY(0);
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
