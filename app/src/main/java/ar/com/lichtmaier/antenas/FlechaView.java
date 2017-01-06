package ar.com.lichtmaier.antenas;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Vibrator;
import android.support.annotation.ColorInt;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

public class FlechaView extends View
{
	private double ángulo, ánguloDibujado = Float.MAX_VALUE;
	final private Paint pinturaFlecha, pinturaBorde;
	private Paint pinturaPuntosCardinales;
	private float cx, cy, z;
	private float[] líneasFlecha;
	private boolean mostrarPuntosCardinales;
	private float altoTexto;

	private static final double D = 10;

	private boolean flechaAlineada, mostrarAlineación;
	@ColorInt private final int colorFlecha, colorFlechaAlineada;
	@ColorInt private int colorFlechaDibujado;
	public static final float TOLERANCIA_ALINEACIÓN = 2, TOLERANCIA_DESALINEACIÓN = 6;

	private ValueAnimator.AnimatorUpdateListener setColorListener;
	private ValueAnimator animaciónDeColor;
	private static ArgbEvaluator argbEvaluator;

	public FlechaView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		TypedArray values = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ArrowView, 0, 0);
		try
		{
			pinturaFlecha = new Paint(Paint.ANTI_ALIAS_FLAG);
			colorFlechaDibujado = colorFlecha = values.getColor(R.styleable.ArrowView_colorFlecha, Color.BLACK);
			colorFlechaAlineada = values.getColor(R.styleable.ArrowView_colorFlechaAlineada, Color.BLACK);
			pinturaFlecha.setStrokeCap(Cap.ROUND);
			pinturaBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
			pinturaBorde.setColor(values.getColor(R.styleable.ArrowView_colorDial, Color.BLACK));
			pinturaBorde.setStyle(Paint.Style.STROKE);
			setMostrarAlineación(values.getBoolean(R.styleable.ArrowView_mostrarAlineacion, false));
		} finally
		{
			values.recycle();
		}

		instalarDelegadoAccesibilidad();
		ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);

		if(BuildConfig.DEBUG && isInEditMode())
			ánguloDibujado = ángulo = 45;
	}

	public double getÁngulo()
	{
		return ángulo;
	}

	public void setÁngulo(double ángulo)
	{
		setÁngulo(ángulo, true);
	}

	public void setÁngulo(double ángulo, boolean suave)
	{
		setÁngulo(ángulo, suave ? ánguloDibujado : Float.MAX_VALUE);
	}

	void setÁngulo(double ángulo, double ánguloDibujado)
	{
		if(sinValor)
		{
			sinValor = false;
			if(ánguloDibujado != Float.MAX_VALUE)
				ViewCompat.animate(this).alpha(1);
			else
			{
				ViewCompat.animate(this).cancel();
				ViewCompat.setAlpha(this, 1);
			}
		}
		double antes = this.ángulo;
		this.ángulo = ángulo;
		if(antes != ángulo)
			ViewCompat.postInvalidateOnAnimation(this);
		this.ánguloDibujado = ánguloDibujado;
	}

	public void setMostrarPuntosCardinales(boolean mostrarPuntosCardinales)
	{
		boolean mpc = this.mostrarPuntosCardinales;
		this.mostrarPuntosCardinales = mostrarPuntosCardinales;
		if(mpc != mostrarPuntosCardinales)
			invalidate();
	}

	public void setMostrarAlineación(boolean mostrarAlineación)
	{
		this.mostrarAlineación = mostrarAlineación;

		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			return;

		if(mostrarAlineación)
		{
			setColorListener = new ValueAnimator.AnimatorUpdateListener()
			{
				@TargetApi(Build.VERSION_CODES.HONEYCOMB)
				@Override
				public void onAnimationUpdate(ValueAnimator animation)
				{
					colorFlechaDibujado = (Integer)animation.getAnimatedValue();
				}
			};
			if(argbEvaluator == null)
				argbEvaluator = new ArgbEvaluator();
		} else
		{
			if(flechaAlineada)
			{
				flechaAlineada = false;
				animarColorA(colorFlecha);
			}
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		pinturaFlecha.setStrokeWidth(6f * (float)w / 100f);
		pinturaBorde.setStrokeWidth(2f * (float)w / 100f);
		cx = w / 2.0f;
		cy = h / 2.0f;
		float maxpadding = Math.max(Math.max(getPaddingLeft(), getPaddingRight()), Math.max(getPaddingTop(), getPaddingBottom()));
		z = Math.min(cx, cy) - maxpadding - pinturaBorde.getStrokeWidth() - pinturaFlecha.getStrokeWidth() * .75f;
		líneasFlecha = new float[] {
				0, w / 5f, 0, -z,
				0, -z, w / 10f, w / 10f - z,
				0, -z, -w / 10f, w / 10f - z
		};
		if(mostrarPuntosCardinales)
		{
			if(pinturaPuntosCardinales == null)
			{
				pinturaPuntosCardinales = new Paint(Paint.ANTI_ALIAS_FLAG);
				pinturaPuntosCardinales.setColor(pinturaBorde.getColor());
			}
			DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
			float scale = dm.scaledDensity / dm.density;
			altoTexto = h * .14f * scale;
			pinturaPuntosCardinales.setTextSize(altoTexto);
		}
		Compat.disableHardwareAccelerationForLineCaps(this);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		float density = getResources().getDisplayMetrics().density;
		int w, h;
		w = h = (int)(100 * density);
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		if(widthMode == View.MeasureSpec.EXACTLY || widthMode == View.MeasureSpec.AT_MOST)
			w = View.MeasureSpec.getSize(widthMeasureSpec);
		switch(MeasureSpec.getMode(heightMeasureSpec))
		{
			case MeasureSpec.EXACTLY:
			case MeasureSpec.AT_MOST:
				h = MeasureSpec.getSize(heightMeasureSpec);
				break;
			case MeasureSpec.UNSPECIFIED:
				h = w;
				break;
		}
		w = h = Math.min(h, w);
		setMeasuredDimension(w, h);
	}
	
	@Override
	protected void onDraw(Canvas canvas)
	{
		if(mostrarPuntosCardinales && pinturaPuntosCardinales != null)
		{
			int h = getHeight();
			canvas.drawText("N", cx - pinturaPuntosCardinales.measureText("N") / 2, altoTexto + h / 10, pinturaPuntosCardinales);
			String text = (int)ángulo + "°";
			canvas.drawText(text, cx - pinturaPuntosCardinales.measureText(text) / 2, h - 2.5f * h / 10, pinturaPuntosCardinales);
		}

		if(ánguloDibujado == Float.MAX_VALUE)
		{
			this.ánguloDibujado = ángulo;
		} else
		{
			double dif = ángulo - this.ánguloDibujado;
			while(dif > 180)
				dif -= 360;
			while(dif < -180)
				dif += 360;
			this.ánguloDibujado += dif * (1. / D);

			ánguloDibujado = (ánguloDibujado + 180.0 * Math.signum(ánguloDibujado)) % 360.0 - 180.0 * Math.signum(ánguloDibujado);
		}

		canvas.save();
		canvas.translate(cx, cy);
		if(ánguloDibujado != Float.MAX_VALUE)
			canvas.rotate((float)ánguloDibujado);

		if(mostrarAlineación)
			verAlineación();

		pinturaFlecha.setColor(colorFlechaDibujado);
		float radio = z + pinturaFlecha.getStrokeWidth() * .75f;
		canvas.drawCircle(0, 0, radio, pinturaBorde);
		canvas.drawLines(líneasFlecha, pinturaFlecha);
		canvas.restore();
		if(mostrarPuntosCardinales)
			canvas.drawLine(cx, pinturaBorde.getStrokeWidth() / 2 + cy - radio, cx, getHeight() * .06f + cy - radio, pinturaBorde);

		if(ánguloDibujado != ángulo)
			ViewCompat.postInvalidateOnAnimation(this);
	}

	private void verAlineación()
	{
		if(flechaAlineada)
		{
			if(!entre(TOLERANCIA_DESALINEACIÓN))
			{
				flechaAlineada = false;
				animarColorA(colorFlecha);
			}
		} else
		{
			if(entre(TOLERANCIA_ALINEACIÓN))
			{
				flechaAlineada = true;
				animarColorA(colorFlechaAlineada);
				if(!isInEditMode())
				{
					Vibrator vibrador = (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
					if(vibrador != null)
						vibrador.vibrate(150);
				}
			}
		}
	}

	private void animarColorA(@ColorInt int color)
	{
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			if(animaciónDeColor == null)
			{
				animaciónDeColor = new ValueAnimator();
				animaciónDeColor.addUpdateListener(setColorListener);
				animaciónDeColor.setDuration(200);
			} else
			{
				animaciónDeColor.cancel();
			}
			animaciónDeColor.setIntValues(colorFlechaDibujado, color);
			animaciónDeColor.setEvaluator(argbEvaluator);
			animaciónDeColor.start();
		} else
		{
			colorFlechaDibujado = color;
		}

	}

	private boolean entre(double t)
	{
		return Math.abs(ánguloDibujado) <= t / 2 || ánguloDibujado > 360 - t / 2;
	}

	public double getÁnguloDibujado()
	{
		return ánguloDibujado;
	}

	private boolean sinValor = false;
	public void sinValor(boolean suave)
	{
		if(sinValor)
			return;
		sinValor = true;
		if(suave)
			ViewCompat.animate(this).alpha(0);
		else
		{
			ViewCompat.animate(this).cancel();
			ViewCompat.setAlpha(this, 0);
		}
	}

	public static class SavedState extends BaseSavedState
	{
		final private double ángulo, ánguloDibujado;
		final private boolean mostrarPuntosCardinales, mostrarAlineación;

		SavedState(Parcelable superState, double ángulo, double ánguloDibujado, boolean mostrarPuntosCardinales, boolean mostrarAlineación)
		{
			super(superState);
			this.ángulo = ángulo;
			this.ánguloDibujado = ánguloDibujado;
			this.mostrarPuntosCardinales = mostrarPuntosCardinales;
			this.mostrarAlineación = mostrarAlineación;
		}

		@Override
		public void writeToParcel(Parcel out, int flags)
		{
			super.writeToParcel(out, flags);
			out.writeDouble(ángulo);
			out.writeDouble(ánguloDibujado);
			out.writeInt(mostrarPuntosCardinales ? 1 : 0);
			out.writeInt(mostrarAlineación ? 1 : 0);
		}

		public static final Parcelable.Creator<SavedState> CREATOR
				= new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in)
			{
				return new SavedState(in);
			}

			public SavedState[] newArray(int size)
			{
				return new SavedState[size];
			}
		};

		private SavedState(Parcel in)
		{
			super(in);
			ángulo = in.readDouble();
			ánguloDibujado = in.readDouble();
			mostrarPuntosCardinales = in.readInt() == 1;
			mostrarAlineación = in.readInt() == 1;
		}
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		return new SavedState(super.onSaveInstanceState(), ángulo, ánguloDibujado, mostrarPuntosCardinales, mostrarAlineación);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		SavedState s = (SavedState)state;
		super.onRestoreInstanceState(s.getSuperState());
		ángulo = s.ángulo;
		ánguloDibujado = s.ánguloDibujado;
		mostrarPuntosCardinales = s.mostrarPuntosCardinales;
		setMostrarAlineación(s.mostrarAlineación);
	}

	private void instalarDelegadoAccesibilidad()
	{
		ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegateCompat() {

			@Override
			public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info)
			{
				super.onInitializeAccessibilityNodeInfo(host, info);

				int a = (int)Math.round(ángulo + 360) % 360;
				int str;
				if(a == 0)
					str = R.plurals.hacia_adelante;
				else if(a > 0 && a < 180)
					str = R.plurals.grados_derecha;
				else if(a == 180)
					str = R.plurals.hacia_atras;
				else if(a > 180)
				{
					str = R.plurals.grados_izquierda;
					a = 360 - a;
				}
				else
					throw new IllegalArgumentException("grados " + a + "?");

				info.setText(getContext().getResources().getQuantityString(str, a, a));
			}
		});
	}

	@Override
	public CharSequence getAccessibilityClassName()
	{
		return FlechaView.class.getName();
	}
}
