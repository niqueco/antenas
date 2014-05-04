package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.util.AttributeSet;
import android.view.View;

public class FlechaView extends View
{
	private static final int ANCHO = 12;
	private double ángulo;
	final private Paint pinturaFlecha, pinturaBorde;
	private float cx, cy, z;
	private float[] líneasFlecha;

	public FlechaView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		float density = getResources().getDisplayMetrics().density;
		pinturaFlecha = new Paint(Paint.ANTI_ALIAS_FLAG);
		pinturaFlecha.setColor(0xffffffff);
		pinturaFlecha.setStrokeWidth((ANCHO / 2) * density);
		pinturaFlecha.setStrokeCap(Cap.ROUND); 
		pinturaBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
		pinturaBorde.setARGB(100, 255, 255, 255);
		pinturaBorde.setStrokeWidth(2 * density);
		pinturaBorde.setStyle(Paint.Style.STROKE);
		int z = (int)(100 * density);
		setMinimumHeight(z);
		setMinimumWidth(z);
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
			invalidate();
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		float density = getResources().getDisplayMetrics().density;
		cx = getWidth() / 2.0f;
		cy = getHeight() / 2.0f;
		float maxpadding = Math.max(Math.max(getPaddingLeft(), getPaddingRight()), Math.max(getPaddingTop(), getPaddingBottom()));
		z = .8f * Math.min(cx, cy) - maxpadding;
		líneasFlecha = new float[] {
				0, 20 * density, 0, -z,
				0, -z, 10 * density, 10 * density - z,
				0, -z, -10 * density, 10 * density - z
		};
		Compat.disableHardwareAccelerationForLineCaps(this);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		float density = getResources().getDisplayMetrics().density;
		int w, h;
		w = h = (int)(100 * density);
		if(View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY)
			w = View.MeasureSpec.getSize(widthMeasureSpec);
		else if(View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.AT_MOST)
			w = Math.min(View.MeasureSpec.getSize(widthMeasureSpec), w);
		if(View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY)
			h = View.MeasureSpec.getSize(heightMeasureSpec);
		else if(View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.AT_MOST)
			h = Math.min(View.MeasureSpec.getSize(heightMeasureSpec), h);
		setMeasuredDimension(w, h);
	}
	
	@Override
	protected void onDraw(Canvas canvas)
	{
		canvas.save();
		canvas.translate(cx, cy);
		canvas.rotate((float)ángulo);
		canvas.drawCircle(0, 0, z + ANCHO / 2, pinturaBorde);
		canvas.drawLines(líneasFlecha, pinturaFlecha);
		canvas.restore();
	}
}
