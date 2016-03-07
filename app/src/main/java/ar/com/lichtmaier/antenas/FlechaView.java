package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.os.Parcel;
import android.os.Parcelable;
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

	public static final double D = 10;

	public FlechaView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		TypedArray values = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ArrowView, 0, 0);
		try
		{
			pinturaFlecha = new Paint(Paint.ANTI_ALIAS_FLAG);
			pinturaFlecha.setColor(values.getColor(R.styleable.ArrowView_colorFlecha, Color.BLACK));
			pinturaFlecha.setStrokeCap(Cap.ROUND);
			pinturaBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
			pinturaBorde.setColor(values.getColor(R.styleable.ArrowView_colorDial, Color.BLACK));
			pinturaBorde.setStyle(Paint.Style.STROKE);
		} finally
		{
			values.recycle();
		}
		float density = getResources().getDisplayMetrics().density;
		int z = (int)(100 * density);
		setMinimumHeight(z);
		setMinimumWidth(z);

		instalarDelegadoAccesibilidad();
		ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
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
		double antes = this.ángulo;
		this.ángulo = ángulo;
		if(antes != ángulo)
			ViewCompat.postInvalidateOnAnimation(this);
		if(!suave)
			ánguloDibujado = Float.MAX_VALUE;
	}

	public void setMostrarPuntosCardinales(boolean mostrarPuntosCardinales)
	{
		boolean mpc = this.mostrarPuntosCardinales;
		this.mostrarPuntosCardinales = mostrarPuntosCardinales;
		if(mpc != mostrarPuntosCardinales)
			invalidate();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		pinturaFlecha.setStrokeWidth(6f * (float)w / 100f);
		pinturaBorde.setStrokeWidth(2f * (float)w / 100f);
		cx = w / 2.0f;
		cy = h / 2.0f;
		float maxpadding = Math.max(Math.max(getPaddingLeft(), getPaddingRight()), Math.max(getPaddingTop(), getPaddingBottom()));
		z = .8f * Math.min(cx, cy) - maxpadding;
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
		}

		canvas.save();
		canvas.translate(cx, cy);
		if(ánguloDibujado != Float.MAX_VALUE)
			canvas.rotate((float)ánguloDibujado);
		float radio = z + pinturaFlecha.getStrokeWidth() * .75f;
		canvas.drawCircle(0, 0, radio, pinturaBorde);
		canvas.drawLines(líneasFlecha, pinturaFlecha);
		canvas.restore();
		if(mostrarPuntosCardinales)
			canvas.drawLine(cx, pinturaBorde.getStrokeWidth() / 2 + cy - radio, cx, getHeight() * .06f + cy - radio, pinturaBorde);

		if(ánguloDibujado != ángulo)
			ViewCompat.postInvalidateOnAnimation(this);
	}

	public static class SavedState extends BaseSavedState
	{
		final private double ángulo, ánguloDibujado;
		final private boolean mostrarPuntosCardinales;

		public SavedState(Parcelable superState, double ángulo, double ánguloDibujado, boolean mostrarPuntosCardinales)
		{
			super(superState);
			this.ángulo = ángulo;
			this.ánguloDibujado = ánguloDibujado;
			this.mostrarPuntosCardinales = mostrarPuntosCardinales;
		}

		@Override
		public void writeToParcel(Parcel out, int flags)
		{
			super.writeToParcel(out, flags);
			out.writeDouble(ángulo);
			out.writeDouble(ánguloDibujado);
			out.writeInt(mostrarPuntosCardinales ? 1 : 0);
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
		}
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		return new SavedState(super.onSaveInstanceState(), ángulo, ánguloDibujado, mostrarPuntosCardinales);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		SavedState s = (SavedState)state;
		super.onRestoreInstanceState(s.getSuperState());
		ángulo = s.ángulo;
		ánguloDibujado = s.ánguloDibujado;
		mostrarPuntosCardinales = s.mostrarPuntosCardinales;
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
