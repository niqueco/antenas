package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.AttributeSet;
import android.view.View;

public class FlechaView extends View
{
	private double ángulo, ánguloDibujado = Float.MAX_VALUE;
	final private Paint pinturaFlecha, pinturaBorde;
	private float cx, cy, z;
	private float[] líneasFlecha;

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
		double antes = this.ángulo;
		this.ángulo = ángulo;
		if(antes != ángulo)
		{
			if(ánguloDibujado == Float.MAX_VALUE)
				ánguloDibujado = ángulo;
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		pinturaFlecha.setStrokeWidth(6f * (float)w / 100f);
		pinturaBorde.setStrokeWidth(2f * (float)w / 100f);
		cx = getWidth() / 2.0f;
		cy = getHeight() / 2.0f;
		float maxpadding = Math.max(Math.max(getPaddingLeft(), getPaddingRight()), Math.max(getPaddingTop(), getPaddingBottom()));
		z = .8f * Math.min(cx, cy) - maxpadding;
		líneasFlecha = new float[] {
				0, w / 5f, 0, -z,
				0, -z, w / 10f, w / 10f - z,
				0, -z, -w / 10f, w / 10f - z
		};
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
		if(ánguloDibujado != Float.MAX_VALUE)
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
		canvas.drawCircle(0, 0, z + pinturaFlecha.getStrokeWidth() * .75f, pinturaBorde);
		canvas.drawLines(líneasFlecha, pinturaFlecha);
		canvas.restore();

		if(ánguloDibujado != ángulo)
			ViewCompat.postInvalidateOnAnimation(this);
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
